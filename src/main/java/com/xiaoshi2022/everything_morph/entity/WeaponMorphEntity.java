package com.xiaoshi2022.everything_morph.entity;

import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.Network.SkinUpdatePacket;
import com.xiaoshi2022.everything_morph.client.ResourcePackSkinLoader;
import com.xiaoshi2022.everything_morph.util.RandomNameGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import java.util.Random;

public class WeaponMorphEntity extends PathfinderMob {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private final String weaponType;
    private Player owner;
    private ResourceLocation skinTexture;
    private SkinLoadState skinLoadState = SkinLoadState.NOT_LOADED;
    private static final Random RANDOM = new Random(); // ✅ 添加这行

    // 保存随机生成的名字
    private String generatedSkinName;
    private boolean nameGenerated = false;

    /**
     * 创建实体的属性构建器
     */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public WeaponMorphEntity(EntityType<? extends PathfinderMob> type, Level level, String weaponType) {
        super(type, level);
        this.weaponType = weaponType;

        // 不要在构造函数中立即加载皮肤，等待NBT数据加载完成
        if (level.isClientSide) {
            this.skinLoadState = SkinLoadState.NOT_LOADED;
        } else {
            this.skinLoadState = SkinLoadState.LOADED;
        }
    }

    /**
     * 保存实体数据到NBT
     */
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        // 保存生成的皮肤名字
        if (generatedSkinName != null) {
            compound.putString("SkinName", generatedSkinName);
        }
        compound.putBoolean("NameGenerated", nameGenerated);

//        LOGGER.debug("保存实体数据 - 皮肤名字: {}, 已生成: {}", generatedSkinName, nameGenerated);
    }

    /**
     * 从NBT加载实体数据
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        // 加载保存的皮肤名字
        if (compound.contains("SkinName")) {
            generatedSkinName = compound.getString("SkinName");
            nameGenerated = compound.getBoolean("NameGenerated");

//            LOGGER.debug("从NBT加载皮肤名字: {}, 已生成: {}", generatedSkinName, nameGenerated);

            // 如果已经有保存的名字，使用它来加载皮肤
            if (this.level().isClientSide && generatedSkinName != null) {
                this.skinLoadState = SkinLoadState.LOADING;
                loadSkinFromSavedName();
            }
        } else {
//            LOGGER.debug("没有保存的皮肤名字，需要生成新的");
            // 如果没有保存的名字，说明是第一次生成，需要创建随机名字
            if (this.level().isClientSide) {
                this.skinLoadState = SkinLoadState.LOADING;
                loadSkinFromRandomName();
            }
        }
    }

    /**
     * 使用保存的名字加载皮肤
     */
    private void loadSkinFromSavedName() {
        if (this.level().isClientSide && generatedSkinName != null) {
//            LOGGER.debug("使用保存的名字加载皮肤: {}", generatedSkinName);
            ResourceLocation skin = ResourcePackSkinLoader.getInstance().getSkinByName(generatedSkinName);

            if (skin != null) {
                this.skinTexture = skin;
                this.skinLoadState = SkinLoadState.LOADED;
//                LOGGER.debug("成功加载保存的皮肤: {}", skin);
            } else {
//                LOGGER.warn("保存的皮肤不存在: {}, 重新生成", generatedSkinName);
                // 如果保存的皮肤不存在，重新生成
                loadSkinFromRandomName();
            }
        }
    }

    public void loadSkinFromRandomName() {
        if (this.level().isClientSide) {
            LOGGER.debug("开始加载随机皮肤...");

            // 如果已经加载过但失败了，避免重复尝试
            if (skinLoadState == SkinLoadState.FAILED) {
                LOGGER.debug("实体 {} 皮肤加载已失败，不再重复尝试", this.getId());
                return;
            }

            // 只在第一次生成随机名字
            if (!nameGenerated) {
                // ✅ 使用新的随机皮肤名字生成器
                generatedSkinName = RandomNameGenerator.getInstance().generateRandomSkinName();
                nameGenerated = true;
                LOGGER.debug("为实体 {} 生成皮肤名字: {}", this.getId(), generatedSkinName);
            }

            ResourceLocation skin = ResourcePackSkinLoader.getInstance().getSkinByName(generatedSkinName);

            if (skin != null && !skin.toString().contains("steve.png")) {
                this.skinTexture = skin;
                this.skinLoadState = SkinLoadState.LOADED;
                LOGGER.info("✅ 实体 {} 成功加载皮肤: {}", this.getId(), skin);
            } else {
                LOGGER.warn("❌ 无法根据名字找到皮肤: {}, 使用随机皮肤", generatedSkinName);
                this.skinLoadState = SkinLoadState.FAILED;
            }
        } else {
            this.skinLoadState = SkinLoadState.LOADED;
        }
    }

    @Override
    public Component getDisplayName() {
        // 使用随机生成器创建显示名称
        if (this.hasCustomName()) {
            return super.getDisplayName();
        }
        return Component.literal(RandomNameGenerator.getInstance().generateRandomDisplayName());
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public ResourceLocation getSkinTexture() {
        return skinTexture != null ? skinTexture : new ResourceLocation("textures/entity/steve.png");
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        LOGGER.debug("实体添加到世界，ID: {}", this.getId());

        if (!this.level().isClientSide) {
            syncSkinToClient();
        }
    }

    private void syncSkinToClient() {
        if (this.skinTexture != null && !this.level().isClientSide) {
            LOGGER.debug("同步皮肤到客户端: {}", this.skinTexture);
            NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
                    new SkinUpdatePacket(this.getId(), this.skinTexture));
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 客户端每20tick检查一次皮肤状态
        if (this.level().isClientSide && this.tickCount % 20 == 0) {
            if (skinLoadState == SkinLoadState.NOT_LOADED) {
//                LOGGER.debug("实体 {} 皮肤未加载，开始加载", this.getId());
                loadSkinFromRandomName();
            } else if (skinLoadState == SkinLoadState.LOADING) {
//                LOGGER.debug("实体 {} 皮肤正在加载中...", this.getId());
            }
        }
    }

    public void setSkinTexture(ResourceLocation texture) {
        this.skinTexture = texture;
        if (texture != null && !texture.toString().equals("textures/entity/steve.png")) {
            this.skinLoadState = SkinLoadState.LOADED;
            if (!this.level().isClientSide) {
                syncSkinToClient();
            }
        }
    }

    public SkinLoadState getSkinLoadState() {
        return this.skinLoadState;
    }

    public String getGeneratedSkinName() {
        return generatedSkinName;
    }

    public boolean isNameGenerated() {
        return nameGenerated;
    }

    public enum SkinLoadState {
        NOT_LOADED, LOADING, LOADED, FAILED
    }

    /**
     * 调试方法：获取实体信息
     */
    public String getDebugInfo() {
        return String.format("ID: %d, SkinState: %s, Skin: %s, GeneratedName: %s, NameGenerated: %b",
                this.getId(), skinLoadState, skinTexture, generatedSkinName, nameGenerated);
    }
}