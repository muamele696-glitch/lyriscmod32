package com.lyricsmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;

public class LyricsConfigScreen extends Screen {

    private final Screen parent;

    public LyricsConfigScreen(Screen parent) {
        super(Text.literal("Spotify Şarkı Sözleri"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig c = ModConfig.get();
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = 50;
        int step = 24;

        // Sol sütun
        addToggle(left, y, "Mod", () -> c.enabled, v -> c.enabled = v);
        addSlider(left, y + step, "Yazı boyutu", 0.5, 3.0, c.textSize,
                v -> String.format("%.1fx", v), v -> c.textSize = (float) (Math.round(v * 10) / 10.0));
        addSlider(left, y + step * 2, "Uzaklık", 2.0, 8.0, c.distance,
                v -> String.format("%.1f blok", v), v -> c.distance = (float) (Math.round(v * 2) / 2.0));
        addSlider(left, y + step * 3, "Ekranda kalma", 2.0, 10.0, c.lifetimeSec,
                v -> String.format("%.0f sn", v), v -> c.lifetimeSec = (float) Math.round(v));
        addSlider(left, y + step * 4, "Söz senkronu", -3000, 3000, c.offsetMs,
                v -> (Math.round(v / 50.0) * 50) + " ms", v -> c.offsetMs = (int) (Math.round(v / 50.0) * 50));

        // Sağ sütun
        addCycle(right, y, "Yazı stili", () -> ModConfig.FONT_NAMES[c.fontIndex],
                () -> c.fontIndex = (c.fontIndex + 1) % ModConfig.FONT_IDS.length);
        addCycle(right, y + step, "Renk", () -> ModConfig.COLOR_NAMES[c.colorIndex],
                () -> c.colorIndex = (c.colorIndex + 1) % ModConfig.COLOR_VALUES.length);
        addCycle(right, y + step * 2, "Animasyon", () -> ModConfig.ANIM_NAMES[c.animIndex],
                () -> c.animIndex = (c.animIndex + 1) % ModConfig.ANIM_NAMES.length);
        addToggle(right, y + step * 3, "Kalın", () -> c.bold, v -> c.bold = v);
        addToggle(right, y + step * 4, "İtalik", () -> c.italic, v -> c.italic = v);
        addToggle(right, y + step * 5, "Duvar arkasında görünsün", () -> c.throughWalls, v -> c.throughWalls = v);

        addDrawableChild(ButtonWidget.builder(Text.literal("Bitti"), b -> close())
                .dimensions(this.width / 2 - 100, this.height - 32, 200, 20).build());
    }

    private void addToggle(int x, int y, String label, Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
        addDrawableChild(ButtonWidget.builder(toggleText(label, getter.get()), b -> {
            setter.accept(!getter.get());
            b.setMessage(toggleText(label, getter.get()));
        }).dimensions(x, y, 150, 20).build());
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "Açık" : "Kapalı"));
    }

    private void addCycle(int x, int y, String label, Supplier<String> value, Runnable next) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + value.get()), b -> {
            next.run();
            b.setMessage(Text.literal(label + ": " + value.get()));
        }).dimensions(x, y, 150, 20).build());
    }

    private void addSlider(int x, int y, String label, double min, double max, double current,
                           DoubleFunction<String> fmt, DoubleConsumer setter) {
        addDrawableChild(new ConfigSlider(x, y, 150, 20, label, min, max, current, fmt, setter));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        String label = LyricsManager.currentLabel();
        if (!label.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("♪ " + label),
                    this.width / 2, this.height - 66, 0xFFD24A);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(LyricsManager.status().text),
                this.width / 2, this.height - 52, 0xAAAAAA);
    }

    @Override
    public void removed() {
        ModConfig.save();
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static class ConfigSlider extends SliderWidget {
        private final String label;
        private final double min;
        private final double max;
        private final DoubleFunction<String> fmt;
        private final DoubleConsumer setter;

        ConfigSlider(int x, int y, int w, int h, String label, double min, double max, double current,
                     DoubleFunction<String> fmt, DoubleConsumer setter) {
            super(x, y, w, h, Text.empty(), (current - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.fmt = fmt;
            this.setter = setter;
            updateMessage();
        }

        private double real() {
            return min + this.value * (max - min);
        }

        @Override
        protected void updateMessage() {
            if (label == null) {
                return;
            }
            setMessage(Text.literal(label + ": " + fmt.apply(real())));
        }

        @Override
        protected void applyValue() {
            setter.accept(real());
        }
    }
}
