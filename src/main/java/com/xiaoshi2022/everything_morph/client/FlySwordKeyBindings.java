package com.xiaoshi2022.everything_morph.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class FlySwordKeyBindings {
    public static KeyMapping flySwordDown;
    public static KeyMapping flySwordAttack;

    public static void init() {
        // 向下飞行键位设置
        flySwordDown = new KeyMapping(
                "key.everything_morph.fly_sword_down",
                KeyConflictContext.IN_GAME,
                KeyModifier.NONE,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "key.categories.everything_morph"
        );

        // 攻击键位设置
        flySwordAttack = new KeyMapping(
                "key.everything_morph.fly_sword_attack",
                KeyConflictContext.IN_GAME,
                KeyModifier.NONE,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.categories.everything_morph"
        );
    }

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        init();
        event.register(flySwordDown);
        event.register(flySwordAttack);
    }
}