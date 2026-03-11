package com.xiaoshi2022.everything_morph.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public class SkinLoaderProxy {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation getSkinByName(String name) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientSkinLoader.getSkinByName(name);
        }
        return DefaultSkinProvider.getDefaultSkin();
    }

    public static boolean hasSkin(String name) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientSkinLoader.hasSkin(name);
        }
        return false;
    }

    public static void reloadExternalSkins() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSkinLoader.reloadExternalSkins();
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
    private static class ClientSkinLoader {
        static ResourceLocation getSkinByName(String name) {
            LOGGER.info("ClientSkinLoader.getSkinByName: '{}'", name);

            // 使用 CSLIntegration 获取皮肤
            ResourceLocation result = null;
            if (CSLIntegration.isAvailable()) {
                result = CSLIntegration.getSkin(name);
            }

            LOGGER.info("ClientSkinLoader.getSkinByName 结果: {}", result);
            return result;
        }

        static boolean hasSkin(String name) {
            LOGGER.info("ClientSkinLoader.hasSkin: '{}'", name);
            if (CSLIntegration.isAvailable()) {
                return CSLIntegration.getSkin(name) != null;
            }
            return false;
        }

        static void reloadExternalSkins() {
            LOGGER.info("ClientSkinLoader.reloadExternalSkins - CSL 会自动处理缓存");
            // CSL 会自动处理缓存，不需要额外操作
        }
    }
}