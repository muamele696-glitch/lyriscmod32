package com.lyricsmod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Windows'un "şu an çalan medya" bilgisini (SMTC) arka planda bir PowerShell
 * süreci ile okur. Spotify hesabı bağlamaya gerek yoktur.
 */
public final class MediaWatcher {

    private static volatile MediaState state = MediaState.NONE;
    private static volatile boolean running = false;
    private static volatile Process process;

    private MediaWatcher() {
    }

    public static MediaState get() {
        return state;
    }

    public static void start() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            LyricsMod.LOGGER.warn("Spotify okuma şu an sadece Windows'ta destekleniyor.");
            return;
        }
        running = true;
        Thread t = new Thread(MediaWatcher::loop, "lyricsmod-media");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            Process p = process;
            if (p != null) {
                p.destroyForcibly();
            }
        }));
    }

    private static Path extractScript() throws IOException {
        Path tmp = Files.createTempFile("lyricsmod-media", ".ps1");
        tmp.toFile().deleteOnExit();
        try (InputStream in = MediaWatcher.class.getResourceAsStream("/assets/lyricsmod/media.ps1")) {
            if (in == null) {
                throw new IOException("media.ps1 bulunamadı");
            }
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    private static void loop() {
        Path script;
        try {
            script = extractScript();
        } catch (IOException e) {
            LyricsMod.LOGGER.error("PowerShell scripti hazırlanamadı", e);
            return;
        }

        while (running) {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                        "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                        "-File", script.toString());
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process p = pb.start();
                process = p;
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = r.readLine()) != null) {
                        parse(line);
                    }
                }
                p.destroyForcibly();
            } catch (Exception e) {
                LyricsMod.LOGGER.warn("Medya okuyucu hata verdi", e);
            }
            state = MediaState.NONE;
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
                return;
            }
        }
    }

    private static void parse(String line) {
        int brace = line.indexOf('{');
        if (brace < 0) {
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(line.substring(brace)).getAsJsonObject();
            if (!o.has("ok") || !o.get("ok").getAsBoolean()) {
                state = MediaState.NONE;
                return;
            }
            long now = System.currentTimeMillis();
            long upd = o.has("updMs") ? o.get("updMs").getAsLong() : 0;
            long anchor = (upd > 1_000_000_000_000L && upd <= now + 5000) ? upd : now;
            state = new MediaState(
                    true,
                    str(o, "title"),
                    str(o, "artist"),
                    o.has("playing") && o.get("playing").getAsBoolean(),
                    o.has("durMs") ? o.get("durMs").getAsLong() : 0,
                    o.has("posMs") ? o.get("posMs").getAsLong() : 0,
                    anchor);
        } catch (Exception ignored) {
            // bozuk satırı atla
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? "" : e.getAsString();
    }
}
