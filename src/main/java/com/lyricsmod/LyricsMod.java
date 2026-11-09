package com.lyricsmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LyricsMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("lyricsmod");

    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        MediaWatcher.start();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lyricsmod.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.lyricsmod"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new LyricsConfigScreen(client.currentScreen));
            }
            LyricsManager.tick(client);
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(LyricsRenderer::render);
    }
}
