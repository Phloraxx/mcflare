package io.mcflare.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bridges one Minecraft TCP connection to one already-open MCflare WebSocket. */
public final class LoopbackCarrier implements Closeable {
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final int HEARTBEAT_READ_TIMEOUT_MS = 90_000;
    private static final int LOCAL_ACCEPT_TIMEOUT_MS = 10_000;

    public interface ErrorHandler { void onError(Throwable error); }

    private final ServerSocket listener;
    private final Rfc6455Client webSocket;
    private final ErrorHandler errorHandler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Socket local;

    private LoopbackCarrier(ServerSocket listener, Rfc6455Client webSocket, ErrorHandler errorHandler) {
        this.listener = listener;
        this.webSocket = webSocket;
        this.errorHandler = errorHandler;
    }

    public static LoopbackCarrier start(Rfc6455Client webSocket, ErrorHandler errorHandler) throws IOException {
        return start(webSocket, errorHandler, LOCAL_ACCEPT_TIMEOUT_MS);
    }

    static LoopbackCarrier start(Rfc6455Client webSocket, ErrorHandler errorHandler, int acceptTimeoutMs)
            throws IOException {
        if (webSocket == null) throw new IllegalArgumentException("webSocket");
        if (acceptTimeoutMs < 1) throw new IllegalArgumentException("acceptTimeoutMs");
        ServerSocket listener = new ServerSocket();
        try {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            listener.setSoTimeout(acceptTimeoutMs);
        } catch (IOException | RuntimeException error) {
            closeQuietly(listener);
            throw error;
        }
        LoopbackCarrier carrier = new LoopbackCarrier(listener, webSocket, errorHandler);
        try {
            carrier.startDaemon("mcflare-accept", carrier::acceptOnce);
            return carrier;
        } catch (RuntimeException | Error error) {
            carrier.close();
            throw error;
        }
    }

    public InetSocketAddress getLocalAddress() {
        return new InetSocketAddress("127.0.0.1", listener.getLocalPort());
    }

    private void acceptOnce() {
        try {
            local = listener.accept();
            closeQuietly(listener);
            local.setTcpNoDelay(true);
            local.setKeepAlive(true);
            bridge(local);
        } catch (IOException e) {
            if (!closed.get()) report(e);
        } finally {
            close();
        }
    }

    private void bridge(final Socket socket) throws IOException {
        webSocket.setReadTimeout(HEARTBEAT_READ_TIMEOUT_MS);
        final AtomicBoolean sessionClosing = new AtomicBoolean(false);
        final OutputStream localOut = socket.getOutputStream();

        Thread heartbeat = startDaemon("mcflare-heartbeat", () -> {
            while (!closed.get() && !socket.isClosed() && !webSocket.isClosed()) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                    if (!closed.get() && !socket.isClosed() && !webSocket.isClosed()) webSocket.sendPing(new byte[0]);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException e) {
                    if (!closed.get() && sessionClosing.compareAndSet(false, true)) report(e);
                    closeQuietly(socket);
                    closeQuietly(webSocket);
                    return;
                }
            }
        });

        Thread downlink = startDaemon("mcflare-downlink", () -> {
            try {
                byte[] payload;
                while (!closed.get() && (payload = webSocket.readData()) != null) {
                    localOut.write(payload);
                    localOut.flush();
                    payload = null;
                }
            } catch (IOException e) {
                if (!closed.get() && sessionClosing.compareAndSet(false, true)) report(e);
            } finally {
                closeQuietly(socket);
                closeQuietly(webSocket);
            }
        });

        try {
            InputStream localIn = socket.getInputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while (!closed.get() && (read = localIn.read(buffer)) >= 0) {
                if (read > 0) webSocket.sendBinary(buffer, 0, read);
            }
        } finally {
            sessionClosing.set(true);
            closeQuietly(webSocket);
            closeQuietly(socket);
            heartbeat.interrupt();
            try { downlink.join(1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
        closeQuietly(local);
        closeQuietly(webSocket);
    }
}
