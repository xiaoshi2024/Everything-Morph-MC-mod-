package com.xiaoshi2022.everything_morph.entity;

import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.Network.SkinUpdatePacket;
import com.xiaoshi2022.everything_morph.client.CSLIntegration;
import com.xiaoshi2022.everything_morph.client.DefaultSkinProvider;
import com.xiaoshi2022.everything_morph.client.SkinLoaderProxy;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerGoalSimple;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerHurtByTargetGoal;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerHurtTargetGoal;
import com.xiaoshi2022.everything_morph.entity.Goal.PlaceBlockGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.xiaoshi2022.everything_morph.EverythingMorphMod.WEAPON_MORPH_ENTITY;

public class WeaponMorphEntity extends PathfinderMob {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    // 实体数据同步器 - 用于同步 customName 到客户端
    private static final EntityDataAccessor<String> DATA_CUSTOM_NAME = 
            SynchedEntityData.defineId(WeaponMorphEntity.class, EntityDataSerializers.STRING);
    
    // 实体数据同步器 - 用于同步物品ID到客户端（用于渲染手持物品）
    private static final EntityDataAccessor<String> DATA_ITEM_ID = 
            SynchedEntityData.defineId(WeaponMorphEntity.class, EntityDataSerializers.STRING);

    private Player owner;
    private ResourceLocation skinTexture;
    public SkinLoadState skinLoadState = SkinLoadState.NOT_LOADED;
    private ItemStack originalItem = ItemStack.EMPTY;
    private int blockCount = 64;
    private BlockPos lastPlayerPlacementPos;
    private int cooldown = 0;

    // 添加一个调试标志
    private boolean debugNamePrinted = false;

    // 核心字段：从物品重命名获取的名称
    private String customName;
    private UUID skinUUID;
    public boolean skinLoadedFromUUID = false;

    // 可选：指令设置的皮肤名
    public String customSkinName = null;

    // 皮肤缓存 - 只在客户端使用
    private static final Map<String, ResourceLocation> SKIN_CACHE = FMLEnvironment.dist == Dist.CLIENT ?
            new ConcurrentHashMap<>() : null;

    // 添加这个无参构造函数
    public WeaponMorphEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // 不在这里设置 customName，等待从 NBT 读取
        LOGGER.info("无参构造函数被调用 (客户端同步)");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // 构造函数
    public WeaponMorphEntity(EntityType<? extends PathfinderMob> type, Level level, String customName) {
        super(type, level);
        this.customName = customName != null && !customName.isEmpty() ? customName : "Unnamed";
        LOGGER.info("构造函数设置 customName = '{}'", this.customName); // 添加日志
        this.skinUUID = UUID.nameUUIDFromBytes(this.customName.getBytes(StandardCharsets.UTF_8));
        // 同步到 entityData（只在服务端）
        if (!level.isClientSide) {
            this.entityData.set(DATA_CUSTOM_NAME, this.customName);
        }
    }

    public ResourceLocation getSkinTexture() {
        if (skinLoadState == SkinLoadState.LOADED && skinTexture != null) {
            return skinTexture;
        }
        // 如果正在加载或失败，返回 null，让渲染器处理
        return null;
    }

    // 工厂方法 - 从物品获取名称
    public static WeaponMorphEntity create(Level level, ItemStack originalItem, Player owner) {
        // 从物品的自定义名称获取玩家ID
        String customName = getItemCustomName(originalItem);

        // ✅ 添加日志
        System.out.println("创建实体，customName = " + customName);

        WeaponMorphEntity entity = new WeaponMorphEntity(WEAPON_MORPH_ENTITY.get(), level, customName);
        entity.setOriginalItem(originalItem);
        entity.setOwner(owner);
        entity.applyItemStats(originalItem);

        // 设置实体的显示名称
        entity.setCustomName(Component.literal(customName));

        LOGGER.info("创建化形实体: 名称={}, 来自物品={}, 物品ID={}",
                customName, originalItem.getDisplayName().getString(), getRegistryName(originalItem.getItem()));

        return entity;
    }

    private static String getItemCustomName(ItemStack item) {
        if (item == null) return "default";

        if (item.hasCustomHoverName()) {
            String name = item.getHoverName().getString();
            System.out.println("物品自定义名称: " + name);
            return name;  // 直接返回原始名称，不做任何处理
        }

        String registryName = getRegistryName(item.getItem());
        System.out.println("物品注册名: " + registryName);
        return registryName != null ? registryName : "default";
    }

    // 获取物品注册名
    public static String getRegistryName(net.minecraft.world.item.Item item) {
        if (item == null) {
            LOGGER.warn("getRegistryName 收到 null Item");
            return "default";
        }
        try {
            net.minecraft.resources.ResourceLocation registryName =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if (registryName != null) {
                String path = registryName.getPath();
                return path != null ? path : "default";
            }
        } catch (Exception e) {
            LOGGER.error("获取物品注册名称失败: {}", e.getMessage());
        }
        return "default";
    }

    // 设置自定义皮肤名（通过指令）
    public void setCustomSkinName(String skinName) {
        // 只在客户端检查皮肤存在性
        if (this.level().isClientSide) {
            if (!SkinLoaderProxy.hasSkin(skinName)) {
                LOGGER.warn("❌ 皮肤不存在: {}", skinName);
                return;
            }
        }

        this.customSkinName = skinName;
        this.skinLoadedFromUUID = false;
        this.skinLoadState = SkinLoadState.NOT_LOADED;
        LOGGER.info("设置自定义皮肤名: {}", skinName);

        if (this.level().isClientSide) {
            this.loadSkinFromResourcePack();
        }
    }

    // 强制重新加载皮肤
    public void reloadSkin() {
        this.skinLoadedFromUUID = false;
        this.skinLoadState = SkinLoadState.NOT_LOADED;
        LOGGER.info("强制重新加载皮肤");

        if (this.level().isClientSide) {
            this.loadSkinFromResourcePack();
        }
    }

    public String determineSkinName() {
        // 优先使用指令设置的皮肤名
        if (customSkinName != null && !customSkinName.isEmpty()) {
            LOGGER.info("使用自定义皮肤名: {}", customSkinName);
            return customSkinName;
        }

        // 直接从 customName 字段获取（这是最可靠的，因为它在构造时就已经设置）
        if (this.customName != null && !this.customName.isEmpty() && !"default".equals(this.customName) && !"Unnamed".equals(this.customName)) {
            LOGGER.info("使用 customName 字段: {}", this.customName);
            return this.customName;
        }

        // 尝试从实体的显示名称中获取（即使不是自定义名称）
        Component displayNameComponent = super.getCustomName();
        if (displayNameComponent != null) {
            String displayName = displayNameComponent.getString();
            LOGGER.info("从实体显示名称获取: {}", displayName);
            if (displayName != null && !displayName.isEmpty() && !"default".equals(displayName) && !"Morph".equals(displayName)) {
                return displayName;
            }
        }

        // 如果 customName 为空但有自定义显示名称，尝试使用显示名称
        if (this.hasCustomName()) {
            String displayName = this.getCustomName().getString();
            LOGGER.info("customName 为空，但显示名称为: {}", displayName);
            if (displayName != null && !displayName.isEmpty() && !"default".equals(displayName) && !"Morph".equals(displayName)) {
                return displayName;
            }
        }

        // 如果 customName 是 "default" 或为空但有 originalItem，尝试从物品获取
        if (originalItem != null && !originalItem.isEmpty()) {
            if (originalItem.hasCustomHoverName()) {
                String itemName = originalItem.getHoverName().getString();
                LOGGER.info("从 originalItem 获取自定义名称: {}", itemName);
                if (itemName != null && !itemName.isEmpty()) {
                    return itemName;
                }
            } else {
                String registryName = getRegistryName(originalItem.getItem());
                LOGGER.info("从 originalItem 获取注册名: {}", registryName);
                if (registryName != null && !registryName.isEmpty() && !"default".equals(registryName)) {
                    return registryName;
                }
            }
        }

        // 直接从字段获取
        String name = this.customName;
        if (name == null || name.isEmpty()) {
            LOGGER.warn("customName 为空！使用 'default'");
            name = "default";
        }

        LOGGER.info("使用物品名作为皮肤名: '{}'", name);
        return name;
    }

    public void loadSkinFromResourcePack() {
        if (!this.level().isClientSide) return;
        if (skinLoadState == SkinLoadState.LOADING || skinLoadState == SkinLoadState.LOADED) return;

        // 使用 determineSkinName() 方法获取正确的皮肤名
        final String skinNameToUse = determineSkinName();

        LOGGER.info("🔍 开始为 '{}' 加载皮肤", skinNameToUse);

        // 检查缓存
        if (SKIN_CACHE != null && SKIN_CACHE.containsKey(skinNameToUse)) {
            skinTexture = SKIN_CACHE.get(skinNameToUse);
            skinLoadState = SkinLoadState.LOADED;
            LOGGER.info("✅ 从缓存加载皮肤: {}", skinTexture);
            syncSkinStateToServer();
            return;
        }

        skinLoadState = SkinLoadState.LOADING;
        syncSkinStateToServer();

        // 使用异步线程加载皮肤
        Thread skinLoadThread = new Thread(() -> {
            try {
                final ResourceLocation skinLocation;

                // 优先使用 CSL 获取皮肤
                if (CSLIntegration.isAvailable()) {
                    skinLocation = CSLIntegration.getSkin(skinNameToUse);
                } else {
                    skinLocation = null;
                }

                // 在主线程中更新皮肤状态
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (skinLocation != null) {
                        skinTexture = skinLocation;
                        skinLoadState = SkinLoadState.LOADED;
                        LOGGER.info("✅ 从 CustomSkinLoader 成功加载皮肤: {}", skinLocation);
                        if (SKIN_CACHE != null) {
                            SKIN_CACHE.put(skinNameToUse, skinLocation);
                        }
                    } else {
                        LOGGER.warn("❌ 未找到皮肤: {}, 将使用默认皮肤", skinNameToUse);
                        skinTexture = null;
                        skinLoadState = SkinLoadState.FAILED;
                    }
                    syncSkinStateToServer();
                });

            } catch (Exception e) {
                LOGGER.error("❌ 加载皮肤时发生异常: {}", e.getMessage());
                // 在主线程中更新错误状态
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    skinTexture = null;
                    skinLoadState = SkinLoadState.FAILED;
                    syncSkinStateToServer();
                });
            }
        });

        skinLoadThread.setDaemon(true);
        skinLoadThread.start();
    }

    // 辅助方法：检查是否为默认皮肤
    private boolean isDefaultSkinLocation(ResourceLocation location) {
        if (location == null) return true;
        String path = location.getPath();
        return path.contains("steve") || path.contains("alex") || path.contains("default");
    }

    // 辅助方法：检查是否为有效的玩家名（用于Mojang API）
    private boolean isValidPlayerName(String name) {
        return name != null &&
                name.matches("^[a-zA-Z0-9_]{3,16}$") &&
                !name.equals("default") &&
                !name.equals("steve") &&
                !name.equals("alex");
    }

    // 同步皮肤状态到服务端
    // 同步皮肤状态到服务端
    private void syncSkinStateToServer() {
        // 只在客户端执行
        if (this.level().isClientSide) {
            try {
                // ✅ 修复：直接传递 skinTexture，允许为 null
                NetworkHandler.INSTANCE.sendToServer(new SkinUpdatePacket(
                        this.getId(),
                        this.skinTexture,  // 可能为 null
                        this.skinLoadState.ordinal()));
            } catch (Exception e) {
                LOGGER.error("❌ 发送皮肤状态失败", e);
            }
        }
    }

    // 其他方法保持不变...
    public int getBlockCount() {
        return blockCount;
    }

    public void setBlockCount(int count) {
        this.blockCount = count;
        if (this.blockCount <= 0) {
            this.dieFromBlockExhaustion();
        }
    }

    public void recordPlayerPlacement(BlockPos pos) {
        this.lastPlayerPlacementPos = pos;
        this.cooldown = 0;
        LOGGER.debug("实体 {} 记录玩家放置位置: {}", this.getId(), pos);
    }

    private void applyItemStats(ItemStack item) {
        if (item.isEmpty()) return;

        if (item.getItem() instanceof SwordItem sword) {
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(sword.getDamage() + 1.0D);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D + sword.getDamage() * 2);
        } else if (item.getItem() instanceof DiggerItem tool) {
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(tool.getAttackDamage() + 1.0D);
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(15.0D + tool.getAttackDamage() * 3);
        } else if (item.getItem() instanceof BlockItem blockItem) {
            // 获取方块硬度
            float hardness = blockItem.getBlock().defaultBlockState().getDestroySpeed(null, null);
            // 根据硬度计算属性
            double health = 20.0D + hardness * 10.0D;
            double damage = 1.0D + hardness * 2.0D;
            double armor = hardness * 1.5D;
            
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
            getAttribute(Attributes.ARMOR).setBaseValue(armor);
        }

        setHealth((float) getAttributeValue(Attributes.MAX_HEALTH));
    }

    public void setOriginalItem(ItemStack item) {
        this.originalItem = item.copy();
        if (item.getItem() instanceof BlockItem) {
            this.blockCount = item.getCount();
        }
        applyItemStats(item);
        
        // 同步物品ID到客户端（用于渲染手持物品）
        if (!this.level().isClientSide && !item.isEmpty()) {
            String itemId = getRegistryName(item.getItem());
            this.entityData.set(DATA_ITEM_ID, itemId);
            System.out.println("同步物品ID到客户端: " + itemId);
        }
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
        // 保存我们的 customName 到 NBT（使用不同的 key 避免与父类冲突）
        if (customName != null && !customName.isEmpty()) {
            compound.putString("MorphCustomName", customName);
        }
        
        super.addAdditionalSaveData(compound);
        
        compound.putInt("BlockCount", blockCount);
        compound.put("OriginalItem", originalItem.save(new CompoundTag()));
        compound.putUUID("SkinUUID", skinUUID);
        compound.putBoolean("SkinLoaded", skinLoadedFromUUID);

        if (customSkinName != null) {
            compound.putString("CustomSkinName", customSkinName);
        }
        if (skinTexture != null) {
            compound.putString("SkinTexture", skinTexture.toString());
        }
        compound.putInt("SkinLoadState", skinLoadState.ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        // 先读取我们自己的 CustomName（在调用 super 之前）
        // 优先使用新的 key "MorphCustomName"，如果不存在则尝试旧的 "CustomName"
        String savedCustomName = null;
        if (compound.contains("MorphCustomName")) {
            savedCustomName = compound.getString("MorphCustomName");
            LOGGER.info("从 NBT 预读取 MorphCustomName: '{}'", savedCustomName);
        } else if (compound.contains("CustomName")) {
            // 向后兼容：读取旧的 key
            savedCustomName = compound.getString("CustomName");
            LOGGER.info("从 NBT 预读取旧版 CustomName: '{}'", savedCustomName);
        }

        super.readAdditionalSaveData(compound);

        LOGGER.info("readAdditionalSaveData 开始");
        LOGGER.info("当前 customName = '{}'", this.customName);

        // 使用我们保存的 CustomName（优先）
        if (savedCustomName != null && !savedCustomName.isEmpty()) {
            String oldName = this.customName;
            this.customName = savedCustomName;
            LOGGER.info("从 NBT 读取 CustomName: '{}' (之前是 '{}')", this.customName, oldName);
            // 同时设置实体的显示名称，确保同步
            super.setCustomName(Component.literal(this.customName));
            // 同步到 entityData（只在服务端）
            if (!this.level().isClientSide) {
                this.entityData.set(DATA_CUSTOM_NAME, this.customName);
            }
        } else {
            LOGGER.info("NBT 中没有 CustomName，保持当前值: '{}'", this.customName);
        }

        if (compound.contains("BlockCount")) {
            blockCount = compound.getInt("BlockCount");
        }
        if (compound.contains("OriginalItem")) {
            this.originalItem = ItemStack.of(compound.getCompound("OriginalItem"));
            applyItemStats(this.originalItem);
        }
        if (compound.contains("SkinUUID")) {
            skinUUID = compound.getUUID("SkinUUID");
            skinLoadedFromUUID = compound.getBoolean("SkinLoaded");
        } else {
            skinUUID = this.getUUID();
        }
        if (compound.contains("CustomSkinName")) {
            customSkinName = compound.getString("CustomSkinName");
        }

        if (this.level().isClientSide) {
            // 客户端：重置皮肤加载状态，但保留 customName
            this.skinLoadState = SkinLoadState.NOT_LOADED;
            this.skinLoadedFromUUID = false;
            // 确保 skinUUID 正确设置
            if (this.customName != null && !this.customName.isEmpty()) {
                this.skinUUID = UUID.nameUUIDFromBytes(this.customName.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            if (compound.contains("SkinTexture")) {
                String texturePath = compound.getString("SkinTexture");
                this.skinTexture = new ResourceLocation(texturePath);
            }
            if (compound.contains("SkinLoadState")) {
                this.skinLoadState = SkinLoadState.values()[compound.getInt("SkinLoadState")];
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CUSTOM_NAME, "");
        this.entityData.define(DATA_ITEM_ID, "");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new FollowOwnerGoalSimple(this, 1.2D, 2.0F, 6.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new PlaceBlockGoal(this));

        this.targetSelector.addGoal(1, new FollowOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new FollowOwnerHurtTargetGoal(this));
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
        return Component.literal(this.customName);
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public void setSkinTexture(ResourceLocation texture) {
        this.skinTexture = texture;
        if (texture != null && !texture.equals(DefaultSkinProvider.getDefaultSkin())) {
            this.skinLoadState = SkinLoadState.LOADED;
        }
    }

    public void setSkinLoadState(SkinLoadState state) {
        this.skinLoadState = state;
    }

    @Override
    public void tick() {
        super.tick();

        if (cooldown > 0) {
            cooldown--;
        }

        if (!level().isClientSide && cooldown == 0 && lastPlayerPlacementPos != null) {
            if (originalItem.getItem() instanceof BlockItem blockItem) {
                if (trySmartBlockPlacement(blockItem)) {
                    this.lastPlayerPlacementPos = null;
                }
            }
        }

        if (this.level().isClientSide) {
            // 从 entityData 同步 customName（用于客户端从服务端接收同步）
            String syncedName = this.entityData.get(DATA_CUSTOM_NAME);
            if (syncedName != null && !syncedName.isEmpty() && !syncedName.equals(this.customName)) {
                LOGGER.info("客户端从 entityData 同步 customName: '{}' -> '{}'", this.customName, syncedName);
                this.customName = syncedName;
                this.skinUUID = UUID.nameUUIDFromBytes(this.customName.getBytes(StandardCharsets.UTF_8));
            }
            
            if (skinLoadState == SkinLoadState.NOT_LOADED && !skinLoadedFromUUID) {
                loadSkinFromResourcePack();
            }
            
            // 调试：输出物品ID同步状态
            String itemId = this.entityData.get(DATA_ITEM_ID);
            if (itemId != null && !itemId.isEmpty()) {
                System.out.println("tick() - 客户端 itemId: '" + itemId + "'");
            }
        } else {
            // 服务端：确保物品ID已同步到 entityData
            String currentItemId = this.entityData.get(DATA_ITEM_ID);
            if (!originalItem.isEmpty()) {
                String expectedItemId = getRegistryName(originalItem.getItem());
                if (!expectedItemId.equals(currentItemId)) {
                    System.out.println("服务端同步物品ID到 entityData: '" + expectedItemId + "'");
                    this.entityData.set(DATA_ITEM_ID, expectedItemId);
                }
            }
        }
    }

    private boolean trySmartBlockPlacement(BlockItem blockItem) {
        if (owner == null || lastPlayerPlacementPos == null || blockCount <= 0) {
            return false;
        }

        BlockPos targetPos = findSymmetricPlacementPosition();
        if (targetPos == null || !canPlaceBlockAt(targetPos)) {
            return false;
        }

        Direction placementDirection = owner.getDirection();

        try {
            BlockPlaceContext context = new BlockPlaceContext(
                    new UseOnContext(level(), null, InteractionHand.MAIN_HAND,
                            originalItem, new BlockHitResult(
                            Vec3.atCenterOf(targetPos), placementDirection, targetPos, false))
            );

            var result = blockItem.place(context);
            if (result.consumesAction()) {
                setBlockCount(blockCount - 1);
                LOGGER.debug("放置成功，位置: {}", targetPos);

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

    public SkinLoadState getSkinLoadState() {
        return this.skinLoadState;
    }

    public LivingEntity getOwner() {
        return this.owner;
    }

    public BlockPos getLastPlayerPlacementPos() {
        return lastPlayerPlacementPos;
    }

    public UUID getSkinUUID() {
        return this.skinUUID;
    }

    public String getPlayerName() {
        return this.customName;
    }

    @Override
    public Component getCustomName() {
        // 确保不会返回 null
        if (customName != null && !customName.isEmpty()) {
            return Component.literal(customName);
        }
        return Component.literal("Morph");
    }

    @Override
    public ItemStack getMainHandItem() {
        // 如果在服务端，直接返回 originalItem
        if (!this.level().isClientSide) {
            return this.originalItem;
        }
        
        // 如果在客户端，从 entityData 获取物品ID并创建物品
        String itemId = this.entityData.get(DATA_ITEM_ID);
        System.out.println("客户端 getMainHandItem - itemId from entityData: '" + itemId + "'");
        if (itemId != null && !itemId.isEmpty() && !"default".equals(itemId)) {
            try {
                net.minecraft.resources.ResourceLocation location = new net.minecraft.resources.ResourceLocation("minecraft", itemId);
                System.out.println("客户端尝试获取物品: " + location);
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(location);
                System.out.println("获取到的物品: " + item);
                if (item != null) {
                    return new ItemStack(item);
                }
            } catch (Exception e) {
                System.out.println("获取物品时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        // 死亡时掉落原始物品
        if (!this.level().isClientSide && !originalItem.isEmpty()) {
            this.spawnAtLocation(originalItem.copy());
        }
    }

    public String getCustomNameString() {
        return this.customName;
    }

    public enum SkinLoadState {
        NOT_LOADED, LOADING, LOADED, FAILED
    }
}