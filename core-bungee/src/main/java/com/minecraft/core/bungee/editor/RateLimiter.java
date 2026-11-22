package com.minecraft.core.bungee.editor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de requisições por endereço numa janela fixa.
 */
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final long window;

    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    public RateLimiter(int limit, long windowMillis) {
        this.limit = limit;
        this.window = windowMillis;
    }

    /* [0] = fim da janela, [1] = contagem */
    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();

        if (windows.size() > CLEANUP_THRESHOLD)
            windows.values().removeIf(entry -> entry[0] <= now);

        long[] updated = windows.compute(key, (ignored, current) -> current == null || current[0] <= now
                ? new long[]{now + window, 1}
                : new long[]{current[0], current[1] + 1});

        return updated[1] <= limit;
    }

    public boolean isBlocked(String key) {
        long[] current = windows.get(key);

        return current != null && current[0] > System.currentTimeMillis() && current[1] >= limit;
    }
}
