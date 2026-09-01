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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Minimal HTTP/WebSocket gateway: one MCflare stream maps to one Minecraft TCP stream. */
public final class McflareGateway implements Closeable {
    private static final int DEFAULT_MAX_CONNECTIONS = 256;

    private final InetSocketAddress listen;
    private final InetSocketAddress minecraft;
    private final Semaphore connectionSlots;
    private final boolean proxyProtocol;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile ServerSocket listener;

    private McflareGateway(InetSocketAddress listen, InetSocketAddress minecraft,
                           int maxConnections, boolean proxyProtocol) {
        this.listen = listen;
        this.minecraft = minecraft;
        this.connectionSlots = new Semaphore(maxConnections);
        this.proxyProtocol = proxyProtocol;
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress listen = parseAddress(args.length > 0 ? args[0] : "127.0.0.1:25577");
        InetSocketAddress minecraft = parseAddress(args.length > 1 ? args[1] : "127.0.0.1:25565");
        int maxConnections = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_MAX_CONNECTIONS;
        boolean proxyProtocol = args.length > 3 && Boolean.parseBoolean(args[3]);
        if (maxConnections < 1) throw new IllegalArgumentException("max connections must be positive");
        McflareGateway gateway = new McflareGateway(listen, minecraft, maxConnections, proxyProtocol);
        gateway.bindListener();
        gateway.runLoop();
    }

    public static McflareGateway startAsync(InetSocketAddress listen, InetSocketAddress minecraft,
                                             int maxConnections, boolean proxyProtocol) throws IOException {
        if (maxConnections < 1) throw new IllegalArgumentException("max connections must be positive");
        McflareGateway gateway = new McflareGateway(listen, minecraft, maxConnections, proxyProtocol);
        gateway.bindListener();
        Thread thread = new Thread(gateway::runLoop, "mcflare-gateway-accept");
        thread.setDaemon(true);
        thread.start();
        return gateway;
    }

    private void bindListener() throws IOException {
        ServerSocket server = new ServerSocket();
        server.bind(listen);
        listener = server;
        System.out.println("MCFLARE_GATEWAY listen=" + listen + " minecraft=" + minecraft
                + " maxConnections=" + connectionSlots.availablePermits() + " proxyProtocol=" + proxyProtocol);
    }

    private void runLoop() {
        while (!closed.get()) {
            Socket client = null;
            try {
                client = listener.accept();
                if (!connectionSlots.tryAcquire()) {
                    rejectBusy(client);
                    continue;
                }
                final Socket accepted = client;
                Thread thread = new Thread(() -> {
                    try { handle(accepted); }
                    finally { connectionSlots.release(); }
                }, "mcflare-gateway-" + accepted.getPort());
                thread.setDaemon(true);
                thread.start();
            } catch (IOException error) {
                if (!closed.get()) System.err.println("MCFLARE_GATEWAY accept error: " + error);
                closeQuietly(client);
            }
        }
    }

    private void handle(Socket client) {
        WebSocketServerConnection webSocket = null;
        Socket backend = null;
        try {
            webSocket = WebSocketServerConnection.accept(client, McflareProtocol.PATH, McflareProtocol.SUBPROTOCOL);
            String clientIp = realClientIp(webSocket);
            String cfRay = webSocket.header("cf-ray");
            System.out.println("MCFLARE_GATEWAY upgrade realIpPresent=" + (clientIp != null)
                    + " cfRayPresent=" + (cfRay != null));

            backend = connectTcp(minecraft);
            OutputStream backendOut = backend.getOutputStream();
            if (proxyProtocol && clientIp != null) {
                byte[] proxyHeader = ProxyProtocolV1.encode(clientIp, nonZeroPort(client.getPort()), minecraft.getPort());
                backendOut.write(proxyHeader);
                backendOut.flush();
            }

            final WebSocketServerConnection activeWebSocket = webSocket;
            final Socket activeBackend = backend;
            Thread downstream = startPipeThread(activeBackend, activeBackend.getInputStream(),
                    new WebSocketOutput(activeWebSocket), "mcflare-minecraft-downstream");
            try { pipe(new WebSocketInput(activeWebSocket), backendOut); }
            finally {
                closeQuietly(activeBackend);
                joinQuietly(downstream);
            }
        } catch (Exception error) {
            if (!(error instanceof EOFException)) System.err.println("MCFLARE_GATEWAY connection error: " + error);
        } finally {
            closeQuietly(backend);
            closeQuietly(webSocket);
            closeQuietly(client);
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

    private static int nonZeroPort(int port) { return port > 0 ? port : 1; }

    private static Socket connectTcp(InetSocketAddress target) throws IOException {
        Socket socket = new Socket();
        socket.connect(target, 3000);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        return socket;
    }

    private static Thread startPipeThread(Closeable backend, InputStream input,
                                          OutputStream output, String name) {
        Thread thread = new Thread(() -> {
            try { pipe(input, output); }
            catch (IOException ignored) {}
            finally { closeQuietly(backend); }
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
