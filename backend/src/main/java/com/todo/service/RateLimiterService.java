package com.todo.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_DURATION_MS = 15 * 60 * 1000; // 15 min

    private final ConcurrentMap<String, int[]> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        int[] record = attempts.get(key);
        if (record == null) return false;
        if (record[1] > 0 && System.currentTimeMillis() - record[1] < BLOCK_DURATION_MS) {
            return record[0] > MAX_ATTEMPTS;
        }
        attempts.remove(key);
        return false;
    }

    public void recordFailure(String key) {
        int[] record = attempts.computeIfAbsent(key, k -> new int[2]);
        record[0]++;
        if (record[0] > MAX_ATTEMPTS) {
            record[1] = (int) System.currentTimeMillis();
        }
    }

    public void clear(String key) {
        attempts.remove(key);
    }
}
