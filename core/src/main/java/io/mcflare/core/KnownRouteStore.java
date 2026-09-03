package io.mcflare.core;

import java.io.IOException;
import java.net.IDN;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
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
        if (loadFailure != null) throw new IllegalStateException(loadFailure);
        if (!validKey(key)) throw new IllegalArgumentException("invalid secure-route pin: " + key);
        if (keys.contains(key)) return;
        if (file == null) {
            keys.add(key);
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) ensurePrivateDirectory(parent);
            ensurePrivateFile(file);
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                long size = channel.size();
                Set<String> diskKeys = readValidatedKeys(channel);
                keys.addAll(diskKeys);
                if (diskKeys.contains(key)) return;
                boolean needsSeparator = size > 0L && !endsWithLineBreak(channel, size);
                byte[] encoded = ((needsSeparator ? "\n" : "") + key + "\n").getBytes(StandardCharsets.UTF_8);
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
        } catch (IOException | OverlappingFileLockException error) {
            throw new IllegalStateException("MCflare could not persist secure-route pin " + key, error);
        }
    }

    private static void ensurePrivateDirectory(Path directory) throws IOException {
        if (supportsPosix(directory)) {
            try {
                Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE)));
            } catch (UnsupportedOperationException error) {
                throw new IOException("could not create private secure-route directory", error);
            }
        } else {
            Files.createDirectories(directory);
        }
        restrictDirectoryPermissions(directory);
    }

    private static void ensurePrivateFile(Path path) throws IOException {
        try {
            if (supportsPosix(path)) {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
            } else {
                Files.createFile(path);
            }
        } catch (FileAlreadyExistsException ignored) {
            // Existing stores are validated and permission-hardened below.
        } catch (UnsupportedOperationException error) {
            throw new IOException("could not create private secure-route pin store", error);
        }
        if (!Files.isRegularFile(path)) throw new IOException("secure-route pin store is not a regular file");
        restrictFilePermissions(path);
    }

    private static boolean endsWithLineBreak(FileChannel channel, long size) throws IOException {
        ByteBuffer last = ByteBuffer.allocate(1);
        channel.position(size - 1L);
        if (channel.read(last) != 1) throw new IOException("could not read secure-route pin store tail");
        byte value = last.array()[0];
        return value == '\n' || value == '\r';
    }

    private static Set<String> readValidatedKeys(FileChannel channel) throws IOException {
        long size = channel.size();
        if (size > MAX_FILE_BYTES) throw new IOException("secure-route pin store is too large");
        ByteBuffer bytes = ByteBuffer.allocate((int) size);
        channel.position(0L);
        while (bytes.hasRemaining()) {
            int read = channel.read(bytes);
            if (read < 0) break;
        }
        bytes.flip();
        final String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(bytes).toString();
        } catch (CharacterCodingException error) {
            throw new IOException("secure-route pin store is not valid UTF-8", error);
        }
        Set<String> result = new java.util.LinkedHashSet<String>();
        for (String line : text.split("\r\n|\n|\r", -1)) {
            if (line.isEmpty()) continue;
            String candidate = line.trim();
            if (!candidate.equals(line) || !validKey(candidate)) {
                throw new IOException("secure-route pin store contains invalid data");
            }
            result.add(candidate);
        }
        return result;
    }

    private static void restrictDirectoryPermissions(Path directory) throws IOException {
        if (!supportsPosix(directory)) return;
        Files.setPosixFilePermissions(directory, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
    }

    private static void restrictFilePermissions(Path path) throws IOException {
        if (!supportsPosix(path)) return;
        Files.setPosixFilePermissions(path, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
    }

    private static boolean supportsPosix(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        if (!Files.isRegularFile(file)) {
            loadFailure = "MCflare secure-route pin store is not a regular file: " + file;
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) restrictDirectoryPermissions(parent);
            restrictFilePermissions(file);
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
                 FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
                if (channel.size() > MAX_FILE_BYTES) {
                    loadFailure = "MCflare secure-route pin store is too large: " + file;
                    return;
                }
                try {
                    keys.addAll(readValidatedKeys(channel));
                } catch (IOException invalid) {
                    keys.clear();
                    loadFailure = "MCflare secure-route pin store contains invalid data: " + file;
                    return;
                }
            }
        } catch (IOException | OverlappingFileLockException error) {
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
            if (Character.isISOControl(c) || Character.isWhitespace(c) || c == '/' || c == '\\') return false;
        }
        try {
            String ascii = IDN.toASCII(host);
            if (ascii.isEmpty() || ascii.length() > 253 || ascii.startsWith(".")
                    || ascii.endsWith(".") || ascii.contains("..")) return false;
        } catch (IllegalArgumentException invalidHost) {
            return false;
        }
        try {
            int port = Integer.parseInt(key.substring(colon + 1));
            return port > 0 && port <= 65535;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
