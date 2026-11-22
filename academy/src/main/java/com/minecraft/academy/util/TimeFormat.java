package com.minecraft.academy.util;

public final class TimeFormat {

    private TimeFormat() {
    }

    public static String millis(long millis) {
        long minutes = millis / 60000L;
        long seconds = (millis / 1000L) % 60L;
        long rest = millis % 1000L;

        return String.format("%02d:%02d.%03d", minutes, seconds, rest);
    }

    public static String seconds(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public static String difference(long millis) {
        return String.format("%.3fs", Math.abs(millis) / 1000D).replace(',', '.');
    }
}
