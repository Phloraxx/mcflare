package io.mcflare.gateway;

import io.mcflare.core.McflareProtocol;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Minimal HTTP/WebSocket gateway: one MCflare stream maps to one Minecraft TCP stream. */
public final class McflareGateway implements Closeable {
    private static final int DEFAULT_MAX_CONNECTIONS = 256;
    private static final int DEFAULT_PRE_BACKEND_TIMEOUT_MS = 10_000;

    private final InetSocketAddress listen;
    private final InetSocketAddress minecraft;
    private final Semaphore connectionSlots;
    private final Set<Socket> activeClients = ConcurrentHashMap.newKeySet();
    private final int maxConnections;
    private final int preBackendTimeoutMs;
    private final boolean proxyProtocol;
    private final Consumer<String> infoLog;
    private final Consumer<String> errorLog;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong sessionSequence = new AtomicLong(1L);
    private volatile ServerSocket listener;

    private McflareGateway(InetSocketAddress listen, InetSocketAddress minecraft,
                           int maxConnections, boolean proxyProtocol, int preBackendTimeoutMs,
                           Consumer<String> infoLog, Consumer<String> errorLog) {
        this.listen = listen;
        this.minecraft = minecraft;
        this.connectionSlots = new Semaphore(maxConnections);
        this.maxConnections = maxConnections;
        this.preBackendTimeoutMs = preBackendTimeoutMs;
        this.proxyProtocol = proxyProtocol;
        this.infoLog = infoLog;
        this.errorLog = errorLog;
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress listen = parseAddress(args.length > 0 ? args[0] : "127.0.0.1:25577");
        InetSocketAddress minecraft = parseAddress(args.length > 1 ? args[1] : "127.0.0.1:25565");
        int maxConnections = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_MAX_CONNECTIONS;
        boolean proxyProtocol = args.length > 3 && parseBoolean("proxy protocol", args[3]);
        if (maxConnections < 1) throw new IllegalArgumentException("max connections must be positive");
        McflareGateway gateway = new McflareGateway(listen, minecraft, maxConnections, proxyProtocol,
                DEFAULT_PRE_BACKEND_TIMEOUT_MS, System.out::println, System.err::println);
        gateway.bindListener();
        gateway.runLoop();
    }

    public static McflareGateway startAsync(InetSocketAddress listen, InetSocketAddress minecraft,
                                             int maxConnections, boolean proxyProtocol) throws IOException {
        return startAsync(listen, minecraft, maxConnections, proxyProtocol, System.out::println, System.err::println);
    }

    public static McflareGateway startAsync(InetSocketAddress listen, InetSocketAddress minecraft,
                                             int maxConnections, boolean proxyProtocol,
                                             Consumer<String> infoLog, Consumer<String> errorLog) throws IOException {
        return startAsync(listen, minecraft, maxConnections, proxyProtocol,
                DEFAULT_PRE_BACKEND_TIMEOUT_MS, infoLog, errorLog);
    }

    static McflareGateway startAsync(InetSocketAddress listen, InetSocketAddress minecraft,
                                      int maxConnections, boolean proxyProtocol, int preBackendTimeoutMs,
                                      Consumer<String> infoLog, Consumer<String> errorLog) throws IOException {
        if (listen == null || minecraft == null) throw new IllegalArgumentException("listen and Minecraft endpoints are required");
        if (maxConnections < 1) throw new IllegalArgumentException("max connections must be positive");
        if (preBackendTimeoutMs < 1) throw new IllegalArgumentException("pre-backend timeout must be positive");
        if (infoLog == null || errorLog == null) throw new IllegalArgumentException("log consumers are required");
        McflareGateway gateway = new McflareGateway(listen, minecraft, maxConnections, proxyProtocol,
                preBackendTimeoutMs, infoLog, errorLog);
        gateway.bindListener();
        try {
            Thread thread = new Thread(gateway::runLoop, "mcflare-gateway-accept");
            thread.setDaemon(true);
            thread.start();
            return gateway;
        } catch (RuntimeException | Error error) {
            gateway.close();
            throw error;
        }
    }

    private void bindListener() throws IOException {
        ServerSocket server = new ServerSocket();
        try {
            server.bind(listen);
            listener = server;
        } catch (IOException | RuntimeException error) {
            closeQuietly(server);
            throw error;
        }
        try {
            infoLog.accept("MCFLARE_GATEWAY event=listen listen=" + listen + " minecraft=" + minecraft
                    + " maxConnections=" + maxConnections + " preBackendTimeoutMs=" + preBackendTimeoutMs
                    + " proxyProtocol=" + proxyProtocol);
        } catch (RuntimeException error) {
            listener = null;
            closeQuietly(server);
            throw error;
        }
    }

    private void runLoop() {
        while (!closed.get()) {
            Socket client = null;
            try {
                client = listener.accept();
                if (!connectionSlots.tryAcquire()) {
                    infoLog.accept("MCFLARE_GATEWAY event=capacity-reject active=" + maxConnections
                            + " maxConnections=" + maxConnections);
                    rejectBusy(client);
                    continue;
                }
                final Socket accepted = client;
                final long sessionId = sessionSequence.getAndIncrement();
                activeClients.add(accepted);
                if (closed.get()) {
                    activeClients.remove(accepted);
                    connectionSlots.release();
                    closeQuietly(accepted);
                    break;
                }
                Thread thread = new Thread(() -> {
                    try { handle(accepted, sessionId); }
                    finally {
                        activeClients.remove(accepted);
                        connectionSlots.release();
                    }
                }, "mcflare-gateway-" + sessionId);
                thread.setDaemon(true);
                try {
                    thread.start();
                } catch (RuntimeException | Error error) {
                    activeClients.remove(accepted);
                    connectionSlots.release();
                    closeQuietly(accepted);
                    throw error;
                }
            } catch (IOException error) {
                if (!closed.get()) errorLog.accept("MCFLARE_GATEWAY accept error: " + error);
                closeQuietly(client);
            }
        }
    }

    private void handle(Socket client, long sessionId) {
        long startedNanos = System.nanoTime();
        AtomicReference<String> termination = new AtomicReference<String>("unknown");
        String stage = "upgrade";
        WebSocketServerConnection webSocket = null;
        Socket backend = null;
        try {
            webSocket = WebSocketServerConnection.accept(client, McflareProtocol.PATH, McflareProtocol.SUBPROTOCOL);
            String clientIp = realClientIp(webSocket);
            String cfRay = safeCfRay(webSocket.header("cf-ray"));
            infoLog.accept("MCFLARE_GATEWAY session=" + sessionId + " event=upgrade realIpPresent="
                    + (clientIp != null) + " cfRay=" + cfRay);

            // Do not open Minecraft until the first application bytes arrive.
            // WebSocket Ping/Pong used during discovery must not consume a backend
            // connection or trip Minecraft's pre-handshake read timeout.
            stage = "pre-backend";
            byte[] firstData = new byte[64 * 1024];
            final int firstRead;
            webSocket.setReadDeadline(preBackendTimeoutMs);
            try {
                firstRead = webSocket.read(firstData, 0, firstData.length);
            } catch (SocketTimeoutException timeout) {
                termination.compareAndSet("unknown", "pre-backend-timeout");
                return;
            } finally {
                webSocket.setReadDeadline(0);
            }
            if (firstRead < 0) {
                termination.compareAndSet("unknown", "client-close-before-backend");
                return;
            }

            stage = "backend-connect";
            backend = connectTcp(minecraft);
            OutputStream backendOut = backend.getOutputStream();
            if (proxyProtocol && clientIp != null) {
                byte[] proxyHeader = ProxyProtocolV1.encode(clientIp, nonZeroPort(client.getPort()), minecraft.getPort());
                backendOut.write(proxyHeader);
            }
            backendOut.write(firstData, 0, firstRead);
            backendOut.flush();

            stage = "stream";
            final WebSocketServerConnection activeWebSocket = webSocket;
            final Socket activeBackend = backend;
            Thread downstream = startPipeThread(activeBackend.getInputStream(),
                    new WebSocketOutput(activeWebSocket), "mcflare-minecraft-downstream-" + sessionId, termination,
                    activeBackend, activeWebSocket);
            try {
                pipe(new WebSocketInput(activeWebSocket), backendOut);
                termination.compareAndSet("unknown", "client-eof");
            } finally {
                closeQuietly(activeBackend);
                joinQuietly(downstream);
            }
        } catch (Exception error) {
            boolean firstTermination = termination.compareAndSet("unknown", stage + "-error");
            if (firstTermination && !(error instanceof EOFException)) {
                errorLog.accept("MCFLARE_GATEWAY session=" + sessionId + " event=error stage=" + stage
                        + " type=" + error.getClass().getSimpleName());
            }
        } finally {
            closeQuietly(backend);
            closeQuietly(webSocket);
            closeQuietly(client);
            if (webSocket != null) {
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
                infoLog.accept("MCFLARE_GATEWAY session=" + sessionId + " event=close durationMs="
                        + durationMs + " reason=" + termination.get());
            }
        }
    }

    private static String realClientIp(WebSocketServerConnection connection) {
        String ipv6 = trimToNull(connection.header("cf-connecting-ipv6"));
        return ipv6 != null ? ipv6 : trimToNull(connection.header("cf-connecting-ip"));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safeCfRay(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) return "absent";
        if (trimmed.length() > 128) return "invalid";
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-')) return "invalid";
        }
        return trimmed;
    }

    private static int nonZeroPort(int port) { return port > 0 ? port : 1; }

    private static Socket connectTcp(InetSocketAddress target) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(target, 3000);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            return socket;
        } catch (IOException | RuntimeException error) {
            closeQuietly(socket);
            throw error;
        }
    }

    private static Thread startPipeThread(InputStream input, OutputStream output, String name,
                                          AtomicReference<String> termination, Closeable... closeables) {
        Thread thread = new Thread(() -> {
            try {
                pipe(input, output);
                termination.compareAndSet("unknown", "backend-eof");
            } catch (IOException ignored) {
                termination.compareAndSet("unknown", "backend-io-error");
            } finally {
                for (Closeable closeable : closeables) closeQuietly(closeable);
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void pipe(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            output.write(buffer, 0, read);
            output.flush();
        }
    }

    static boolean parseBoolean(String name, String value) {
        String normalized = value == null ? "" : value.trim();
        if ("true".equalsIgnoreCase(normalized)) return true;
        if ("false".equalsIgnoreCase(normalized)) return false;
        throw new IllegalArgumentException(name + " must be true or false");
    }

    private static InetSocketAddress parseAddress(String value) {
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || colon == value.length() - 1) throw new IllegalArgumentException("host:port required: " + value);
        return new InetSocketAddress(value.substring(0, colon), Integer.parseInt(value.substring(colon + 1)));
    }

    private static void rejectBusy(Socket client) {
        try {
            byte[] body = "MCflare gateway busy".getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 503 Service Unavailable\r\nContent-Length: " + body.length
                    + "\r\nConnection: close\r\n\r\n";
            OutputStream output = client.getOutputStream();
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        } catch (IOException ignored) {} finally { closeQuietly(client); }
    }

    private static void joinQuietly(Thread thread) {
        try { thread.join(1000L); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        closeQuietly(listener);
        for (Socket client : activeClients) closeQuietly(client);
    }

    private static final class WebSocketInput extends InputStream {
        private final WebSocketServerConnection connection;
        private WebSocketInput(WebSocketServerConnection connection) { this.connection = connection; }
        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            int read = connection.read(one, 0, 1);
            return read < 0 ? -1 : one[0] & 0xFF;
        }
        @Override public int read(byte[] data, int offset, int length) throws IOException {
            return connection.read(data, offset, length);
        }
    }

    private static final class WebSocketOutput extends OutputStream {
        private final WebSocketServerConnection connection;
        private WebSocketOutput(WebSocketServerConnection connection) { this.connection = connection; }
        @Override public void write(int value) throws IOException { connection.write(new byte[] {(byte) value}); }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            connection.write(data, offset, length);
        }
    }
}
