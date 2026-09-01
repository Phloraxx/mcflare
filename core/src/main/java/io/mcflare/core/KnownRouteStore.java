package io.mcflare.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent positive-only trust-on-first-use pins for MCflare routes. */
final class KnownRouteStore {
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
        if (!validKey(key) || keys.contains(key)) return;
        if (file == null) {
            keys.add(key);
            return;
        }
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(file, Collections.singletonList(key), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            keys.add(key);
        } catch (IOException error) {
            throw new IllegalStateException("MCflare could not persist secure-route pin " + key, error);
        }
    }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        if (!Files.isRegularFile(file)) {
            loadFailure = "MCflare secure-route pin store is not a regular file: " + file;
            return;
        }
        try {
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
        try {
            int port = Integer.parseInt(key.substring(colon + 1));
            return port > 0 && port <= 65535;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
