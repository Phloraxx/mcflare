package io.mcflare.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownRouteStoreTest {
    @TempDir Path tempDir;

    @Test
    void persistsOnlyValidPositivePinsAcrossInstances() throws Exception {
        Path file = tempDir.resolve("known-hosts-v1.txt");
        KnownRouteStore first = new KnownRouteStore(file);
        first.remember("play.example.com:25565");

        KnownRouteStore second = new KnownRouteStore(file);
        assertTrue(second.contains("play.example.com:25565"));
        assertFalse(second.contains("other.example.com:25565"));
    }

    @Test
    void corruptPersistedDataFailsClosed() throws Exception {
        Path file = tempDir.resolve("known-hosts-v1.txt");
        Files.write(file, ("good.example.com:25565\n" +
                "bad entry\n").getBytes(StandardCharsets.UTF_8));

        KnownRouteStore store = new KnownRouteStore(file);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> store.contains("good.example.com:25565"));
        assertTrue(error.getMessage().contains("invalid data"));
    }

    @Test
    void failedAppendDoesNotCreateInMemoryPin() throws Exception {
        Path notDirectory = tempDir.resolve("not-a-directory");
        Files.write(notDirectory, new byte[] {1});
        KnownRouteStore store = new KnownRouteStore(notDirectory.resolve("known-hosts-v1.txt"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> store.remember("play.example.com:25565"));
        assertTrue(error.getMessage().contains("could not persist"));
        assertFalse(store.contains("play.example.com:25565"));
    }
}
