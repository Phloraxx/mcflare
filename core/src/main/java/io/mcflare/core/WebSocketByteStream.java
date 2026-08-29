package io.mcflare.core;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;

/** Presents RFC6455 binary frames as one ordered byte stream. */
public final class WebSocketByteStream implements Closeable {
    private final Rfc6455Client webSocket;
    private byte[] pending = new byte[0];
    private int pendingOffset;

    public WebSocketByteStream(Rfc6455Client webSocket) {
        if (webSocket == null) throw new IllegalArgumentException("webSocket");
        this.webSocket = webSocket;
    }

    public synchronized void write(byte[] data) throws IOException {
        webSocket.sendBinary(data);
    }

    public synchronized void write(byte[] data, int offset, int length) throws IOException {
        webSocket.sendBinary(data, offset, length);
    }

    public byte[] readExact(int length) throws IOException {
        if (length < 0) throw new IllegalArgumentException("length");
        byte[] result = new byte[length];
        int written = 0;
        while (written < length) {
            if (pendingOffset >= pending.length) {
                pending = webSocket.readData();
                pendingOffset = 0;
                if (pending == null) throw new EOFException("WebSocket closed");
                if (pending.length == 0) continue;
            }
            int available = pending.length - pendingOffset;
            int take = Math.min(available, length - written);
            System.arraycopy(pending, pendingOffset, result, written, take);
            pendingOffset += take;
            written += take;
        }
        return result;
    }

    public int readUnsignedByte() throws IOException {
        return readExact(1)[0] & 0xFF;
    }

    public int readU16() throws IOException {
        byte[] data = readExact(2);
        return ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
    }

    @Override
    public void close() throws IOException {
        webSocket.close();
    }
}
