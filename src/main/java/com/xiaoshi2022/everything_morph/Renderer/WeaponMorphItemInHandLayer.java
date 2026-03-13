package com.xiaoshi2022.everything_morph.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WeaponMorphItemInHandLayer extends ItemInHandLayer<WeaponMorphEntity, WeaponMorphModel> {

    private final net.minecraft.client.renderer.ItemInHandRenderer itemInHandRenderer;

    public WeaponMorphItemInHandLayer(RenderLayerParent<WeaponMorphEntity, WeaponMorphModel> parent,
                                      net.minecraft.client.renderer.ItemInHandRenderer itemInHandRenderer) {
        super(parent, itemInHandRenderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       WeaponMorphEntity entity, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack mainHandItem = entity.getMainHandItem();

        if (mainHandItem.isEmpty()) {
            return;
        }

        WeaponMorphModel model = this.getParentModel();
        if (!(model instanceof HumanoidModel)) {
            return;
        }

        HumanoidModel<WeaponMorphEntity> humanoidModel = (HumanoidModel<WeaponMorphEntity>) model;

        poseStack.pushPose();

        // 获取头部骨骼并应用变换
        ModelPart head = humanoidModel.head;
        head.translateAndRotate(poseStack);

        // 使用保存的itemInHandRenderer引用
        this.itemInHandRenderer.renderItem(
                entity,
                mainHandItem,
                ItemDisplayContext.HEAD,  // 使用HEAD上下文，原版会处理所有偏移
                false,  // 不是左手（头部不需要区分左右手）
                poseStack,
                buffer,
                packedLight
        );

        poseStack.popPose();
    }

    // 完全禁用原版的手臂渲染，因为我们只想要头部物品
    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack itemStack,
                                     ItemDisplayContext displayContext, HumanoidArm arm,
                                     PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 空实现，不渲染任何手臂物品
    }
}