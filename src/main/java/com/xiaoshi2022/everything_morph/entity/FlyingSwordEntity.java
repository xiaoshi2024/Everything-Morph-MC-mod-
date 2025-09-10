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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import com.xiaoshi2022.everything_morph.client.FlySwordKeyBindings;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class FlyingSwordEntity extends PathfinderMob {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final String NBT_KEY_RENDER_ITEM = "RenderItem";
    private static final String NBT_KEY_OWNER_UUID = "OwnerUUID";

    private static final EntityDataAccessor<ItemStack> SWORD_ITEM_STACK =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> CONTROL_STATE =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UNIQUE_ID =
            SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    private ItemStack renderItemStack;

    public FlyingSwordEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SWORD_ITEM_STACK, ItemStack.EMPTY);
        this.entityData.define(CONTROL_STATE, (byte) 0);
        this.entityData.define(OWNER_UNIQUE_ID, Optional.empty());
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

            // 尝试从服务器获取玩家
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
    public boolean shouldRiderSit() {
        return false;
    }

    @Nullable
    public LivingEntity getControllingPassenger() {
        return this.getPassengers().isEmpty() ? null : (LivingEntity) this.getPassengers().get(0);
    }

    @Nullable
    public Player getControllingPlayer() {
        Entity entity = getControllingPassenger();
        if (entity instanceof Player) {
            return (Player) entity;
        }
        return null;
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);

        // 更新乘客的位置和旋转
        if (player instanceof LivingEntity) {
            LivingEntity entityliving = (LivingEntity) player;
            this.yBodyRot = entityliving.yBodyRot;
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public void tick() {
        Player player = getControllingPlayer();
        if (player != null && player.isCrouching()) {
            putAwaySword(player);
            return;
        }

        super.tick();

        // 客户端处理控制
        if (level().isClientSide) {
            this.updateClientControls();
        }
    }

    @OnlyIn(Dist.CLIENT)
    protected void updateClientControls() {
        if (this.isControlledByLocalInstance()) {
            // 使用空格键上升
            this.up(net.minecraft.client.Minecraft.getInstance().options.keyJump.isDown());
            // 使用自定义键位下降
            this.down(FlySwordKeyBindings.flySwordDown.isDown());
        }
    }

    private boolean up() {
        return (this.entityData.get(CONTROL_STATE) & 1) == 1;
    }

    private boolean down() {
        return (this.entityData.get(CONTROL_STATE) >> 1 & 1) == 1;
    }

    private void up(boolean up) {
        setStateField(0, up);
    }

    private void down(boolean down) {
        setStateField(1, down);
    }

    private void setStateField(int i, boolean newState) {
        byte prevState = this.entityData.get(CONTROL_STATE);
        if (newState) {
            this.entityData.set(CONTROL_STATE, (byte) (prevState | (1 << i)));
        } else {
            this.entityData.set(CONTROL_STATE, (byte) (prevState & ~(1 << i)));
        }
    }

    private void putAwaySword(Player player) {
        this.discard();
        if (!level().isClientSide) {
            ItemStack sword = getItemStack();
            if (!player.getInventory().add(sword)) {
                this.spawnAtLocation(sword, 0.0F);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Player player = getControllingPlayer();
        if (player != null) {
            return false;
        }

        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof Player && isOwner(sourceEntity)) {
            putAwaySword((Player) sourceEntity);
        }
        return false;
    }

    @Override
    public void travel(Vec3 travelVector) {
        Player player;
        if (this.isVehicle() && (player = getControllingPlayer()) != null) {
            // 处理上下移动
            if (down()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
            }

            if (up()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.03D, 0.0D));
            }

            // 同步玩家的旋转角度
            this.setYRot(player.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(player.getXRot() * 0.5F);
            this.xRotO = this.getXRot();
            this.setRot(this.getYRot(), this.getXRot());
            this.yHeadRot = this.getYRot();
            this.yHeadRotO = this.yHeadRot;

            // 从玩家获取移动输入
            float strafe = player.xxa * 0.5F;
            float forward = player.zza;

            if (forward <= 0.0F) {
                forward *= 0.25F;
            }

            this.jumping = false;
            this.setSpeed((float) this.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.0F);

            // 应用移动
            Vec3 movement = new Vec3(strafe, travelVector.y, forward);
            super.travel(movement);

            // 粒子效果
            if (this.level().isClientSide) {
                for (int i = 0; i < 2; ++i) {
                    this.level().addParticle(
                            ParticleTypes.WHITE_ASH,
                            this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(),
                            this.getY() + 0.1D,
                            this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(),
                            0.0D, 0.0D, 0.0D
                    );
                }
            }
        } else {
            this.setDeltaMovement(Vec3.ZERO);
            super.travel(Vec3.ZERO);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}