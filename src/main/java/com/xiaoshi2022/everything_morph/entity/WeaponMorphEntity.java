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
import java.util.Random;

import static com.xiaoshi2022.everything_morph.EverythingMorphMod.WEAPON_MORPH_ENTITY;

public class WeaponMorphEntity extends PathfinderMob {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private final String weaponType;
    private Player owner;
    private ResourceLocation skinTexture;
    private SkinLoadState skinLoadState = SkinLoadState.NOT_LOADED;
    private static final Random RANDOM = new Random(); // ✅ 添加这行

    private ItemStack originalItem = ItemStack.EMPTY;

    // 添加这些字段
    private int blockCount = 64; // 初始方块数量
    private BlockPos lastPlayerPlacementPos;
    private int cooldown = 0;

    // 保存随机生成的名字
    private String generatedSkinName;
    private boolean nameGenerated = false;

    /**
     * 创建实体的属性构建器
     */
    // 修改 createAttributes 为更通用的基础值
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // 修改构造函数，移除 originalItem 参数
    public WeaponMorphEntity(EntityType<? extends PathfinderMob> type, Level level, String weaponType) {
        super(type, level);
        this.weaponType = weaponType;
        this.blockCount = 64; // 初始64个方块
        this.originalItem = ItemStack.EMPTY; // 初始化为空

        if (level.isClientSide) {
            this.skinLoadState = SkinLoadState.NOT_LOADED;
        } else {
            this.skinLoadState = SkinLoadState.LOADED;
        }
    }

    // 添加获取和设置方块数量的方法
    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int count) {
        this.blockCount = count;
        if (this.blockCount <= 0) {
            this.dieFromBlockExhaustion();
        }
    }

    // 在 WeaponMorphEntity 类中添加
    public static WeaponMorphEntity create(Level level, String weaponType, ItemStack originalItem) {
        WeaponMorphEntity entity = new WeaponMorphEntity(WEAPON_MORPH_ENTITY.get(), level, weaponType);
        entity.setOriginalItem(originalItem);
        entity.applyItemStats(originalItem); // 应用物品属性
        return entity;
    }

    // 记录玩家放置方块的位置
    public void recordPlayerPlacement(BlockPos pos) {
        this.lastPlayerPlacementPos = pos;
        this.cooldown = 0; // 重置冷却，立即尝试放置
        LOGGER.debug("实体 {} 记录玩家放置位置: {}", this.getId(), pos);
    }

    // 方块耗尽死亡
    private void dieFromBlockExhaustion() {
        if (!this.level().isClientSide) {
            // 播放死亡效果
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        getX(), getY() + 0.5, getZ(),
                        20, 0.3, 0.3, 0.3, 0.1);
            }
            this.discard();
        }
    }

    // 添加调试方法
    public String getDebugItemInfo() {
        return String.format("物品: %s, 数量: %d, 是方块物品: %b",
                originalItem.getItem().getDescription().getString(),
                originalItem.getCount(),
                originalItem.getItem() instanceof BlockItem);
    }

    public String getDebugOwnerInfo() {
        return String.format("主人: %s, 主人存在: %b",
                owner != null ? owner.getName().getString() : "null",
                owner != null);
    }

    private void applyItemStats(ItemStack item) {
        if (item.isEmpty()) return;

        // 根据物品类型设置不同的属性
        if (item.getItem() instanceof SwordItem sword) {
            // 继承剑的攻击力
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(sword.getDamage() + 1.0D);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D + sword.getDamage() * 2);
        } else if (item.getItem() instanceof DiggerItem tool) {
            // 继承工具的属性
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(tool.getAttackDamage() + 1.0D);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(15.0D + tool.getAttackDamage() * 3);
        } else if (item.getItem() instanceof BlockItem) {
            // 方块物品 - 防御型
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0D);
            getAttribute(Attributes.ARMOR).setBaseValue(5.0D);
        }

        // 设置当前生命值为最大值
        setHealth((float) getAttributeValue(Attributes.MAX_HEALTH));
    }


    public void setOriginalItem(ItemStack item) {
        this.originalItem = item.copy();

        // 如果是方块物品，设置初始方块数量
        if (item.getItem() instanceof BlockItem) {
            this.blockCount = item.getCount(); // 继承物品堆叠数量
        }

        // 应用物品属性
        applyItemStats(item);
    }

    public ItemStack getOriginalItem() {
        return this.originalItem;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        // 如果上次移动被阻止（撞墙），立即同步位置
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().broadcast(this, new ClientboundTeleportEntityPacket(this));
        }
    }

    /**
     * 保存实体数据到NBT
     */
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("BlockCount", blockCount);
        compound.put("OriginalItem", originalItem.save(new CompoundTag()));
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

        if (compound.contains("BlockCount")) {
            blockCount = compound.getInt("BlockCount");
        }

        if (compound.contains("OriginalItem")) {
            this.originalItem = ItemStack.of(compound.getCompound("OriginalItem"));
            // 重新应用物品属性
            applyItemStats(this.originalItem);
        }

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
    protected void registerGoals() {
        // ===== 行为 Goal（移动、攻击、放置） =====
        this.goalSelector.addGoal(2, new FollowOwnerGoalSimple(this, 1.2D, 2.0F, 6.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new PlaceBlockGoal(this)); // 放置方块

        // ===== 目标 Target（选谁打） =====
        this.targetSelector.addGoal(1, new FollowOwnerHurtByTargetGoal(this)); // 主人被谁打，我打谁
        this.targetSelector.addGoal(2, new FollowOwnerHurtTargetGoal(this));   // 主人打谁，我打谁
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
                e -> e != null && e != owner && !(e instanceof WeaponMorphEntity)));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity living)) return false;

        // 1. 计算伤害（含附魔）
        float dmg = (float) EnchantmentHelper.getDamageBonus(originalItem, living.getMobType());
        if (dmg <= 0) dmg = 3.0F;

        // 2. 应用伤害
        boolean success = living.hurt(damageSources().mobAttack(this), dmg);

        if (success && !originalItem.isEmpty() && originalItem.isDamageableItem()) {
            // 3. 手动减耐久（不调用 hurtAndBreak）
            int newDamage = originalItem.getDamageValue() + 1;
            originalItem.setDamageValue(newDamage);

            // 4. 如果耐久耗尽，销毁物品
            if (originalItem.getDamageValue() >= originalItem.getMaxDamage()) {
                originalItem.shrink(1); // 直接销毁
            }

            // 5. 火焰附加 - 修复：使用正确的方法获取火焰附加等级
            int fireLevel = EnchantmentHelper.getFireAspect(this); // 使用 this (LivingEntity)
            if (fireLevel > 0) {
                living.setSecondsOnFire(fireLevel * 4);
            }

            // 6. 击退 - 修复：使用正确的方法获取击退等级
            int knockbackLevel = EnchantmentHelper.getKnockbackBonus(this); // 使用 this (LivingEntity)
            if (knockbackLevel > 0) {
                living.knockback(knockbackLevel * 0.5F, this.getX() - living.getX(), this.getZ() - living.getZ());
            }
        }
        return success;
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

        // 冷却时间计数
        if (cooldown > 0) {
            cooldown--;
        }

        // 尝试跟随玩家放置方块 - 只有在有记录的位置时才执行
        if (!level().isClientSide && cooldown == 0 && lastPlayerPlacementPos != null) {
            tryFollowPlayerPlaceBlock();
        }
        
        if (!level().isClientSide && !originalItem.isEmpty()) {
            Item item = originalItem.getItem();

            if (item instanceof BlockItem blockItem) {
                tryPlaceBlock(blockItem);
            } else if (item instanceof SwordItem || item instanceof DiggerItem) {
                tryAttackNearby();
            }
        }

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

    // 尝试跟随玩家放置方块
    private void tryFollowPlayerPlaceBlock() {
        if (blockCount <= 0) {
            LOGGER.debug("实体 {} 方块数量为0，无法放置", this.getId());
            return;
        }

        if (!(originalItem.getItem() instanceof BlockItem blockItem)) {
            LOGGER.debug("实体 {} 持有的不是方块物品: {}", this.getId(), originalItem.getItem());
            return;
        }

        // 检查距离（32格内）
        double distanceSq = lastPlayerPlacementPos.distSqr(this.blockPosition());
        if (distanceSq > 32 * 32) {
            LOGGER.debug("实体 {} 距离玩家放置位置太远: {}格", this.getId(), Math.sqrt(distanceSq));
            return;
        }

        // 寻找合适的放置位置（玩家放置位置的对称位置）
        BlockPos placePos = findSymmetricPlacementPosition();
        if (placePos == null) {
            LOGGER.debug("实体 {} 无法找到对称放置位置", this.getId());
            return;
        }

        if (!canPlaceBlockAt(placePos)) {
            LOGGER.debug("实体 {} 位置 {} 无法放置方块", this.getId(), placePos);
            return;
        }

        try {
            // 放置方块 - 使用 BlockPlaceContext
            BlockPlaceContext context = new BlockPlaceContext(
                    new UseOnContext(level(), null, InteractionHand.MAIN_HAND,
                            originalItem, new BlockHitResult(
                            Vec3.atCenterOf(placePos), Direction.UP, placePos, false))
            );

            // 在 1.20.1 中，place 方法返回 InteractionResult
            var result = blockItem.place(context);
            if (result.consumesAction()) {
                // 消耗方块
                setBlockCount(blockCount - 1);
                LOGGER.info("实体 {} 成功放置方块，剩余方块: {}", this.getId(), blockCount);

                // 播放放置效果
                if (level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5,
                            5, 0.2, 0.2, 0.2, 0.1);
                }

                // 重置冷却
                cooldown = 20;

                // 清除记录的位置，避免重复放置
                this.lastPlayerPlacementPos = null;
            } else {
                LOGGER.debug("实体 {} 放置方块失败: {}", this.getId(), result);
            }
        } catch (Exception e) {
            LOGGER.error("实体 {} 放置方块时发生错误", this.getId(), e);
        }
    }

    // 寻找对称放置位置
    private BlockPos findSymmetricPlacementPosition() {
        if (owner == null || lastPlayerPlacementPos == null) {
            return null;
        }

        // 计算相对于玩家的对称位置
        BlockPos playerPos = owner.blockPosition();
        BlockPos relativePlacement = lastPlayerPlacementPos.subtract(playerPos);

        // 返回相对于NPC的对称位置
        return this.blockPosition().offset(relativePlacement.getX(),
                relativePlacement.getY(),
                relativePlacement.getZ());
    }

    // 检查是否可以在此位置放置方块
    private boolean canPlaceBlockAt(BlockPos pos) {
        return level().getBlockState(pos).canBeReplaced() &&
                level().isEmptyBlock(pos.above()); // 确保上方没有方块阻挡
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
        return this.owner; // 返回实际的 owner 字段
    }

    public BlockPos getLastPlayerPlacementPos() {
        return lastPlayerPlacementPos;
    }

    public boolean hasBlocks() {
        return blockCount > 0;
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