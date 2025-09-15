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
    public static int skinLoadRetryDelay = 100; // 皮肤加载重试延迟(ms)
    public static int maxSkinLoadRetries = 3;   // 最大皮肤加载重试次数

    public static class General {
        public final ForgeConfigSpec.BooleanValue enableSkinCache;
        public final ForgeConfigSpec.IntValue skinCacheSizeMB;
        public final ForgeConfigSpec.IntValue networkRetryCount; // 新增网络重试次数
        public final ForgeConfigSpec.IntValue networkRetryDelay; // 新增网络重试延迟

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

            networkRetryCount = builder
                    .comment("网络包重试次数")
                    .translation("config.everything_morph.network_retry_count")
                    .defineInRange("networkRetryCount", 3, 1, 10);

            networkRetryDelay = builder
                    .comment("网络重试延迟(ms)")
                    .translation("config.everything_morph.network_retry_delay")
                    .defineInRange("networkRetryDelay", 50, 10, 1000);

            builder.pop();
        }
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // 配置值实例
    public static boolean enableSkinCache;
    public static int skinCacheSizeMB;
    public static int networkRetryCount; // 新增
    public static int networkRetryDelay; // 新增

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
        networkRetryCount = GENERAL.networkRetryCount.get(); // 新增
        networkRetryDelay = GENERAL.networkRetryDelay.get(); // 新增

        EverythingMorphMod.LOGGER.info("配置已加载: enableSkinCache={}, skinCacheSizeMB={}MB, networkRetryCount={}, networkRetryDelay={}ms",
                enableSkinCache, skinCacheSizeMB, networkRetryCount, networkRetryDelay);
    }
}