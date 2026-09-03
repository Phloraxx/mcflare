package io.mcflare.core;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LoopbackCarrierTest {
    @Test
    void noLocalAttachmentTimesOutAndClosesCarrier() throws Exception {
        try (ServerSocket server = new ServerSocket(0);
             Socket raw = new Socket("127.0.0.1", server.getLocalPort());
             Socket peer = server.accept()) {
            Rfc6455Client webSocket = wrap(raw);
            CountDownLatch failed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            LoopbackCarrier carrier = LoopbackCarrier.start(webSocket, error -> {
                failure.set(error);
                failed.countDown();
            }, 100);
            int port = carrier.getLocalAddress().getPort();

            assertTrue(failed.await(2, TimeUnit.SECONDS), "carrier did not time out waiting for Minecraft");
            assertTrue(failure.get() instanceof SocketTimeoutException, String.valueOf(failure.get()));
            try (Socket probe = new Socket()) {
                try {
                    probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                    fail("closed carrier still accepted a local connection");
                } catch (java.io.IOException expected) {
                    // Listener is closed, which is the required state.
                }
            }
            carrier.close();
        }
    }

    private static Rfc6455Client wrap(Socket socket) throws Exception {
        Constructor<Rfc6455Client> constructor = Rfc6455Client.class.getDeclaredConstructor(Socket.class);
        constructor.setAccessible(true);
        return constructor.newInstance(socket);
    }
}
