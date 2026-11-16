package com.pinpoint.saigai.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.pinpoint.saigai.KeyPayload;
import com.pinpoint.saigai.Saigai;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** キー：押すとサーバーへ合図を送るだけ。画面には何も出さない（音も鳴らさない）。 */
public class SaigaiClient implements ClientModInitializer {

    public static KeyMapping THUNDER, WIND, BLIZZARD;
    public static int sentThunder, sentWind, sentBlizzard;

    @Override
    public void onInitializeClient() {
        THUNDER = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ppsaigai.thunder", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J, KeyMapping.Category.MISC));
        WIND = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ppsaigai.wind", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
        BLIZZARD = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ppsaigai.blizzard", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(SaigaiClient::tick);
    }

    private static void tick(Minecraft mc) {
        while (THUNDER.consumeClick()) send(KeyPayload.THUNDER);
        while (WIND.consumeClick()) send(KeyPayload.WIND);
        while (BLIZZARD.consumeClick()) send(KeyPayload.BLIZZARD);
    }

    static void send(int kind) {
        if (Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(KeyPayload.TYPE)) {
            Saigai.LOG.info("(ppsaigai) キー {}：サーバーに ppsaigai が無いので送らない", kind);
            return;
        }
        ClientPlayNetworking.send(new KeyPayload(kind));
        if (kind == KeyPayload.THUNDER) sentThunder++; else if (kind == KeyPayload.BLIZZARD) sentBlizzard++; else sentWind++;
        Saigai.LOG.info("(ppsaigai) キー {} を送った", kind == KeyPayload.THUNDER ? "雷" : kind == KeyPayload.BLIZZARD ? "吹雪" : "暴風");
    }
}
