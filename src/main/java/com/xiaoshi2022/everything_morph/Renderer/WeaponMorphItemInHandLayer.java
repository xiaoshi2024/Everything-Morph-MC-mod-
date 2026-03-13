package com.xiaoshi2022.everything_morph.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WeaponMorphItemInHandLayer extends ItemInHandLayer<WeaponMorphEntity, WeaponMorphModel> {
    
    public WeaponMorphItemInHandLayer(RenderLayerParent<WeaponMorphEntity, WeaponMorphModel> parent, 
                                       net.minecraft.client.renderer.ItemInHandRenderer itemInHandRenderer) {
        super(parent, itemInHandRenderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, 
                       WeaponMorphEntity entity, float limbSwing, float limbSwingAmount, 
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack mainHandItem = entity.getMainHandItem();
        
        System.out.println("WeaponMorphItemInHandLayer.render called - item: " + mainHandItem);
        
        if (mainHandItem.isEmpty()) {
            System.out.println("Item is empty, skipping render");
            return;
        }

        WeaponMorphModel model = this.getParentModel();
        if (!(model instanceof HumanoidModel)) {
            System.out.println("Model is not HumanoidModel, skipping render");
            return;
        }

        HumanoidModel<WeaponMorphEntity> humanoidModel = (HumanoidModel<WeaponMorphEntity>) model;
        
        System.out.println("rightArm: " + humanoidModel.rightArm);
        System.out.println("leftArm: " + humanoidModel.leftArm);

        poseStack.pushPose();

        // 获取右手模型部件并应用变换
        ModelPart rightArm = humanoidModel.rightArm;
        rightArm.translateAndRotate(poseStack);
        
        // 调整物品位置到手部 - 这些值需要根据实际模型调整
        // 原版 PlayerRenderer 使用的值
        poseStack.translate(0.0F, 0.125F, -0.125F);
        
        this.renderArmWithItem(entity, mainHandItem, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, 
                               HumanoidArm.RIGHT, poseStack, buffer, packedLight);

        poseStack.popPose();
    }
}
