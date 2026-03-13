package com.xiaoshi2022.everything_morph.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class SmartFlyingSwordEntity extends PathfinderMob {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final String NBT_KEY_RENDER_ITEM = "RenderItem";
    private static final String NBT_KEY_OWNER_UUID = "OwnerUUID";

    private static final EntityDataAccessor<ItemStack> SWORD_ITEM_STACK =
            SynchedEntityData.defineId(SmartFlyingSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UNIQUE_ID =
            SynchedEntityData.defineId(SmartFlyingSwordEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private ItemStack renderItemStack;

    public SmartFlyingSwordEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SWORD_ITEM_STACK, ItemStack.EMPTY);
        this.entityData.define(OWNER_UNIQUE_ID, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(3, new SmartFlyingSwordWanderGoal(this));

        // 添加反击行为
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, true));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);
        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(true);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
    }

    public void setItemStack(ItemStack itemStack) {
        this.entityData.set(SWORD_ITEM_STACK, itemStack);
    }

    public ItemStack getItemStack() {
        return this.entityData.get(SWORD_ITEM_STACK);
    }

    public ItemStack getRenderItemStack() {
        if (renderItemStack == null) {
            renderItemStack = new ItemStack(getItemStack().getItem());
        }
        return renderItemStack;
    }

    public UUID getOwnerId() {
        return this.entityData.get(OWNER_UNIQUE_ID).orElse(null);
    }

    public void setOwnerId(@Nullable UUID uuid) {
        this.entityData.set(OWNER_UNIQUE_ID, Optional.ofNullable(uuid));
    }

    @Nullable
    public LivingEntity getOwner() {
        try {
            UUID uuid = this.getOwnerId();
            if (uuid == null) return null;

            if (this.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getPlayerByUUID(uuid);
            }
            return null;
        } catch (IllegalArgumentException var2) {
            return null;
        }
    }

    public boolean isOwner(Entity entityIn) {
        if (!(entityIn instanceof Player)) return false;

        UUID ownerId = this.getOwnerId();
        return ownerId != null && ownerId.equals(entityIn.getUUID());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        if (!this.getItemStack().isEmpty()) {
            compound.put(NBT_KEY_RENDER_ITEM, this.getItemStack().save(new CompoundTag()));
        }
        if (this.getOwnerId() != null) {
            compound.putUUID(NBT_KEY_OWNER_UUID, this.getOwnerId());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        if (compound.contains(NBT_KEY_RENDER_ITEM, 10)) {
            this.setItemStack(ItemStack.of(compound.getCompound(NBT_KEY_RENDER_ITEM)));
        }
        if (compound.contains(NBT_KEY_OWNER_UUID)) {
            this.setOwnerId(compound.getUUID(NBT_KEY_OWNER_UUID));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) {
            // 当被玩家攻击时，将该玩家设为目标
            this.setTarget((LivingEntity) source.getEntity());
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof LivingEntity livingTarget) {
            // 获取攻击伤害值（从属性中获取）
            float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);

            // 对目标造成伤害 - 修复：使用 level().damageSources()
            boolean hurt = target.hurt(this.damageSources().mobAttack(this), damage);

            if (hurt) {
                // 获取击退值
                float knockback = (float) this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);

                // 计算击退方向（基于攻击者和目标的位置）
                double dx = target.getX() - this.getX();
                double dz = target.getZ() - this.getZ();

                // 归一化并应用击退
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 0) {
                    dx = dx / distance * knockback;
                    dz = dz / distance * knockback;
                } else {
                    dx = 0;
                    dz = 0;
                }

                // 应用击退（Y轴向上，X/Z轴向后）
                target.push(dx, knockback * 0.3D, dz);

                // 播放攻击音效
                this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);

                // 添加攻击粒子效果
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            ParticleTypes.SWEEP_ATTACK,
                            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                            1, 0, 0, 0, 0
                    );
                }

                // 设置攻击后摇
                this.swing(InteractionHand.MAIN_HAND);

                // 更新最后攻击时间
                this.setLastHurtMob(target);
            }

            return hurt;
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            ItemStack sword = getItemStack();
            this.spawnAtLocation(sword, 0.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(
                        ParticleTypes.ENCHANT,
                        this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(),
                        this.getY() + 0.1D,
                        this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(),
                        0.0D, 0.0D, 0.0D
                );
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    static class SmartFlyingSwordWanderGoal extends Goal {
        private final SmartFlyingSwordEntity entity;
        private double x; private double y; private double z;
        private int executionChance = 10;

        public SmartFlyingSwordWanderGoal(SmartFlyingSwordEntity entity) {
            this.entity = entity;
            this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.entity.getTarget() != null) return false;
            if (this.entity.getRandom().nextInt(executionChance) != 0) return false;

            Vec3 vec3 = this.getRandomLocation();
            if (vec3 == null) return false;

            this.x = vec3.x;
            this.y = vec3.y;
            this.z = vec3.z;
            return true;
        }

        @Override
        public void start() {
            this.entity.getNavigation().moveTo(this.x, this.y, this.z, 1.0D);
        }

        @Override
        public boolean canContinueToUse() {
            return !this.entity.getNavigation().isDone();
        }

        @Nullable
        private Vec3 getRandomLocation() {
            double x = entity.getX() + (entity.getRandom().nextDouble() - 0.5D) * 20.0D;
            double y = entity.getY() + (entity.getRandom().nextDouble() - 0.5D) * 10.0D;
            double z = entity.getZ() + (entity.getRandom().nextDouble() - 0.5D) * 20.0D;
            return new Vec3(x, y, z);
        }
    }
}