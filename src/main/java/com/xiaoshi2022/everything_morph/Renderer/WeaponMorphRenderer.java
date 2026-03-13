package com.xiaoshi2022.everything_morph.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class WeaponMorphRenderer extends LivingEntityRenderer<WeaponMorphEntity, WeaponMorphModel> {
    private static final Logger LOGGER = LogUtils.getLogger();

    public WeaponMorphRenderer(EntityRendererProvider.Context context) {
        super(context, new WeaponMorphModel(context.bakeLayer(WeaponMorphModel.LAYER_LOCATION)), 0.5F);
        System.out.println("WeaponMorphRenderer: Adding ItemInHandLayer");
        this.addLayer(new WeaponMorphItemInHandLayer(this, context.getItemInHandRenderer()));
        System.out.println("WeaponMorphRenderer: ItemInHandLayer added");
    }

    @Override
    public ResourceLocation getTextureLocation(WeaponMorphEntity entity) {
        // 如果已加载，使用自定义皮肤
        if (entity.getSkinLoadState() == WeaponMorphEntity.SkinLoadState.LOADED) {
            ResourceLocation skin = entity.getSkinTexture();
            if (skin != null) {
                return skin;
            }
        }

        // 如果正在加载或失败，直接使用 UUID 对应的默认皮肤
        return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }

    @Override
    public void render(WeaponMorphEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        try {
            super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } catch (Exception e) {
            LOGGER.error("渲染实体时出错: {}", e.getMessage());
        }
    }

    @Override
    protected void renderNameTag(WeaponMorphEntity entity, Component component,
                                 PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 什么都不做，完全禁用名称标签渲染
        // 这个方法体为空，就不会渲染名称标签
    }
}