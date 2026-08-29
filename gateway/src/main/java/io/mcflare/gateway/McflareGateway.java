package io.mcflare.gateway;

import io.mcflare.core.GatewayProtocol;
import io.mcflare.core.MinecraftStatusProbe;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Enhanced-mode WebSocket gateway for Minecraft and optional side services. */
public final class McflareGateway {
    private final InetSocketAddress listen;
    private final InetSocketAddress minecraft;
    private final Map<String, ServiceTarget> services;

    private McflareGateway(InetSocketAddress listen, InetSocketAddress minecraft,
                           Map<String, ServiceTarget> services) {
        this.listen = listen;
        this.minecraft = minecraft;
        this.services = services;
    }

    public static void main(String[] args) throws Exception {
        InetSocketAddress listen = parseAddress(args.length > 0 ? args[0] : "127.0.0.1:25577");
        InetSocketAddress minecraft = parseAddress(args.length > 1 ? args[1] : "127.0.0.1:25565");
        Map<String, ServiceTarget> services = new LinkedHashMap<String, ServiceTarget>();
        for (int i = 2; i < args.length; i++) {
            ServiceTarget target = parseService(args[i]);
            if (services.put(target.id, target) != null) {
                throw new IllegalArgumentException("Duplicate service: " + target.id);
            }
        }
        new McflareGateway(listen, minecraft, services).run();
    }

    private void run() throws IOException {
        ServerSocket server = new ServerSocket();
        server.bind(listen);
        System.out.println("MCFLARE_GATEWAY listen=" + listen + " minecraft=" + minecraft
                + " services=" + services.keySet());
        while (true) {
            final Socket client = server.accept();
            Thread thread = new Thread(new Runnable() {
                @Override public void run() { handle(client); }
            }, "mcflare-gateway-" + client.getPort());
            thread.setDaemon(true);
            thread.start();
        }
    }
    private void handle(Socket client) {
        WebSocketServerConnection webSocket = null;
        try {
            webSocket = WebSocketServerConnection.accept(client, MinecraftStatusProbe.DEFAULT_PATH);
            System.out.println("MCFLARE_GATEWAY upgrade cfIpPresent="
                    + (webSocket.header("cf-connecting-ip") != null)
                    + " cfRayPresent=" + (webSocket.header("cf-ray") != null));

            InputStream input = new WebSocketInput(webSocket);
            OutputStream output = new WebSocketOutput(webSocket);
            byte[] prefix = readExact(input, GatewayProtocol.MAGIC.length);
            if (Arrays.equals(prefix, GatewayProtocol.MAGIC)) {
                handleMcflare(input, output);
            } else {
                proxyMinecraft(input, output, prefix);
            }
        } catch (Exception e) {
            if (!(e instanceof EOFException)) {
                System.err.println("MCFLARE_GATEWAY connection error: " + e);
            }
        } finally {
            closeQuietly(webSocket);
            closeQuietly(client);
        }
    }

    private void handleMcflare(InputStream input, OutputStream output) throws IOException {
        int version = input.read();
        int opcode = input.read();
        if (version < 0 || opcode < 0) throw new EOFException("truncated MCflare preamble");
        if (version != GatewayProtocol.VERSION) {
            writeControl(output, GatewayProtocol.OP_ERROR, "{\"error\":\"unsupported_version\"}");
            return;
        }

        if (opcode == GatewayProtocol.OP_HELLO) {
            writeControl(output, GatewayProtocol.responseOpcode(opcode), capabilitiesJson());
            return;
        }

        String serviceId = readServiceId(input);
        ServiceTarget service = services.get(serviceId);
        if (service == null) {
            writeControl(output, GatewayProtocol.OP_ERROR, "{\"error\":\"unknown_service\"}");
            return;
        }

        if (opcode == GatewayProtocol.OP_OPEN_STREAM && service.kind == ServiceKind.STREAM) {
            writeControl(output, GatewayProtocol.responseOpcode(opcode), "{\"ok\":true}");
            proxyStreamService(input, output, service.address);
            return;
        }
        if (opcode == GatewayProtocol.OP_OPEN_DATAGRAM && service.kind == ServiceKind.DATAGRAM) {
            writeControl(output, GatewayProtocol.responseOpcode(opcode), "{\"ok\":true}");
            proxyDatagramService(input, output, service.address);
            return;
        }
        writeControl(output, GatewayProtocol.OP_ERROR, "{\"error\":\"wrong_service_kind\"}");
    }

    private String capabilitiesJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"protocol\":").append(GatewayProtocol.VERSION)
                .append(",\"mode\":\"enhanced\",\"services\":[");
        boolean first = true;
        for (ServiceTarget service : services.values()) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"id\":\"").append(service.id)
                    .append("\",\"kind\":\"")
                    .append(service.kind == ServiceKind.STREAM ? "stream" : "datagram")
                    .append('\"');
            if (service.kind == ServiceKind.DATAGRAM) {
                json.append(",\"maxDatagram\":").append(GatewayProtocol.MAX_DATAGRAM);
            }
            json.append('}');
        }
        return json.append("]}").toString();
    }

    private static String readServiceId(InputStream input) throws IOException {
        int length = input.read();
        if (length < 1 || length > GatewayProtocol.MAX_SERVICE_ID_BYTES) {
            throw new IOException("invalid service id length");
        }
        return new String(readExact(input, length), StandardCharsets.UTF_8);
    }

    private static void writeControl(OutputStream output, int opcode, String payload)
            throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        if (body.length > GatewayProtocol.MAX_CONTROL_PAYLOAD) {
            throw new IOException("control payload too large");
        }
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        record.write(GatewayProtocol.MAGIC);
        record.write(GatewayProtocol.VERSION);
        record.write(opcode);
        writeU16(record, body.length);
        record.write(body);
        output.write(record.toByteArray());
        output.flush();
    }

    private void proxyMinecraft(InputStream input, OutputStream output, byte[] prefix)
            throws IOException {
        final Socket backend = connectTcp(minecraft);
        OutputStream backendOut = backend.getOutputStream();
        backendOut.write(prefix);
        backendOut.flush();
        Thread downstream = startPipeThread(
                backend, backend.getInputStream(), output, "mcflare-minecraft-downstream");
        try {
            pipe(input, backendOut);
        } finally {
            closeQuietly(backend);
            joinQuietly(downstream);
        }
    }

    private void proxyStreamService(InputStream input, OutputStream output,
                                    InetSocketAddress target) throws IOException {
        final Socket backend = connectTcp(target);
        Thread downstream = startPipeThread(
                backend, backend.getInputStream(), output, "mcflare-service-downstream");
        try {
            pipe(input, backend.getOutputStream());
        } finally {
            closeQuietly(backend);
            joinQuietly(downstream);
        }
    }

    private void proxyDatagramService(final InputStream input, final OutputStream output,
                                      InetSocketAddress target) throws IOException {
        final DatagramSocket udp = new DatagramSocket();
        udp.connect(target);

        Thread downstream = new Thread(new Runnable() {
            @Override public void run() {
                byte[] buffer = new byte[GatewayProtocol.MAX_DATAGRAM];
                try {
                    while (!udp.isClosed()) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udp.receive(packet);
                        ByteArrayOutputStream record = new ByteArrayOutputStream(packet.getLength() + 2);
                        writeU16(record, packet.getLength());
                        record.write(packet.getData(), packet.getOffset(), packet.getLength());
                        output.write(record.toByteArray());
                        output.flush();
                    }
                } catch (IOException ignored) {
                } finally {
                    udp.close();
                }
            }
        }, "mcflare-datagram-downstream");
        downstream.setDaemon(true);
        downstream.start();
        try {
            while (true) {
                int length = readU16(input);
                if (length < 1 || length > GatewayProtocol.MAX_DATAGRAM) {
                    throw new IOException("invalid datagram length: " + length);
                }
                byte[] payload = readExact(input, length);
                udp.send(new DatagramPacket(payload, payload.length));
            }
        } finally {
            udp.close();
            joinQuietly(downstream);
        }
    }

    private static Socket connectTcp(InetSocketAddress target) throws IOException {
        Socket socket = new Socket();
        socket.connect(target, 3000);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        return socket;
    }

    private static Thread startPipeThread(final Closeable backend, final InputStream input,
                                          final OutputStream output, String name) {
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try { pipe(input, output); }
                catch (IOException ignored) {}
                finally { closeQuietly(backend); }
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

    private static byte[] readExact(InputStream input, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read < 0) throw new EOFException("stream EOF");
            offset += read;
        }
        return result;
    }

    private static int readU16(InputStream input) throws IOException {
        int high = input.read();
        int low = input.read();
        if (high < 0 || low < 0) throw new EOFException("stream EOF");
        return (high << 8) | low;
    }
    private static void writeU16(OutputStream output, int value) throws IOException {
        output.write((value >>> 8) & 0xFF);
        output.write(value & 0xFF);
    }

    private static ServiceTarget parseService(String value) {
        int equals = value.indexOf('=');
        if (equals <= 0 || equals == value.length() - 1) {
            throw new IllegalArgumentException("service format: id=tcp://host:port or id=udp://host:port");
        }
        String id = value.substring(0, equals);
        String endpoint = value.substring(equals + 1);
        if (id.getBytes(StandardCharsets.UTF_8).length > GatewayProtocol.MAX_SERVICE_ID_BYTES) {
            throw new IllegalArgumentException("service id too long: " + id);
        }
        if (endpoint.startsWith("tcp://")) {
            return new ServiceTarget(id, ServiceKind.STREAM, parseAddress(endpoint.substring(6)));
        }
        if (endpoint.startsWith("udp://")) {
            return new ServiceTarget(id, ServiceKind.DATAGRAM, parseAddress(endpoint.substring(6)));
        }
        throw new IllegalArgumentException("unsupported service endpoint: " + endpoint);
    }

    private static InetSocketAddress parseAddress(String value) {
        int colon = value.lastIndexOf(':');
        if (colon <= 0 || colon == value.length() - 1) {
            throw new IllegalArgumentException("host:port required: " + value);
        }
        return new InetSocketAddress(
                value.substring(0, colon), Integer.parseInt(value.substring(colon + 1)));
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

    private static final class ServiceTarget {
        private final String id;
        private final ServiceKind kind;
        private final InetSocketAddress address;

        private ServiceTarget(String id, ServiceKind kind, InetSocketAddress address) {
            this.id = id;
            this.kind = kind;
            this.address = address;
        }
    }

    private enum ServiceKind { STREAM, DATAGRAM }

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
