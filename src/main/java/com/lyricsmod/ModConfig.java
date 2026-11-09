package com.lyricsmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    public static final String[] FONT_NAMES = {"Normal", "Tekdüze", "Büyülü (Galaktik)", "Illager"};
    public static final String[] FONT_IDS = {"minecraft:default", "minecraft:uniform", "minecraft:alt", "minecraft:illageralt"};

    public static final String[] COLOR_NAMES = {"Beyaz", "Camgöbeği", "Pembe", "Altın", "Yeşil", "Mor", "Gökkuşağı"};
    public static final int[] COLOR_VALUES = {0xFFFFFF, 0x55FFFF, 0xFF77C8, 0xFFD24A, 0x7CFF8A, 0xB388FF, -1};

    public static final String[] ANIM_NAMES = {"Yükselen", "Yörünge", "Pop"};

    public boolean enabled = true;
    public float textSize = 1.0f;
    public int fontIndex = 0;
    public boolean bold = false;
    public boolean italic = false;
    public int colorIndex = 0;
    public int animIndex = 0;
    public float distance = 4.0f;
    public float lifetimeSec = 5.0f;
    public boolean throughWalls = true;
    public int offsetMs = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance = new ModConfig();

    public static ModConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("lyricsmod.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try {
                ModConfig loaded = GSON.fromJson(Files.readString(p), ModConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    instance.sanitize();
                }
            } catch (Exception e) {
                LyricsMod.LOGGER.warn("Ayar dosyası okunamadı, varsayılanlar kullanılıyor", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (IOException e) {
            LyricsMod.LOGGER.warn("Ayar dosyası kaydedilemedi", e);
        }
    }

    private void sanitize() {
        textSize = Math.max(0.5f, Math.min(3.0f, textSize));
        distance = Math.max(2.0f, Math.min(8.0f, distance));
        lifetimeSec = Math.max(2.0f, Math.min(10.0f, lifetimeSec));
        offsetMs = Math.max(-3000, Math.min(3000, offsetMs));
        fontIndex = Math.floorMod(fontIndex, FONT_IDS.length);
        colorIndex = Math.floorMod(colorIndex, COLOR_VALUES.length);
        animIndex = Math.floorMod(animIndex, ANIM_NAMES.length);
    }
}
