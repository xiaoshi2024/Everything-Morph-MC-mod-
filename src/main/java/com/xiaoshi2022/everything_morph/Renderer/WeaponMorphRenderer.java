package com.xiaoshi2022.everything_morph.Renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class WeaponMorphRenderer extends LivingEntityRenderer<WeaponMorphEntity, WeaponMorphModel> {

    private final PlayerRenderer playerRenderer;

    public WeaponMorphRenderer(EntityRendererProvider.Context context) {
        super(context, new WeaponMorphModel(context.bakeLayer(WeaponMorphModel.LAYER_LOCATION)), 0.5F);
        this.playerRenderer = new PlayerRenderer(context, false); // 使用Steve模型
    }

    @Override
    public ResourceLocation getTextureLocation(WeaponMorphEntity entity) {
        // 直接使用实体的皮肤纹理
        ResourceLocation skin = entity.getSkinTexture();
        if (skin != null) {
            return skin;
        }

        // 回退到默认皮肤
        return DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    public void render(WeaponMorphEntity entity, float entityYaw, float partialTicks,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {

        // 如果皮肤已加载，使用自定义渲染
        if (entity.getSkinLoadState() == WeaponMorphEntity.SkinLoadState.LOADED &&
                entity.getSkinTexture() != null) {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } else {
            // 否则使用玩家渲染器（避免闪烁）
            renderAsPlayer(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        }
    }

    private void renderAsPlayer(WeaponMorphEntity entity, float entityYaw, float partialTicks,
                                com.mojang.blaze3d.vertex.PoseStack poseStack,
                                net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        // 创建临时GameProfile
        GameProfile profile = new GameProfile(UUID.randomUUID(), "WeaponMorph");

        // 使用玩家渲染器渲染
        net.minecraft.client.player.RemotePlayer fakePlayer = new net.minecraft.client.player.RemotePlayer(
                Minecraft.getInstance().level, profile
        );

        fakePlayer.setPos(entity.getX(), entity.getY(), entity.getZ());
        fakePlayer.setYRot(entity.getYRot());
        fakePlayer.setXRot(entity.getXRot());
        fakePlayer.yBodyRot = entity.yBodyRot;
        fakePlayer.yHeadRot = entity.yHeadRot;

        playerRenderer.render(fakePlayer, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}