package com.xiaoshi2022.everything_morph.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "everything_morph", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FlySwordKeyBindings {
    public static KeyMapping flySwordDown;
    public static KeyMapping summonMorph;

    public static void init() {
        flySwordDown = new KeyMapping(
                "key.everything_morph.fly_sword_down",
                KeyConflictContext.IN_GAME,
                KeyModifier.NONE,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_X,
                "key.categories.everything_morph"
        );

        summonMorph = new KeyMapping(
                "key.everything_morph.summon_morph",
                KeyConflictContext.IN_GAME,
                KeyModifier.NONE,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Z,
                "key.categories.everything_morph"
        );
    }

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        init();
        event.register(flySwordDown);
        event.register(summonMorph);
    }
}
