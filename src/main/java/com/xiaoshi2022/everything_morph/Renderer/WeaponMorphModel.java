package com.xiaoshi2022.everything_morph.Renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;

/**
 * 化形实体模型 - 定义实体的3D形态
 */
public class WeaponMorphModel extends HumanoidModel<WeaponMorphEntity> {
    // 模型层位置定义
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(EverythingMorphMod.MODID, "weapon_morph"), "main");
    public static final ModelLayerLocation INNER_ARMOR_LOCATION = new ModelLayerLocation(
            new ResourceLocation(EverythingMorphMod.MODID, "weapon_morph"), "inner_armor");
    public static final ModelLayerLocation OUTER_ARMOR_LOCATION = new ModelLayerLocation(
            new ResourceLocation(EverythingMorphMod.MODID, "weapon_morph"), "outer_armor");

    public WeaponMorphModel(ModelPart root) {
        super(root);
    }

    /**
     * 构建模型的几何结构
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition partdefinition = meshdefinition.getRoot();

        // 基本的人类形态模型，与Steve/Alex相同
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    /**
     * 构建护甲层的几何结构
     */
    public static LayerDefinition createArmorLayer(CubeDeformation deformation) {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(deformation, 0.0F);
        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    /**
     * 渲染模型
     */
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        // 渲染实体
        super.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /**
     * 设置模型的姿态
     */
    @Override
    public void setupAnim(WeaponMorphEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // 根据实体状态设置动画
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}