package io.mcflare.gateway;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Minimal RFC 6455 server-side byte stream used by the Enhanced gateway. */
final class WebSocketServerConnection implements Closeable {
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_HEADERS = 64 * 1024;
    private static final int MAX_FRAME = 1024 * 1024;
    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final Map<String, String> headers;
    private final Object writeLock = new Object();
    private byte[] current = new byte[0];
    private int currentOffset;
    private boolean fragmented;
    private volatile boolean closed;

    private WebSocketServerConnection(Socket socket, Map<String, String> headers)
            throws IOException {
        this.socket = socket;
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
        this.headers = headers;
    }

    static WebSocketServerConnection accept(Socket socket, String requiredPath)
            throws IOException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
        UpgradeRequest request = readUpgrade(socket.getInputStream());
        if (!requiredPath.equals(request.path)) {
            writeHttpError(socket.getOutputStream(), 404, "Not found");
            throw new IOException("unexpected WebSocket path");
        }
        validateUpgrade(request.headers);
        writeUpgrade(socket.getOutputStream(), request.headers.get("sec-websocket-key"));
        socket.setSoTimeout(0);
        return new WebSocketServerConnection(socket, request.headers);
    }

    String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }
    int read(byte[] target, int offset, int length) throws IOException {
        if (length == 0) return 0;
        while (currentOffset >= current.length) {
            current = readNextDataFrame();
            currentOffset = 0;
            if (current == null) return -1;
            if (current.length == 0) continue;
        }
        int count = Math.min(length, current.length - currentOffset);
        System.arraycopy(current, currentOffset, target, offset, count);
        currentOffset += count;
        return count;
    }

    byte[] readExact(int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = read(result, offset, length - offset);
            if (read < 0) throw new EOFException("WebSocket EOF");
            offset += read;
        }
        return result;
    }

    void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }
    void write(byte[] data, int offset, int length) throws IOException {
        if (closed) throw new EOFException("WebSocket closed");
        synchronized (writeLock) {
            output.write(0x82);
            if (length <= 125) {
                output.write(length);
            } else if (length <= 0xFFFF) {
                output.write(126);
                output.write((length >>> 8) & 0xFF);
                output.write(length & 0xFF);
            } else {
                output.write(127);
                long value = length;
                for (int shift = 56; shift >= 0; shift -= 8) {
                    output.write((int) ((value >>> shift) & 0xFF));
                }
            }
            output.write(data, offset, length);
            output.flush();
        }
    }

    private byte[] readNextDataFrame() throws IOException {
        while (!closed) {
            int first = input.read();
            int second = input.read();
            if (first < 0 || second < 0) throw new EOFException("WebSocket EOF");
            if ((first & 0x70) != 0) throw new IOException("unsupported RSV bits");
            boolean fin = (first & 0x80) != 0;
            int opcode = first & 0x0F;
            boolean masked = (second & 0x80) != 0;
            if (!masked) throw new IOException("client WebSocket frame must be masked");

            long length = second & 0x7F;
            if (length == 126) length = readUnsigned(2);
            else if (length == 127) length = readUnsigned(8);
            if (length < 0 || length > MAX_FRAME || length > Integer.MAX_VALUE) {
                throw new IOException("WebSocket frame too large: " + length);
            }
            if (opcode >= 0x8 && (!fin || length > 125)) {
                throw new IOException("invalid WebSocket control frame");
            }

            byte[] mask = readRawExact(4);
            byte[] payload = readRawExact((int) length);
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];

            if (opcode == 0x8) {
                closed = true;
                return null;
            }
            if (opcode == 0x9) {
                writeControl(0xA, payload);
                continue;
            }
            if (opcode == 0xA) continue;
            if (opcode == 0x2) {
                if (fragmented) throw new IOException("nested fragmented message");
                fragmented = !fin;
                return payload;
            }
            if (opcode == 0x0) {
                if (!fragmented) throw new IOException("unexpected continuation frame");
                if (fin) fragmented = false;
                return payload;
            }
            throw new IOException("binary WebSocket required");
        }
        return null;
    }

    private void writeControl(int opcode, byte[] payload) throws IOException {
        synchronized (writeLock) {
            output.write(0x80 | opcode);
            output.write(payload.length);
            output.write(payload);
            output.flush();
        }
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
    private byte[] readRawExact(int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(result, offset, length - offset);
            if (read < 0) throw new EOFException("WebSocket EOF");
            offset += read;
        }
        return result;
    }

    private static void validateUpgrade(Map<String, String> headers) throws IOException {
        String key = headers.get("sec-websocket-key");
        String upgrade = headers.get("upgrade");
        String connection = headers.get("connection");
        String version = headers.get("sec-websocket-version");
        if (key == null || !"websocket".equalsIgnoreCase(upgrade)
                || !containsToken(connection, "upgrade") || !"13".equals(version)) {
            throw new IOException("invalid WebSocket upgrade");
        }
        try {
            if (Base64.getDecoder().decode(key.trim()).length != 16) {
                throw new IOException("invalid Sec-WebSocket-Key");
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid Sec-WebSocket-Key", e);
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

    private static UpgradeRequest readUpgrade(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (bytes.size() < MAX_HEADERS) {
            int b = input.read();
            if (b < 0) throw new EOFException("HTTP EOF");
            bytes.write(b);
            char expected = "\r\n\r\n".charAt(matched);
            if (b == expected) {
                matched++;
                if (matched == 4) break;
            } else {
                matched = b == '\r' ? 1 : 0;
            }
        }
        if (matched != 4) throw new IOException("HTTP headers too large");

        String raw = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] lines = raw.split("\\r\\n");
        if (lines.length == 0) throw new IOException("missing HTTP request line");
        String[] requestLine = lines[0].split(" ", 3);
        if (requestLine.length != 3 || !"GET".equals(requestLine[0])) {
            throw new IOException("expected HTTP GET");
        }

        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT),
                    lines[i].substring(colon + 1).trim());
        }
        return new UpgradeRequest(requestLine[1], headers);
    }
    private static void writeUpgrade(OutputStream output, String key) throws IOException {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            String accept = Base64.getEncoder().encodeToString(
                    sha1.digest((key.trim() + WS_MAGIC).getBytes(StandardCharsets.US_ASCII)));
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            output.write(response.getBytes(StandardCharsets.US_ASCII));
            output.flush();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 unavailable", e);
        }
    }

    private static void writeHttpError(OutputStream output, int code, String message)
            throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " Error\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        socket.close();
    }

    private static final class UpgradeRequest {
        private final String path;
        private final Map<String, String> headers;

        private UpgradeRequest(String path, Map<String, String> headers) {
            this.path = path;
            this.headers = headers;
        }
    }
}
