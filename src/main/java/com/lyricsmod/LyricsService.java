package com.lyricsmod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Zamanlamalı şarkı sözlerini LRCLIB'den (ücretsiz, hesap gerektirmez) çeker. */
public final class LyricsService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");

    private LyricsService() {
    }

    public static List<LyricLine> fetch(String artist, String title, long durMs) throws IOException, InterruptedException {
        String t = enc(title);
        String a = enc(artist);

        StringBuilder u = new StringBuilder("https://lrclib.net/api/get?track_name=" + t + "&artist_name=" + a);
        if (durMs > 0) {
            u.append("&duration=").append(Math.round(durMs / 1000.0));
        }
        String body = get(u.toString());
        if (body != null) {
            List<LyricLine> r = fromObject(JsonParser.parseString(body).getAsJsonObject());
            if (!r.isEmpty()) {
                return r;
            }
        }

        body = get("https://lrclib.net/api/search?track_name=" + t + "&artist_name=" + a);
        List<LyricLine> r = bestFromArray(body, durMs);
        if (!r.isEmpty()) {
            return r;
        }

        body = get("https://lrclib.net/api/search?q=" + enc(artist + " " + title));
        return bestFromArray(body, durMs);
    }

    private static List<LyricLine> bestFromArray(String body, long durMs) {
        if (body == null) {
            return List.of();
        }
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) {
            return List.of();
        }
        JsonArray arr = root.getAsJsonArray();
        JsonObject best = null;
        double bestDiff = Double.MAX_VALUE;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            if (!hasSynced(o)) {
                continue;
            }
            double d = 0;
            if (durMs > 0 && o.has("duration") && !o.get("duration").isJsonNull()) {
                d = Math.abs(o.get("duration").getAsDouble() - durMs / 1000.0);
            }
            if (d < bestDiff) {
                bestDiff = d;
                best = o;
            }
        }
        return best == null ? List.of() : fromObject(best);
    }

    private static boolean hasSynced(JsonObject o) {
        return o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()
                && !o.get("syncedLyrics").getAsString().isBlank();
    }

    private static List<LyricLine> fromObject(JsonObject o) {
        if (!hasSynced(o)) {
            return List.of();
        }
        return parseLrc(o.get("syncedLyrics").getAsString());
    }

    static List<LyricLine> parseLrc(String lrc) {
        List<LyricLine> out = new ArrayList<>();
        for (String raw : lrc.split("\\r?\\n")) {
            Matcher m = TAG.matcher(raw);
            int idx = 0;
            List<Long> times = new ArrayList<>();
            while (m.find() && m.start() == idx) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                String frac = m.group(3);
                if (frac != null) {
                    ms = Long.parseLong(frac);
                    if (frac.length() == 1) {
                        ms *= 100;
                    } else if (frac.length() == 2) {
                        ms *= 10;
                    }
                }
                times.add(min * 60_000 + sec * 1000 + ms);
                idx = m.end();
            }
            if (times.isEmpty()) {
                continue;
            }
            String text = raw.substring(idx).trim();
            if (text.isEmpty()) {
                continue;
            }
            for (long time : times) {
                out.add(new LyricLine(time, text));
            }
        }
        out.sort(Comparator.comparingLong(LyricLine::timeMs));
        return out;
    }

    /** 404 -> null, diğer hatalarda IOException. */
    private static String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "LyricsMod/1.0 (Minecraft Fabric mod)")
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 404) {
            return null;
        }
        if (res.statusCode() != 200) {
            throw new IOException("LRCLIB HTTP " + res.statusCode());
        }
        return res.body();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
