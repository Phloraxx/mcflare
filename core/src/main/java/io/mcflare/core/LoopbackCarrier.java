package io.mcflare.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Presents a local TCP socket and carries each accepted stream over WSS.
 * This keeps Minecraft/other mods seeing ordinary TCP while MCflare owns transport.
 */
public final class LoopbackCarrier implements Closeable {
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    public interface ErrorHandler {
        void onError(Throwable error);
    }

    private final String host;
    private final String path;
    private final ServerSocket listener;
    private final ErrorHandler errorHandler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Set<Socket> sockets = Collections.synchronizedSet(new HashSet<Socket>());

    private LoopbackCarrier(String host, String path, ServerSocket listener, ErrorHandler errorHandler) {
        this.host = host;
        this.path = path;
        this.listener = listener;
        this.errorHandler = errorHandler;
    }

    public static LoopbackCarrier start(String host, String path, ErrorHandler errorHandler) throws IOException {
        ServerSocket listener = new ServerSocket();
        listener.bind(new InetSocketAddress("127.0.0.1", 0));
        LoopbackCarrier carrier = new LoopbackCarrier(host, path, listener, errorHandler);
        carrier.startDaemon("mcflare-accept", carrier::acceptLoop);
        return carrier;
    }

    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress("127.0.0.1", listener.getLocalPort());
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                final Socket socket = listener.accept();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                sockets.add(socket);
                startDaemon("mcflare-stream", new Runnable() {
                    @Override public void run() { bridge(socket); }
                });
            } catch (IOException e) {
                if (!closed.get()) report(e);
                return;
            }
        }
    }

    private void bridge(final Socket local) {
        Rfc6455Client ws = null;
        final AtomicBoolean sessionClosing = new AtomicBoolean(false);
        try {
            ws = Rfc6455Client.connect(host, 443, path, 4000, 0);
            final Rfc6455Client activeWs = ws;
            final OutputStream localOut = local.getOutputStream();

            Thread heartbeat = startDaemon("mcflare-heartbeat", new Runnable() {
                @Override public void run() {
                    while (!closed.get() && !local.isClosed() && !activeWs.isClosed()) {
                        try {
                            Thread.sleep(HEARTBEAT_INTERVAL_MS);
                            if (!closed.get() && !local.isClosed() && !activeWs.isClosed()) {
                                activeWs.sendPing(new byte[0]);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        } catch (IOException e) {
                            if (!closed.get() && sessionClosing.compareAndSet(false, true)) report(e);
                            closeQuietly(local);
                            closeQuietly(activeWs);
                            return;
                        }
                    }
                }
            });

            Thread downlink = startDaemon("mcflare-downlink", new Runnable() {
                @Override public void run() {
                    try {
                        byte[] payload;
                        while (!closed.get() && (payload = activeWs.readData()) != null) {
                            localOut.write(payload);
                            localOut.flush();
                        }
                    } catch (IOException e) {
                        if (!closed.get() && sessionClosing.compareAndSet(false, true)) report(e);
                    } finally {
                        closeQuietly(local);
                        closeQuietly(activeWs);
                    }
                }
            });

            InputStream localIn = local.getInputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while (!closed.get() && (read = localIn.read(buffer)) >= 0) {
                if (read > 0) activeWs.sendBinary(buffer, 0, read);
            }
            sessionClosing.set(true);
            closeQuietly(activeWs);
            closeQuietly(local);
            heartbeat.interrupt();
            try { downlink.join(1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        } catch (IOException e) {
            if (!closed.get() && sessionClosing.compareAndSet(false, true)) report(e);
        } finally {
            closeQuietly(ws);
            closeQuietly(local);
            sockets.remove(local);
        }
    }

    private Thread startDaemon(String name, Runnable task) {
        Thread thread = new Thread(task, name + "-" + Integer.toHexString(System.identityHashCode(task)));
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void report(Throwable error) {
        if (errorHandler != null) errorHandler.onError(error);
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeQuietly(listener);
        synchronized (sockets) {
            for (Socket socket : sockets) closeQuietly(socket);
            sockets.clear();
        }
    }
}
