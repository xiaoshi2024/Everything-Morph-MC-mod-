package com.xiaoshi2022.everything_morph.entity;

import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.Network.SkinUpdatePacket;
import com.xiaoshi2022.everything_morph.client.ResourcePackSkinLoader;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerGoalSimple;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerHurtByTargetGoal;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerHurtTargetGoal;
import com.xiaoshi2022.everything_morph.entity.Goal.PlaceBlockGoal;
import com.xiaoshi2022.everything_morph.util.RandomNameGenerator;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.xiaoshi2022.everything_morph.EverythingMorphMod.WEAPON_MORPH_ENTITY;

public class WeaponMorphEntity extends PathfinderMob {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private final String weaponType;
    private Player owner;
    private ResourceLocation skinTexture;
    public SkinLoadState skinLoadState = SkinLoadState.NOT_LOADED;
    private static final Random RANDOM = new Random();

    private ItemStack originalItem = ItemStack.EMPTY;

    private ResourceLocation cachedSkin = null;
    private int blockCount = 64;
    private BlockPos lastPlayerPlacementPos;
    private int cooldown = 0;

    private static final int MAX_SKIN_LOAD_RETRIES = 3;
    private int skinLoadRetryCount = 0;

    // 皮肤名称相关字段 - 修复核心问题
    private String generatedSkinName;
    private boolean nameGenerated = false;
    private boolean skinNamePersisted = false;

    // 皮肤缓存
    private static final Map<String, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public WeaponMorphEntity(EntityType<? extends PathfinderMob> type, Level level, String weaponType) {
        super(type, level);
        this.weaponType = weaponType;
        this.blockCount = 64;
        this.originalItem = ItemStack.EMPTY;

        // 在创建时生成固定名称 - 修复核心问题
        if (level.isClientSide) {
            this.skinLoadState = SkinLoadState.NOT_LOADED;
            this.generatedSkinName = generateStableSkinName();
            this.nameGenerated = true;
            LOGGER.debug("实体创建时生成稳定皮肤名称: {}", generatedSkinName);
        } else {
            this.skinLoadState = SkinLoadState.LOADED;
        }
    }

    // 生成稳定的皮肤名称（基于UUID）
    private String generateStableSkinName() {
        UUID uuid = this.getUUID();
        String[] skinNames = {"skin1", "skin2", "skin3", "skin4", "skin5"};
        int index = Math.abs(uuid.hashCode()) % skinNames.length;
        return skinNames[index];
    }

    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int count) {
        this.blockCount = count;
        if (this.blockCount <= 0) {
            this.dieFromBlockExhaustion();
        }
    }

    public static WeaponMorphEntity create(Level level, String weaponType, ItemStack originalItem) {
        WeaponMorphEntity entity = new WeaponMorphEntity(WEAPON_MORPH_ENTITY.get(), level, weaponType);
        entity.setOriginalItem(originalItem);
        entity.applyItemStats(originalItem);
        return entity;
    }

    public void recordPlayerPlacement(BlockPos pos) {
        this.lastPlayerPlacementPos = pos;
        this.cooldown = 0;
        LOGGER.debug("实体 {} 记录玩家放置位置: {}", this.getId(), pos);
    }

    private boolean trySmartBlockPlacement(BlockItem blockItem) {
        if (owner == null || lastPlayerPlacementPos == null || blockCount <= 0) {
            return false;
        }

        BlockPos targetPos = analyzePlayerPlacementPattern();
        if (targetPos == null) {
            targetPos = findSymmetricPlacementPosition();
        }

        if (targetPos == null || !canPlaceBlockAt(targetPos)) {
            return false;
        }

        Direction placementDirection = determineBestPlacementDirection(targetPos);

        try {
            BlockPlaceContext context = new BlockPlaceContext(
                    new UseOnContext(level(), null, InteractionHand.MAIN_HAND,
                            originalItem, new BlockHitResult(
                            Vec3.atCenterOf(targetPos), placementDirection, targetPos, false))
            );

            var result = blockItem.place(context);
            if (result.consumesAction()) {
                setBlockCount(blockCount - 1);
                LOGGER.debug("智能放置成功，位置: {}, 方向: {}, 剩余方块: {}",
                        targetPos, placementDirection, blockCount);

                if (level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                            5, 0.2, 0.2, 0.2, 0.1);
                }

                cooldown = 20;
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("智能放置失败", e);
        }

        return false;
    }

    private BlockPos analyzePlayerPlacementPattern() {
        if (owner == null || lastPlayerPlacementPos == null) {
            return null;
        }

        BlockPos playerPos = owner.blockPosition();
        BlockPos lastPlacement = lastPlayerPlacementPos;
        Vec3 toLastPlacement = Vec3.atCenterOf(lastPlacement).subtract(Vec3.atCenterOf(playerPos));
        Direction primaryDirection = getPrimaryDirection(toLastPlacement);
        return predictNextPosition(lastPlacement, primaryDirection);
    }

    private Direction getPrimaryDirection(Vec3 displacement) {
        double absX = Math.abs(displacement.x());
        double absY = Math.abs(displacement.y());
        double absZ = Math.abs(displacement.z());

        if (absX > absY && absX > absZ) {
            return displacement.x() > 0 ? Direction.EAST : Direction.WEST;
        } else if (absZ > absX && absZ > absY) {
            return displacement.z() > 0 ? Direction.SOUTH : Direction.NORTH;
        } else {
            return displacement.y() > 0 ? Direction.UP : Direction.DOWN;
        }
    }

    private BlockPos predictNextPosition(BlockPos lastPos, Direction direction) {
        if (owner != null) {
            Direction playerFacing = owner.getDirection();
            if (playerFacing.getAxis() == direction.getAxis()) {
                return lastPos.relative(direction);
            }
            return lastPos.relative(playerFacing);
        }
        return lastPos.relative(direction);
    }

    private Direction determineBestPlacementDirection(BlockPos targetPos) {
        if (owner != null) {
            return owner.getDirection();
        }
        return Direction.UP;
    }

    private void dieFromBlockExhaustion() {
        if (!this.level().isClientSide) {
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        getX(), getY() + 0.5, getZ(),
                        20, 0.3, 0.3, 0.3, 0.1);
            }
            this.discard();
        }
    }

    private void applyItemStats(ItemStack item) {
        if (item.isEmpty()) return;

        if (item.getItem() instanceof SwordItem sword) {
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(sword.getDamage() + 1.0D);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D + sword.getDamage() * 2);
        } else if (item.getItem() instanceof DiggerItem tool) {
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(tool.getAttackDamage() + 1.0D);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(15.0D + tool.getAttackDamage() * 3);
        } else if (item.getItem() instanceof BlockItem) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D);
            getAttribute(Attributes.ARMOR).setBaseValue(5.0D);
        }

        setHealth((float) getAttributeValue(Attributes.MAX_HEALTH));
    }

    public void setOriginalItem(ItemStack item) {
        this.originalItem = item.copy();
        if (item.getItem() instanceof BlockItem) {
            this.blockCount = item.getCount();
        }
        applyItemStats(item);
    }

    public ItemStack getOriginalItem() {
        return this.originalItem;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(this, new ClientboundTeleportEntityPacket(this));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("BlockCount", blockCount);
        compound.put("OriginalItem", originalItem.save(new CompoundTag()));

        // 保存皮肤名称和状态 - 修复核心问题
        if (generatedSkinName != null) {
            compound.putString("SkinName", generatedSkinName);
            compound.putBoolean("NameGenerated", nameGenerated);
            compound.putBoolean("SkinNamePersisted", skinNamePersisted);
            LOGGER.debug("保存皮肤名称: {}", generatedSkinName);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        if (compound.contains("BlockCount")) {
            blockCount = compound.getInt("BlockCount");
        }

        if (compound.contains("OriginalItem")) {
            this.originalItem = ItemStack.of(compound.getCompound("OriginalItem"));
            applyItemStats(this.originalItem);
        }

        // 修复：正确处理皮肤名称的加载
        if (compound.contains("SkinName") && compound.contains("NameGenerated")) {
            generatedSkinName = compound.getString("SkinName");
            nameGenerated = compound.getBoolean("NameGenerated");
            skinNamePersisted = compound.getBoolean("SkinNamePersisted");
            LOGGER.debug("从NBT加载皮肤名称: {}", generatedSkinName);

            if (this.level().isClientSide && skinLoadState == SkinLoadState.NOT_LOADED) {
                loadSkinFromSavedName();
            }
        } else if (!nameGenerated) {
            // 首次生成稳定名称
            generatedSkinName = generateStableSkinName();
            nameGenerated = true;
            skinNamePersisted = false;
            LOGGER.debug("首次生成稳定皮肤名称: {}", generatedSkinName);
        }
    }

    public void loadSkinFromSavedName() {
        if (this.level().isClientSide && generatedSkinName != null) {
            LOGGER.debug("尝试从保存的名字加载皮肤: {}", generatedSkinName);

            // 检查全局缓存
            ResourceLocation cached = SKIN_CACHE.get(generatedSkinName);
            if (cached != null) {
                skinTexture = cached;
                cachedSkin = cached;
                skinLoadState = SkinLoadState.LOADED;
                LOGGER.debug("从全局缓存加载皮肤: {}", cached);
                return;
            }

            ResourceLocation skin = ResourcePackSkinLoader.getInstance().getSkinByName(generatedSkinName);
            if (skin != null) {
                skinTexture = skin;
                cachedSkin = skin;
                skinLoadState = SkinLoadState.LOADED;
                SKIN_CACHE.put(generatedSkinName, skin); // 添加到全局缓存
                LOGGER.info("✅ 成功加载并缓存皮肤: {}", skin);
            } else {
                LOGGER.warn("保存的皮肤不存在: {}, 使用默认皮肤", generatedSkinName);
                skinTexture = DefaultPlayerSkin.getDefaultSkin();
                skinLoadState = SkinLoadState.FAILED;
            }
        }
    }

    // 修改 loadSkinFromRandomName 方法 - 修复核心问题
    public void loadSkinFromRandomName() {
        if (this.level().isClientSide && skinLoadState != SkinLoadState.LOADED && skinLoadState != SkinLoadState.FAILED) {
            LOGGER.debug("开始加载皮肤...");

            // 检查重试次数
            if (skinLoadRetryCount >= MAX_SKIN_LOAD_RETRIES) {
                LOGGER.warn("皮肤加载重试次数超过限制，使用默认皮肤");
                skinTexture = DefaultPlayerSkin.getDefaultSkin();
                skinLoadState = SkinLoadState.FAILED;
                return;
            }

            skinLoadState = SkinLoadState.LOADING;
            skinLoadRetryCount++;

            // 确保名称只生成一次
            if (!nameGenerated) {
                generatedSkinName = generateStableSkinName();
                nameGenerated = true;
                skinNamePersisted = false;
                LOGGER.debug("为实体 {} 设置固定皮肤名称: {}", this.getId(), generatedSkinName);
            }

            // 使用缓存或加载皮肤
            ResourceLocation skin = getCachedSkin(generatedSkinName);
            if (skin != null && !skin.toString().contains("steve")) {
                cachedSkin = skin;
                skinTexture = skin;
                skinLoadState = SkinLoadState.LOADED;
                LOGGER.info("✅ 实体 {} 皮肤加载完成: {}", this.getId(), skin);
            } else {
                LOGGER.warn("皮肤加载失败，将在下次tick重试");
                skinLoadState = SkinLoadState.NOT_LOADED;
            }
        }
    }

    // 添加皮肤缓存方法
    private ResourceLocation getCachedSkin(String skinName) {
        // 检查全局缓存
        ResourceLocation globalCached = SKIN_CACHE.get(skinName);
        if (globalCached != null) {
            return globalCached;
        }

        // 检查实例缓存
        if (cachedSkin != null && skinName.equals(generatedSkinName)) {
            return cachedSkin;
        }

        // 从加载器获取并缓存
        ResourceLocation skin = ResourcePackSkinLoader.getInstance().getSkinByName(skinName);
        if (skin != null) {
            SKIN_CACHE.put(skinName, skin);
        }
        return skin;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new FollowOwnerGoalSimple(this, 1.2D, 2.0F, 6.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new PlaceBlockGoal(this));

        this.targetSelector.addGoal(1, new FollowOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new FollowOwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
                e -> e != null && e != owner && !(e instanceof WeaponMorphEntity)));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;

        float dmg = (float) EnchantmentHelper.getDamageBonus(originalItem, living.getMobType());
        if (dmg <= 0) dmg = 3.0F;

        boolean success = living.hurt(damageSources().mobAttack(this), dmg);

        if (success && !originalItem.isEmpty() && originalItem.isDamageableItem()) {
            int newDamage = originalItem.getDamageValue() + 1;
            originalItem.setDamageValue(newDamage);

            if (originalItem.getDamageValue() >= originalItem.getMaxDamage()) {
                originalItem.shrink(1);
            }

            int fireLevel = EnchantmentHelper.getFireAspect(this);
            if (fireLevel > 0) {
                living.setSecondsOnFire(fireLevel * 4);
            }

            int knockbackLevel = EnchantmentHelper.getKnockbackBonus(this);
            if (knockbackLevel > 0) {
                living.knockback(knockbackLevel * 0.5F, this.getX() - living.getX(), this.getZ() - living.getZ());
            }
        }
        return success;
    }

    @Override
    public Component getDisplayName() {
        if (this.hasCustomName()) {
            return super.getDisplayName();
        }
        // 使用稳定的显示名称（基于实体ID）
        return Component.literal("Morph_" + this.getId());
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public ResourceLocation getSkinTexture() {
        if (skinTexture != null && skinLoadState == SkinLoadState.LOADED) {
            return skinTexture;
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        LOGGER.debug("实体添加到世界，ID: {}", this.getId());

        if (!this.level().isClientSide) {
            // 服务器端：生成稳定名称
            if (!nameGenerated) {
                generatedSkinName = generateStableSkinName();
                nameGenerated = true;
                skinNamePersisted = false;
                LOGGER.debug("服务器生成皮肤名字: {}", generatedSkinName);
            }
        } else {
            // 客户端：开始加载皮肤（减少频率）
            if (skinLoadState == SkinLoadState.NOT_LOADED && this.tickCount % 20 == 0) {
                loadSkinFromRandomName();
            }
        }
    }

    private void syncSkinToClient() {
        if (this.skinTexture != null && !this.level().isClientSide) {
            NetworkHandler.sendToAllTrackingWithRetry(this,
                    new SkinUpdatePacket(this.getId(), this.skinTexture));
        }
    }

    @Override
    public void tick() {
        super.tick();

        // 冷却时间计数
        if (cooldown > 0) {
            cooldown--;
        }

        // 智能方块放置
        if (!level().isClientSide && cooldown == 0 && lastPlayerPlacementPos != null) {
            if (originalItem.getItem() instanceof BlockItem blockItem) {
                if (trySmartBlockPlacement(blockItem)) {
                    this.lastPlayerPlacementPos = null;
                }
            }
        }

        // 客户端皮肤加载逻辑（减少检查频率）- 修复核心问题
        if (this.level().isClientSide) {
            if (this.tickCount % 40 == 0) { // 每2秒检查一次
                if (skinLoadState == SkinLoadState.NOT_LOADED) {
                    loadSkinFromRandomName();
                } else if (skinLoadState == SkinLoadState.LOADED && skinTexture != null && !skinNamePersisted) {
                    // 标记名称已持久化，避免重复处理
                    skinNamePersisted = true;
                }
            }
        }
    }

    private BlockPos findSymmetricPlacementPosition() {
        if (owner == null || lastPlayerPlacementPos == null) {
            return null;
        }

        BlockPos playerPos = owner.blockPosition();
        BlockPos relativePlacement = lastPlayerPlacementPos.subtract(playerPos);
        return this.blockPosition().offset(relativePlacement.getX(),
                relativePlacement.getY(),
                relativePlacement.getZ());
    }

    private boolean canPlaceBlockAt(BlockPos pos) {
        return level().getBlockState(pos).canBeReplaced() &&
                level().isEmptyBlock(pos.above());
    }

    private void tryAttackNearby() {
        AABB area = new AABB(this.blockPosition()).inflate(2.0D);
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != this && e != owner && e.isAlive());

        if (!targets.isEmpty()) {
            LivingEntity target = targets.get(0);
            float damage = (float) EnchantmentHelper.getDamageBonus(originalItem, target.getMobType());
            target.hurt(damageSources().mobAttack(this), damage);
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

    private void tryPlaceBlock(BlockItem blockItem) {
        BlockPos targetPos = this.blockPosition().relative(this.getDirection());
        Level level = this.level();

        if (level.getBlockState(targetPos).isAir()) {
            blockItem.place(new DirectionalPlaceContext(level, targetPos, Direction.DOWN, originalItem, Direction.UP));
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

    public LivingEntity getOwner() {
        return this.owner;
    }

    public BlockPos getLastPlayerPlacementPos() {
        return lastPlayerPlacementPos;
    }

    public boolean hasBlocks() {
        return blockCount > 0;
    }

    public void setGeneratedSkinName(String name) {
        this.generatedSkinName = name;
        this.nameGenerated = true;
        this.skinNamePersisted = true;
    }

    public enum SkinLoadState {
        NOT_LOADED, LOADING, LOADED, FAILED
    }

    /**
     * 调试方法：获取实体信息
     */
    public String getDebugInfo() {
        return String.format("ID: %d, SkinState: %s, Skin: %s, GeneratedName: %s, NameGenerated: %b, Persisted: %b",
                this.getId(), skinLoadState, skinTexture, generatedSkinName, nameGenerated, skinNamePersisted);
    }
}