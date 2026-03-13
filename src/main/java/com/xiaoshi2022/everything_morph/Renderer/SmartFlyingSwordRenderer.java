package com.xiaoshi2022.everything_morph.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xiaoshi2022.everything_morph.entity.SmartFlyingSwordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

public class SmartFlyingSwordRenderer extends EntityRenderer<SmartFlyingSwordEntity> {
    private final Minecraft minecraft = Minecraft.getInstance();

    public SmartFlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(@Nonnull SmartFlyingSwordEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack itemStack = entity.getRenderItemStack();
        if (itemStack.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        // 智能飞行剑的渲染逻辑
        renderSmartSword(entity, partialTicks, poseStack, buffer, packedLight, itemStack);

        poseStack.popPose();

        // 渲染名称标签
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void renderSmartSword(SmartFlyingSwordEntity entity, float partialTicks,
                                  PoseStack poseStack, MultiBufferSource buffer,
                                  int packedLight, ItemStack itemStack) {
        // 设置固定的旋转角度，不跟随实体旋转
        poseStack.translate(0.0D, 0.3D, 0.0D); // 稍微抬高一点

        // 剑水平放置
        poseStack.mulPose(Axis.XP.rotationDegrees(90)); // 让剑水平放置
        poseStack.mulPose(Axis.ZP.rotationDegrees(180)); // 调整剑的方向

        // 添加缓慢的旋转动画效果
        float spin = (entity.tickCount + partialTicks) * 1.5F;
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));

        // 添加平滑的浮动效果
        float floatOffset = Mth.sin((entity.tickCount + partialTicks) * 0.05F) * 0.08F;
        poseStack.translate(0.0D, floatOffset, 0.0D);

        // 缩放1.5倍
        float scale = 1.5F;
        poseStack.scale(scale, scale, scale);

        // 渲染物品
        minecraft.getItemRenderer().renderStatic(
                itemStack,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                0
        );
    }

    @Override
    public ResourceLocation getTextureLocation(SmartFlyingSwordEntity entity) {
        // 使用物品渲染器，所以返回物品纹理集
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
