package com.xiaoshi2022.everything_morph.Renderer;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * 化形实体渲染器 - 处理皮肤渲染
 */
public class WeaponMorphRenderer extends HumanoidMobRenderer<WeaponMorphEntity, WeaponMorphModel> {
    private static final ResourceLocation DEFAULT_SKIN =
            new ResourceLocation("textures/entity/steve.png");

    public WeaponMorphRenderer(EntityRendererProvider.Context context) {
        super(context, new WeaponMorphModel(context.bakeLayer(WeaponMorphModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new WeaponMorphModel(context.bakeLayer(WeaponMorphModel.INNER_ARMOR_LOCATION)),
                new WeaponMorphModel(context.bakeLayer(WeaponMorphModel.OUTER_ARMOR_LOCATION)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(WeaponMorphEntity entity) {
        ResourceLocation skin = entity.getSkinTexture();

        // 如果皮肤状态不是已加载，使用默认皮肤
        if (entity.getSkinLoadState() != WeaponMorphEntity.SkinLoadState.LOADED) {
            return DEFAULT_SKIN;
        }

        // 验证纹理是否有效
        if (skin != null && !skin.equals(DEFAULT_SKIN)) {
            return skin;
        }

        return DEFAULT_SKIN;
    }
}