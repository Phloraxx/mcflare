package io.mcflare.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

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
    void invalidNewPinIsRejectedInsteadOfSilentlySkippingPersistence() {
        KnownRouteStore store = new KnownRouteStore(null);
        assertThrows(IllegalArgumentException.class, () -> store.remember("not a host:25565"));
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
    void corruptStoreRejectsRememberWithoutMutatingFile() throws Exception {
        Path file = tempDir.resolve("known-hosts-v1.txt");
        byte[] original = ("good.example.com:25565\n" + "bad entry\n").getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        KnownRouteStore store = new KnownRouteStore(file);

        assertThrows(IllegalStateException.class, () -> store.remember("other.example.com:25565"));
        assertTrue(java.util.Arrays.equals(original, Files.readAllBytes(file)));
    }

    @Test
    void persistedPinUsesOwnerOnlyPermissionsWhenPosixIsAvailable() throws Exception {
        Path directory = tempDir.resolve("pins");
        Path file = directory.resolve("known-hosts-v1.txt");
        KnownRouteStore store = new KnownRouteStore(file);
        store.remember("play.example.com:25565");

        try {
            Set<PosixFilePermission> filePermissions = Files.getPosixFilePermissions(file);
            assertFalse(filePermissions.contains(PosixFilePermission.GROUP_READ));
            assertFalse(filePermissions.contains(PosixFilePermission.OTHERS_READ));
            Set<PosixFilePermission> directoryPermissions = Files.getPosixFilePermissions(directory);
            assertFalse(directoryPermissions.contains(PosixFilePermission.GROUP_EXECUTE));
            assertFalse(directoryPermissions.contains(PosixFilePermission.OTHERS_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems are covered by successful persistence above.
        }
    }

    @Test
    void oversizedPersistedStoreFailsClosedWithoutLoadingIt() throws Exception {
        Path file = tempDir.resolve("known-hosts-v1.txt");
        byte[] oversized = new byte[1024 * 1024 + 1];
        java.util.Arrays.fill(oversized, (byte) 'x');
        Files.write(file, oversized);

        KnownRouteStore store = new KnownRouteStore(file);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> store.contains("play.example.com:25565"));
        assertTrue(error.getMessage().contains("too large"));
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
