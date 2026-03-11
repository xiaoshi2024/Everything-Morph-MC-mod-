package com.xiaoshi2022.everything_morph.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.logging.LogUtils;
import customskinloader.CustomSkinLoader;
import customskinloader.profile.UserProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * CustomSkinLoader 集成工具类
 */
public class CSLIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean cslLoaded = false;

    static {
        try {
            cslLoaded = ModList.get().isLoaded("customskinloader");
            if (cslLoaded) {
                LOGGER.info("CustomSkinLoader 已加载，将使用 CSL 获取皮肤");
            } else {
                LOGGER.info("CustomSkinLoader 未安装，将使用默认皮肤");
            }
        } catch (Exception e) {
            LOGGER.warn("检查 CSL 时出错", e);
            cslLoaded = false;
        }
    }

    /**
     * 根据用户名获取皮肤
     * @param username 玩家名/物品名
     * @return 已注册的 ResourceLocation，或 null
     */
    public static ResourceLocation getSkin(String username) {
        if (!cslLoaded || username == null || username.isEmpty()) {
            return null;
        }

        try {
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(username.getBytes()), username);
            UserProfile userProfile = CustomSkinLoader.loadProfile(profile);

            if (userProfile != null && userProfile.skinUrl != null && !userProfile.skinUrl.isEmpty()) {
                LOGGER.info("CSL 找到皮肤: {} -> {}", username, userProfile.skinUrl);

                // 创建 MinecraftProfileTexture 并注册
                MinecraftProfileTexture texture = new MinecraftProfileTexture(userProfile.skinUrl, null);
                ResourceLocation location = Minecraft.getInstance().getSkinManager().registerTexture(texture, MinecraftProfileTexture.Type.SKIN);

                LOGGER.info("皮肤已注册到: {}", location);
                return location;
            }
        } catch (Exception e) {
            LOGGER.warn("从 CSL 获取皮肤失败: {}", e.getMessage());
        }

        return null;
    }

    public static boolean isAvailable() {
        return cslLoaded;
    }
}