package com.lyricsmod;

public record MediaState(boolean ok, String title, String artist, boolean playing,
                         long durMs, long posMs, long anchorMs) {

    public static final MediaState NONE = new MediaState(false, "", "", false, 0, 0, 0);

    /** Şarkının şu anki tahmini saniyesi (ms). */
    public long currentPos() {
        if (!playing) {
            return posMs;
        }
        long elapsed = System.currentTimeMillis() - anchorMs;
        elapsed = Math.max(0, Math.min(elapsed, 3_600_000L));
        long p = posMs + elapsed;
        return durMs > 0 ? Math.min(p, durMs) : p;
    }

    public String key() {
        return artist + "|" + title;
    }
}
