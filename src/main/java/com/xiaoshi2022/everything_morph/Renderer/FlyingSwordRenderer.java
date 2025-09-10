package com.xiaoshi2022.everything_morph.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xiaoshi2022.everything_morph.entity.FlyingSwordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

public class FlyingSwordRenderer extends EntityRenderer<FlyingSwordEntity> {
    private final Minecraft minecraft = Minecraft.getInstance();

    public FlyingSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(@Nonnull FlyingSwordEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack itemStack = entity.getRenderItemStack();
        if (itemStack.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        // 获取玩家和控制状态
        Player controllingPlayer = entity.getControllingPlayer();
        boolean hasPassenger = controllingPlayer != null && entity.hasPassenger(controllingPlayer);

        if (hasPassenger) {
            // 当有玩家乘坐时，将剑固定在玩家脚下
            renderSwordUnderPlayer(entity, controllingPlayer, partialTicks, poseStack, buffer, packedLight, itemStack);
        } else {
            // 当没有玩家乘坐时，正常渲染剑
            renderFloatingSword(entity, partialTicks, poseStack, buffer, packedLight, itemStack);
        }

        poseStack.popPose();

        // 渲染名称标签
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void renderSwordUnderPlayer(FlyingSwordEntity entity, Player player, float partialTicks,
                                        PoseStack poseStack, MultiBufferSource buffer,
                                        int packedLight, ItemStack itemStack) {
        // 直接使用实体的位置，不要计算玩家位置差值
        // 这样可以避免鬼畜问题
        poseStack.translate(0.0D, 0.15D, 0.0D); // 让剑紧贴玩家脚底（正值让剑向上移动）

        // 使用固定的旋转角度，不跟随玩家视角
        // 剑水平放置，稍微向前倾斜
        poseStack.mulPose(Axis.XP.rotationDegrees(90)); // 让剑水平放置
        poseStack.mulPose(Axis.ZP.rotationDegrees(180)); // 调整剑的方向

        // 根据玩家移动方向轻微倾斜
        float forward = player.zza;
        if (forward > 0) {
            float tilt = Mth.clamp(forward * 3.0F, 0.0F, 8.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-tilt)); // 向前倾斜
        }

        // 轻微上下浮动效果 - 使用更平滑的动画
        float floatOffset = Mth.sin((entity.tickCount + partialTicks) * 0.05F) * 0.01F; // 非常小的浮动幅度
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

    private void renderFloatingSword(FlyingSwordEntity entity, float partialTicks,
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
    public ResourceLocation getTextureLocation(FlyingSwordEntity entity) {
        // 使用物品渲染器，所以返回物品纹理集
        return TextureAtlas.LOCATION_BLOCKS;
    }
}