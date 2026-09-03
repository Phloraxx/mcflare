package io.mcflare.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent positive-only trust-on-first-use pins for MCflare routes. */
final class KnownRouteStore {
    private static final long MAX_FILE_BYTES = 1024L * 1024L;

    private final Set<String> keys = ConcurrentHashMap.newKeySet();
    private final Path file;
    private volatile String loadFailure;

    KnownRouteStore(Path file) {
        this.file = file;
        load();
    }

    boolean contains(String key) {
        if (loadFailure != null) throw new IllegalStateException(loadFailure);
        return keys.contains(key);
    }

    synchronized void remember(String key) {
        if (!validKey(key)) throw new IllegalArgumentException("invalid secure-route pin: " + key);
        if (keys.contains(key)) return;
        if (file == null) {
            keys.add(key);
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                restrictDirectoryPermissions(parent);
            }
            byte[] encoded = (key + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                long size = channel.size();
                if (size > MAX_FILE_BYTES - encoded.length) {
                    throw new IOException("secure-route pin store is too large");
                }
                channel.position(size);
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            restrictFilePermissions(file);
            keys.add(key);
        } catch (IOException error) {
            throw new IllegalStateException("MCflare could not persist secure-route pin " + key, error);
        }
    }

    private static void restrictDirectoryPermissions(Path directory) {
        try {
            Files.setPosixFilePermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX filesystems rely on their native ACL/default permissions.
        }
    }

    private static void restrictFilePermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX filesystems rely on their native ACL/default permissions.
        }
    }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        if (!Files.isRegularFile(file)) {
            loadFailure = "MCflare secure-route pin store is not a regular file: " + file;
            return;
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                loadFailure = "MCflare secure-route pin store is too large: " + file;
                return;
            }
            Path parent = file.getParent();
            if (parent != null) restrictDirectoryPermissions(parent);
            restrictFilePermissions(file);
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String key = line.trim();
                if (key.isEmpty()) continue;
                if (!validKey(key)) {
                    keys.clear();
                    loadFailure = "MCflare secure-route pin store contains invalid data: " + file;
                    return;
                }
                keys.add(key);
            }
        } catch (IOException error) {
            keys.clear();
            loadFailure = "MCflare could not read secure-route pins from " + file;
        }
    }

    static boolean validKey(String key) {
        if (key == null) return false;
        int colon = key.lastIndexOf(':');
        if (colon <= 0 || colon == key.length() - 1) return false;
        String host = key.substring(0, colon);
        if (!RouteResolver.isProbeCandidate(host) || !host.equals(RouteResolver.normalizeHost(host))) return false;
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            if (c <= 0x20 || c == 0x7F || c == '/' || c == '\\') return false;
        }
        try {
            int port = Integer.parseInt(key.substring(colon + 1));
            return port > 0 && port <= 65535;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
