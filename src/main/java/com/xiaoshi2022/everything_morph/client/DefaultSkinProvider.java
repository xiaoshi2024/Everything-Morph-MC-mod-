package com.xiaoshi2022.everything_morph.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.Map;
import java.util.UUID;

public class DefaultSkinProvider {

    public static ResourceLocation getDefaultSkin() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientDefaultSkin.getDefaultSkin();
        }
        // 服务端返回一个占位符
        return new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");
    }

    public static ResourceLocation getSkinForPlayer(Player player) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientDefaultSkin.getPlayerSkin(player);
        }
        return getDefaultSkin();
    }

    public static ResourceLocation getSkinByUUID(UUID uuid) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return ClientDefaultSkin.getSkinByUUID(uuid);
        }
        return getDefaultSkin();
    }

    @net.minecraftforge.api.distmarker.OnlyIn(Dist.CLIENT)
    private static class ClientDefaultSkin {

        static ResourceLocation getDefaultSkin() {
            return DefaultPlayerSkin.getDefaultSkin();
        }

        static ResourceLocation getPlayerSkin(Player player) {
            if (player == null) {
                return DefaultPlayerSkin.getDefaultSkin();
            }

            try {
                // 方法1：使用 GameProfile 和 SkinManager 获取皮肤
                GameProfile gameProfile = player.getGameProfile();
                if (gameProfile != null) {
                    Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> skinMap =
                            Minecraft.getInstance().getSkinManager().getInsecureSkinInformation(gameProfile);

                    if (skinMap.containsKey(MinecraftProfileTexture.Type.SKIN)) {
                        MinecraftProfileTexture skinTexture = skinMap.get(MinecraftProfileTexture.Type.SKIN);
                        return Minecraft.getInstance().getSkinManager().registerTexture(skinTexture, MinecraftProfileTexture.Type.SKIN);
                    }
                }
            } catch (Exception e) {
                // 忽略异常，返回默认皮肤
            }

            // 方法2：如果无法获取，使用基于UUID的默认皮肤
            return DefaultPlayerSkin.getDefaultSkin(player.getUUID());
        }

        static ResourceLocation getSkinByUUID(UUID uuid) {
            return DefaultPlayerSkin.getDefaultSkin(uuid);
        }
    }
}