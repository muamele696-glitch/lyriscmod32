package com.lyricsmod;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public final class LyricsRenderer {

    private LyricsRenderer() {
    }

    public static void render(WorldRenderContext ctx) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.enabled) {
            return;
        }
        List<LyricsManager.Floating> list = LyricsManager.active();
        if (list.isEmpty()) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        MatrixStack matrices = ctx.matrixStack();
        if (player == null || matrices == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();
        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d eye = player.getCameraPosVec(tickDelta);
        TextRenderer font = mc.textRenderer;
        VertexConsumerProvider.Immediate buffers = mc.getBufferBuilders().getEntityVertexConsumers();

        Style style = Style.EMPTY
                .withFont(Identifier.of(ModConfig.FONT_IDS[cfg.fontIndex]))
                .withBold(cfg.bold)
                .withItalic(cfg.italic);

        long now = System.currentTimeMillis();

        for (LyricsManager.Floating f : list) {
            float age = (now - f.spawnMs) / 1000f;
            float life = f.lifeMs / 1000f;
            if (age < 0 || age >= life) {
                continue;
            }

            float fadeIn = Math.min(1f, age / 0.35f);
            float fadeOut = Math.min(1f, (life - age) / 1.0f);
            float alpha = MathHelper.clamp(Math.min(fadeIn, fadeOut), 0f, 1f);
            int a = Math.max(8, (int) (alpha * 255f));

            float angle = f.angle;
            float rise = 0f;
            float popScale = 1f;
            switch (cfg.animIndex) {
                case 1 -> { // Yörünge
                    angle += age * 0.35f;
                    rise = MathHelper.sin(age * 1.6f + f.seq) * 0.12f;
                }
                case 2 -> { // Pop
                    float t = Math.min(1f, age / 0.4f);
                    // easeOutBack
                    float c1 = 1.70158f;
                    float c3 = c1 + 1f;
                    float e = 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
                    popScale = 0.5f + 0.5f * e;
                    rise = MathHelper.sin(age * 1.2f + f.seq) * 0.06f;
                }
                default -> rise = age * 0.25f; // Yükselen
            }

            double x = eye.x + Math.cos(angle) * f.radius;
            double z = eye.z + Math.sin(angle) * f.radius;
            double y = eye.y + f.yOff + rise;

            int rgb = ModConfig.COLOR_VALUES[cfg.colorIndex];
            if (rgb == -1) {
                float hue = ((now % 6000L) / 6000f + f.seq * 0.13f) % 1f;
                rgb = MathHelper.hsvToRgb(hue, 0.55f, 1f);
            }
            int color = (a << 24) | (rgb & 0xFFFFFF);

            Text text = Text.literal(f.text).setStyle(style);
            float width = font.getWidth(text);

            matrices.push();
            matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
            matrices.multiply(camera.getRotation());
            float scale = 0.025f * cfg.textSize * popScale;
            matrices.scale(-scale, -scale, scale);
            Matrix4f mat = matrices.peek().getPositionMatrix();

            font.draw(text, -width / 2f, 0f, color, true, mat, buffers,
                    cfg.throughWalls ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                    0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();
        }

        buffers.draw();
    }
}
