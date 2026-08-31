package io.mcflare.core;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;

/** Dependency-free RFC 6455 client, intentionally compatible with Java 8. */
public final class Rfc6455Client implements Closeable {
    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_HEADERS = 64 * 1024;
    private static final long MAX_FRAME = 32L * 1024L * 1024L;

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final SecureRandom random = new SecureRandom();
    private final Object writeLock = new Object();
    private final byte[] writeScratch = new byte[16 * 1024];
    private final byte[] maskKey = new byte[4];
    private volatile boolean closed;
    private boolean fragmented;

    private Rfc6455Client(Socket socket) throws IOException {
        this.socket = socket;
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
    }

    public static Rfc6455Client connect(String host, int port, String path,
                                         int connectTimeoutMs, int readTimeoutMs) throws IOException {
        return connect(host, port, path, connectTimeoutMs, readTimeoutMs, null);
    }

    public static Rfc6455Client connect(String host, int port, String path,
                                         int connectTimeoutMs, int readTimeoutMs,
                                         String requiredSubprotocol) throws IOException {
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("host");
        if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("path");

        Socket raw = new Socket();
        raw.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        raw.setTcpNoDelay(true);
        raw.setKeepAlive(true);
        raw.setSoTimeout(connectTimeoutMs);

        SSLSocket ssl = null;
        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            ssl = (SSLSocket) factory.createSocket(raw, host, port, true);
            configureTls(ssl, host);
            ssl.startHandshake();
            Rfc6455Client client = new Rfc6455Client(ssl);
            client.upgrade(host, port, path, requiredSubprotocol);
            ssl.setSoTimeout(readTimeoutMs);
            return client;
        } catch (IOException | RuntimeException e) {
            try { if (ssl != null) ssl.close(); else raw.close(); } catch (IOException ignored) {}
            throw e;
        }
    }

    private static void configureTls(SSLSocket ssl, String host) {
        SSLParameters parameters = ssl.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        try {
            parameters.setServerNames(Collections.singletonList(new SNIHostName(host)));
        } catch (IllegalArgumentException ignored) {
            // Literal IPs do not require SNI.
        }
        ssl.setSSLParameters(parameters);
    }

    private void upgrade(String host, int port, String path, String requiredSubprotocol) throws IOException {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        String hostHeader = port == 443 ? host : host + ":" + port;
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + hostHeader + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + (requiredSubprotocol == null ? "" : "Sec-WebSocket-Protocol: " + requiredSubprotocol + "\r\n")
                + "User-Agent: MCflare/0.1\r\n\r\n";
        output.write(request.getBytes(StandardCharsets.US_ASCII));
        output.flush();

        String headers = readHeaders(input);
        String[] lines = headers.split("\\r\\n");
        if (lines.length == 0 || !lines[0].contains(" 101 ")) {
            throw new IOException("WebSocket upgrade failed: " + (lines.length == 0 ? "empty response" : lines[0]));
        }

        String expectedAccept = websocketAccept(key);
        String actualAccept = null;
        String upgrade = null;
        String connection = null;
        String subprotocol = null;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = lines[i].substring(colon + 1).trim();
            if ("sec-websocket-accept".equals(name)) actualAccept = value;
            else if ("upgrade".equals(name)) upgrade = value;
            else if ("connection".equals(name)) connection = value;
            else if ("sec-websocket-protocol".equals(name)) subprotocol = value;
        }
        if (!expectedAccept.equals(actualAccept)
                || !"websocket".equalsIgnoreCase(upgrade)
                || !containsToken(connection, "upgrade")
                || (requiredSubprotocol != null && !requiredSubprotocol.equals(subprotocol))) {
            throw new IOException("Invalid WebSocket upgrade response");
        }
    }

    private static boolean containsToken(String value, String expected) {
        if (value == null) return false;
        String[] tokens = value.split(",");
        for (String token : tokens) {
            if (expected.equalsIgnoreCase(token.trim())) return true;
        }
        return false;
    }

    private static String readHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int state = 0;
        while (bytes.size() < MAX_HEADERS) {
            int b = in.read();
            if (b < 0) throw new EOFException("EOF during WebSocket upgrade");
            bytes.write(b);
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = b == '\r' ? 1 : 0;
            if (state == 4) return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
        }
        throw new IOException("WebSocket headers too large");
    }

    private static String websocketAccept(String key) throws IOException {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + MAGIC).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 unavailable", e);
        }
    }

    public void sendBinary(byte[] data) throws IOException {
        sendBinary(data, 0, data.length);
    }

    public void sendBinary(byte[] data, int offset, int length) throws IOException {
        sendFrame(0x2, data, offset, length);
    }

    public void sendPing(byte[] payload) throws IOException {
        if (payload == null) payload = new byte[0];
        if (payload.length > 125) throw new IllegalArgumentException("WebSocket ping payload too large");
        sendFrame(0x9, payload, 0, payload.length);
    }

    private void sendPong(byte[] payload) throws IOException {
        if (payload.length > 125) throw new IOException("WebSocket pong payload too large");
        sendFrame(0xA, payload, 0, payload.length);
    }

    public void setReadTimeout(int timeoutMs) throws java.net.SocketException {
        if (timeoutMs < 0) throw new IllegalArgumentException("timeoutMs");
        socket.setSoTimeout(timeoutMs);
    }

    public boolean isClosed() {
        return closed || socket.isClosed();
    }

    private void sendFrame(int opcode, byte[] data, int offset, int length) throws IOException {
        if (closed) throw new EOFException("WebSocket closed");
        synchronized (writeLock) {
            output.write(0x80 | (opcode & 0x0F));
            if (length <= 125) {
                output.write(0x80 | length);
            } else if (length <= 0xFFFF) {
                output.write(0x80 | 126);
                output.write((length >>> 8) & 0xFF);
                output.write(length & 0xFF);
            } else {
                output.write(0x80 | 127);
                long value = length;
                for (int shift = 56; shift >= 0; shift -= 8) {
                    output.write((int) ((value >>> shift) & 0xFF));
                }
            }

            random.nextBytes(maskKey);
            output.write(maskKey);
            int written = 0;
            while (written < length) {
                int chunk = Math.min(writeScratch.length, length - written);
                for (int i = 0; i < chunk; i++) {
                    writeScratch[i] = (byte) (data[offset + written + i]
                            ^ maskKey[(written + i) & 3]);
                }
                output.write(writeScratch, 0, chunk);
                written += chunk;
            }
            output.flush();
        }
    }

    /** Returns the next binary/continuation payload, or null after a close frame. */
    public byte[] readData() throws IOException {
        while (!closed) {
            int first = input.read();
            int second = input.read();
            if (first < 0 || second < 0) throw new EOFException("WebSocket EOF");
            if ((first & 0x70) != 0) throw new IOException("Unsupported WebSocket RSV bits");

            int opcode = first & 0x0F;
            boolean fin = (first & 0x80) != 0;
            boolean masked = (second & 0x80) != 0;
            if (masked) throw new IOException("Server WebSocket frame must not be masked");
            long length = second & 0x7F;
            if (length == 126) length = readUnsigned(2);
            else if (length == 127) {
                length = readUnsigned(8);
                if (length < 0) throw new IOException("Invalid WebSocket 64-bit frame length");
            }
            boolean control = opcode >= 0x8;
            if (control && (!fin || length > 125)) {
                throw new IOException("Invalid fragmented/oversized WebSocket control frame");
            }
            if (length > MAX_FRAME || length > Integer.MAX_VALUE) {
                throw new IOException("WebSocket frame too large: " + length);
            }

            byte[] payload = readExact((int) length);

            if (opcode == 0x8) {
                closed = true;
                return null;
            }
            if (opcode == 0x9) {
                sendPong(payload);
                continue;
            }
            if (opcode == 0xA) continue;
            if (opcode == 0x2) {
                if (fragmented) throw new IOException("Nested fragmented WebSocket message");
                fragmented = !fin;
                return payload;
            }
            if (opcode == 0x0) {
                if (!fragmented) throw new IOException("Unexpected WebSocket continuation frame");
                if (fin) fragmented = false;
                return payload;
            }
            if (opcode == 0x1) throw new IOException("Unexpected text WebSocket frame");
            throw new IOException("Unsupported WebSocket opcode: " + opcode);
        }
        return null;
    }

    private long readUnsigned(int bytes) throws IOException {
        long value = 0L;
        for (int i = 0; i < bytes; i++) {
            int b = input.read();
            if (b < 0) throw new EOFException("WebSocket EOF");
            value = (value << 8) | (b & 0xFFL);
        }
        return value;
    }

    private byte[] readExact(int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read < 0) throw new EOFException("WebSocket EOF");
            offset += read;
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            socket.close();
            return;
        }
        closed = true;
        try {
            synchronized (writeLock) {
                if (!socket.isClosed()) {
                    // Best effort close frame. An empty client close frame is valid.
                    output.write(0x88);
                    output.write(0x80);
                    random.nextBytes(maskKey);
                    output.write(maskKey);
                    output.flush();
                }
            }
        } catch (IOException ignored) {
            // Socket close below is authoritative.
        } finally {
            socket.close();
        }
    }
}
