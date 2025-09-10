package com.xiaoshi2022.everything_morph;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final General GENERAL = new General(BUILDER);

    public static class General {
        public final ForgeConfigSpec.BooleanValue enableSkinCache;
        public final ForgeConfigSpec.IntValue skinCacheSizeMB;

        public General(ForgeConfigSpec.Builder builder) {
            builder.push("general");

            enableSkinCache = builder
                    .comment("启用皮肤缓存以提高性能")
                    .translation("config.everything_morph.enable_skin_cache")
                    .define("enableSkinCache", true);

            skinCacheSizeMB = builder
                    .comment("最大皮肤缓存大小(MB) (0表示无限制)")
                    .translation("config.everything_morph.skin_cache_size")
                    .defineInRange("skinCacheSizeMB", 100, 0, 1000);

            builder.pop();
        }
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // 配置值实例
    public static boolean enableSkinCache;
    public static int skinCacheSizeMB;

    // 注册配置
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "everything_morph-common.toml");
    }

    // 当配置加载或重载时更新值
    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            updateConfigValues();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            updateConfigValues();
        }
    }

    private static void updateConfigValues() {
        enableSkinCache = GENERAL.enableSkinCache.get();
        skinCacheSizeMB = GENERAL.skinCacheSizeMB.get();

        EverythingMorphMod.LOGGER.info("配置已加载: enableSkinCache={}, skinCacheSizeMB={}MB",
                enableSkinCache, skinCacheSizeMB);
    }
}