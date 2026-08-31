package io.mcflare.gateway;

import io.mcflare.core.MinecraftStatusProbe;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

/** Minimal HTTP/WebSocket gateway: one MCflare stream maps to one Minecraft TCP stream. */
public final class McflareGateway {
    private static final int DEFAULT_MAX_CONNECTIONS = 256;

    private final InetSocketAddress listen;
    private final InetSocketAddress minecraft;
    private final Semaphore connectionSlots;

    private McflareGateway(InetSocketAddress listen, InetSocketAddress minecraft, int maxConnections) {
        this.listen = listen;
        this.minecraft = minecraft;
        this.connectionSlots = new Semaphore(maxConnections);
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress listen = parseAddress(args.length > 0 ? args[0] : "127.0.0.1:25577");
        InetSocketAddress minecraft = parseAddress(args.length > 1 ? args[1] : "127.0.0.1:25565");
        int maxConnections = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_MAX_CONNECTIONS;
        if (maxConnections < 1) throw new IllegalArgumentException("max connections must be positive");
        new McflareGateway(listen, minecraft, maxConnections).run();
    }

    private void run() throws IOException {
        ServerSocket server = new ServerSocket();
        server.bind(listen);
        System.out.println("MCFLARE_GATEWAY listen=" + listen + " minecraft=" + minecraft
                + " maxConnections=" + connectionSlots.availablePermits());
        while (true) {
            Socket client = server.accept();
            if (!connectionSlots.tryAcquire()) {
                rejectBusy(client);
                continue;
            }
            Thread thread = new Thread(() -> {
                try { handle(client); }
                finally { connectionSlots.release(); }
            }, "mcflare-gateway-" + client.getPort());
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void handle(Socket client) {
        WebSocketServerConnection webSocket = null;
        Socket backend = null;
        try {
            webSocket = WebSocketServerConnection.accept(client, MinecraftStatusProbe.DEFAULT_PATH);
            System.out.println("MCFLARE_GATEWAY upgrade cfIpPresent="
                    + (webSocket.header("cf-connecting-ip") != null)
                    + " cfRayPresent=" + (webSocket.header("cf-ray") != null));

            backend = connectTcp(minecraft);
            final WebSocketServerConnection activeWebSocket = webSocket;
            final Socket activeBackend = backend;
            Thread downstream = startPipeThread(
                    activeBackend,
                    activeBackend.getInputStream(),
                    new WebSocketOutput(activeWebSocket),
                    "mcflare-minecraft-downstream");
            try {
                pipe(new WebSocketInput(activeWebSocket), activeBackend.getOutputStream());
            } finally {
                closeQuietly(activeBackend);
                joinQuietly(downstream);
            }
        } catch (Exception e) {
            if (!(e instanceof java.io.EOFException)) {
                System.err.println("MCFLARE_GATEWAY connection error: " + e);
            }
        } finally {
            closeQuietly(backend);
            closeQuietly(webSocket);
            closeQuietly(client);
        }
    }

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
        if (colon <= 0 || colon == value.length() - 1) {
            throw new IllegalArgumentException("host:port required: " + value);
        }
        return new InetSocketAddress(value.substring(0, colon), Integer.parseInt(value.substring(colon + 1)));
    }

    private static void rejectBusy(Socket client) {
        try {
            byte[] body = "MCflare gateway busy".getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 503 Service Unavailable\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream output = client.getOutputStream();
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        } catch (IOException ignored) {
        } finally {
            closeQuietly(client);
        }
    }

    private static void joinQuietly(Thread thread) {
        try { thread.join(1000L); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); }
        catch (IOException ignored) {}
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
        @Override public void write(int value) throws IOException {
            connection.write(new byte[] {(byte) value});
        }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            connection.write(data, offset, length);
        }
    }
}
