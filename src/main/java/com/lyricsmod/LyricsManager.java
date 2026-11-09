package com.lyricsmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LyricsManager {

    public enum Status {
        NO_SPOTIFY("Spotify'da çalan şarkı bulunamadı"),
        LOADING("Sözler aranıyor..."),
        FOUND("Sözler bulundu"),
        NOT_FOUND("Bu şarkının zamanlamalı sözü bulunamadı"),
        ERROR("Söz servisine ulaşılamadı");

        public final String text;

        Status(String text) {
            this.text = text;
        }
    }

    /** Havada duran tek bir söz satırı. */
    public static final class Floating {
        public final String text;
        public final long spawnMs;
        public final long lifeMs;
        public final float angle;
        public final float yOff;
        public final float radius;
        public final int seq;

        Floating(String text, long spawnMs, long lifeMs, float angle, float yOff, float radius, int seq) {
            this.text = text;
            this.spawnMs = spawnMs;
            this.lifeMs = lifeMs;
            this.angle = angle;
            this.yOff = yOff;
            this.radius = radius;
            this.seq = seq;
        }
    }

    private static volatile Status status = Status.NO_SPOTIFY;
    private static volatile List<LyricLine> lyrics = List.of();
    private static volatile String currentKey = "";
    private static volatile String currentLabel = "";

    private static int lastIndex = -1;
    private static int counter = 0;
    private static final List<Floating> ACTIVE = new ArrayList<>();

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lyricsmod-fetch");
        t.setDaemon(true);
        return t;
    });

    private LyricsManager() {
    }

    public static Status status() {
        return status;
    }

    public static String currentLabel() {
        return currentLabel;
    }

    public static List<Floating> active() {
        return ACTIVE;
    }

    public static void tick(MinecraftClient mc) {
        ModConfig cfg = ModConfig.get();
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(f -> now - f.spawnMs > f.lifeMs);

        if (!cfg.enabled || mc.player == null) {
            return;
        }

        MediaState s = MediaWatcher.get();
        if (!s.ok() || s.title().isEmpty()) {
            status = Status.NO_SPOTIFY;
            currentLabel = "";
            return;
        }

        String key = s.key();
        if (!key.equals(currentKey)) {
            currentKey = key;
            currentLabel = s.artist() + " - " + s.title();
            lyrics = List.of();
            lastIndex = -1;
            status = Status.LOADING;
            ACTIVE.clear();
            startFetch(s, key);
        }

        if (status != Status.FOUND || !s.playing()) {
            return;
        }

        long pos = s.currentPos() + cfg.offsetMs;
        List<LyricLine> ls = lyrics;
        int idx = indexAt(ls, pos);
        if (idx != lastIndex) {
            boolean fresh = lastIndex != -1 || idx < 0 || pos - ls.get(idx).timeMs() < 4000;
            lastIndex = idx;
            if (idx >= 0 && fresh) {
                spawn(ls.get(idx).text(), cfg, now);
            }
        }
    }

    private static void spawn(String text, ModConfig cfg, long now) {
        int seq = counter++;
        // Altın açı: ardışık satırlar birbirinin üstüne binmesin
        float angle = (float) (seq * 2.399963 + (Math.random() - 0.5) * 0.4);
        float y = ((seq % 5) - 2) * 0.32f + 0.15f;
        ACTIVE.add(new Floating(text, now, (long) (cfg.lifetimeSec * 1000), angle, y, cfg.distance, seq));
        while (ACTIVE.size() > 12) {
            ACTIVE.remove(0);
        }
    }

    private static int indexAt(List<LyricLine> ls, long pos) {
        int lo = 0;
        int hi = ls.size() - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ls.get(mid).timeMs() <= pos) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }

    private static void startFetch(MediaState s, String key) {
        final String artist = s.artist();
        final String title = s.title();
        final long dur = s.durMs();
        EXEC.submit(() -> {
            try {
                List<LyricLine> r = LyricsService.fetch(artist, title, dur);
                if (!key.equals(currentKey)) {
                    return;
                }
                lyrics = r;
                status = r.isEmpty() ? Status.NOT_FOUND : Status.FOUND;
                if (!r.isEmpty()) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.execute(() -> {
                        if (mc.inGameHud != null && key.equals(currentKey)) {
                            mc.inGameHud.setOverlayMessage(Text.literal("♪ " + artist + " - " + title), false);
                        }
                    });
                }
            } catch (Exception e) {
                LyricsMod.LOGGER.warn("Sözler alınamadı: {} - {}", artist, title, e);
                if (key.equals(currentKey)) {
                    status = Status.ERROR;
                }
            }
        });
    }
}
