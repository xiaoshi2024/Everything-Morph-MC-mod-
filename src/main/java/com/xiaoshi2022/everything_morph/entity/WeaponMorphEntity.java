package com.xiaoshi2022.everything_morph.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.Network.SkinUpdatePacket;
import com.xiaoshi2022.everything_morph.client.ResourcePackSkinLoader;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerGoalSimple;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerHurtByTargetGoal;
import com.xiaoshi2022.everything_morph.entity.Goal.FollowOwnerHurtTargetGoal;
import com.xiaoshi2022.everything_morph.entity.Goal.PlaceBlockGoal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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

    // 添加基于UUID的皮肤字段
    private UUID skinUUID;
    public boolean skinLoadedFromUUID = false;

    // 添加字段
    private boolean skinNeedsUpdate = false;

    // 添加皮肤模式字段
    private String skinPattern = "everything_morph:skins/{USERNAME}.png";

    // 添加玩家名字段
    private String playerName;

    // 添加一个新的字段来存储指令设置的皮肤名
    public String customSkinName = null;

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

    private GameProfile playerProfile;

    // 修改构造函数
    public WeaponMorphEntity(EntityType<? extends PathfinderMob> type, Level level, String weaponType, GameProfile playerProfile, String playerName) {
        super(type, level);
        this.weaponType = weaponType;
        this.playerProfile = playerProfile;
        this.playerName = playerName != null ? playerName : "default_player"; // 添加空值检查
        this.skinTexture = DefaultPlayerSkin.getDefaultSkin();

        // 使用玩家名生成UUID
        this.skinUUID = UUID.nameUUIDFromBytes(this.playerName.getBytes(StandardCharsets.UTF_8));
    }

    // 添加设置自定义皮肤名的方法
    // 在 setCustomSkinName 方法中添加检查
    public void setCustomSkinName(String skinName) {
        if (skinName == null || skinName.isEmpty()) {
            LOGGER.warn("❌ 皮肤名称为空");
            return;
        }
        
        // 特殊处理外部皮肤文件，尝试添加扩展名
        String skinNameToCheck = skinName;
        if (!skinName.endsWith(".png") && !skinName.endsWith(".svg")) {
            // 尝试直接使用原始名称检查
            if (!ResourcePackSkinLoader.getInstance().hasSkin(skinName)) {
                // 如果原始名称不存在，尝试添加.png扩展名
                skinNameToCheck = skinName + ".png";
                if (!ResourcePackSkinLoader.getInstance().hasSkin(skinNameToCheck)) {
                    // 如果.png也不存在，尝试添加.svg扩展名
                    skinNameToCheck = skinName + ".svg";
                    if (!ResourcePackSkinLoader.getInstance().hasSkin(skinNameToCheck)) {
                        LOGGER.warn("❌ 皮肤不存在: {}", skinName);
                        return;
                    }
                }
            }
        }

        this.customSkinName = skinName;
        this.skinLoadedFromUUID = false;
        this.skinLoadState = SkinLoadState.NOT_LOADED;
        this.skinNeedsUpdate = true; // 添加更新标志
        LOGGER.info("设置自定义皮肤名: {}, 实际检查: {}", skinName, skinNameToCheck);

        // 立即尝试重新加载皮肤
        if (this.level().isClientSide) {
            this.loadSkinFromResourcePack();
        } else {
            // ✅ 服务端：立即换皮并广播
            ResourceLocation skin = ResourcePackSkinLoader.getInstance().getSkinByName(skinName);
            if (skin != null && !skin.getPath().contains("steve")) {
                this.skinTexture = skin;
                this.skinLoadState = SkinLoadState.LOADED;
                this.skinLoadedFromUUID = true;
                LOGGER.info("✅ 服务端直接换皮: {}", skin);
                this.syncSkinToClient(); // 广播给所有跟踪玩家
            }
        }
    }

    // 新增方法：手动设置皮肤UUID
    public void setSkinUUID(UUID skinUUID) {
        this.skinUUID = skinUUID;
        this.skinLoadedFromUUID = false;
        this.skinLoadState = SkinLoadState.NOT_LOADED;
        LOGGER.info("手动设置皮肤UUID: {}", skinUUID);
    }

    // 新增方法：手动设置皮肤UUID和玩家名
    public void setSkinUUIDAndName(UUID skinUUID, String playerName) {
        this.skinUUID = skinUUID;
        this.playerName = playerName != null ? playerName : "Player";
        this.skinLoadedFromUUID = false;
        this.skinLoadState = SkinLoadState.NOT_LOADED;
        LOGGER.info("手动设置皮肤UUID: {}, 玩家名: {}", skinUUID, playerName);
    }

    // 新增方法：强制重新加载皮肤
    public void reloadSkin() {
        this.skinLoadedFromUUID = false;
        this.skinLoadState = SkinLoadState.NOT_LOADED;
        LOGGER.info("强制重新加载皮肤");
    }

    // 添加获取和设置皮肤模式的方法
    public String getSkinPattern() {
        return skinPattern;
    }

    // 在 setmorphskin 等相关指令中添加立即重载逻辑
    public void setSkinPattern(String pattern) {
        this.skinPattern = pattern;
        this.skinLoadedFromUUID = false;
        this.skinLoadState = SkinLoadState.NOT_LOADED;
        LOGGER.info("设置皮肤模式: {}", pattern);

        // 立即尝试重新加载皮肤
        if (this.level().isClientSide) {
            this.loadSkinFromResourcePack();
        }
    }

    // 修改 loadSkinFromUUID 方法，支持模式化皮肤加载
    public void loadSkinFromUUID() {
        if (this.level().isClientSide && skinLoadState != SkinLoadState.LOADED && !skinLoadedFromUUID) {
            LOGGER.debug("开始基于模式加载皮肤: {}", skinPattern);

            skinLoadState = SkinLoadState.LOADING;

            try {
                // 处理占位符
                String processedPattern = processSkinPattern(skinPattern);

                // 使用资源包皮肤加载器获取皮肤
                ResourcePackSkinLoader skinLoader = ResourcePackSkinLoader.getInstance();
                ResourceLocation skinLocation;

                // 检查是否是模式化路径（包含占位符）
                if (processedPattern.contains("{") && processedPattern.contains("}")) {
                    LOGGER.debug("检测到模式化皮肤路径: {}", processedPattern);
                    // 对于模式化路径，直接使用资源包皮肤加载器
                    skinLocation = skinLoader.getSkinByName(this.playerName);
                } else {
                    // 对于固定路径，创建资源位置
                    skinLocation = new ResourceLocation(processedPattern);

                    // 检查资源是否存在
                    if (!Minecraft.getInstance().getResourceManager().getResource(skinLocation).isPresent()) {
                        // 如果固定路径不存在，回退到使用玩家名查找
                        skinLocation = skinLoader.getSkinByName(this.playerName);
                    }
                }

                if (skinLocation != null && !skinLocation.getPath().contains("steve") &&
                        !skinLocation.getPath().contains("default")) {
                    skinTexture = skinLocation;
                    skinLoadState = SkinLoadState.LOADED;
                    skinLoadedFromUUID = true;
                    LOGGER.info("✅ 成功加载皮肤: {}", skinLocation);

                    // 缓存皮肤
                    SKIN_CACHE.put(this.playerName, skinLocation);
                } else {
                    LOGGER.warn("无法找到合适的皮肤，使用默认皮肤");
                    skinTexture = DefaultPlayerSkin.getDefaultSkin();
                    skinLoadState = SkinLoadState.FAILED;
                }
            } catch (Exception e) {
                LOGGER.warn("基于模式加载皮肤失败: {}", e.getMessage());
                skinTexture = DefaultPlayerSkin.getDefaultSkin();
                skinLoadState = SkinLoadState.FAILED;
            }
        }
    }

    public void loadSkinFromResourcePack() {
        if (this.level().isClientSide) {
            // 确保ResourcePackSkinLoader已初始化
            ResourcePackSkinLoader skinLoader = ResourcePackSkinLoader.getInstance();
            if (!skinLoader.isInitialized()) {
                LOGGER.info("🔄 初始化ResourcePackSkinLoader...");
                skinLoader.initialize();
            }

            // 如果有自定义皮肤名，优先使用；否则使用玩家名
            String skinNameToUse = (customSkinName != null) ? customSkinName : playerName;
            LOGGER.info("🔍 开始从资源包加载皮肤: {}", skinNameToUse);

            // 先检查缓存
            if (SKIN_CACHE.containsKey(skinNameToUse)) {
                ResourceLocation cachedSkin = SKIN_CACHE.get(skinNameToUse);
                // 验证缓存皮肤是否仍然有效
                if (isSkinValid(cachedSkin)) {
                    skinTexture = cachedSkin;
                    skinLoadState = SkinLoadState.LOADED;
                    skinLoadedFromUUID = true;
                    LOGGER.info("✅ 从缓存加载皮肤: {}", skinTexture);
                    syncSkinStateToServer();
                    return;
                } else {
                    // 缓存无效，移除
                    SKIN_CACHE.remove(skinNameToUse);
                    LOGGER.warn("❌ 缓存皮肤无效，重新加载: {}", cachedSkin);
                }
            }

            skinLoadState = SkinLoadState.LOADING;
            LOGGER.info("🔄 皮肤加载中...");
            syncSkinStateToServer();

            try {
                Set<String> allSkins = skinLoader.getAllSkinNames();
                LOGGER.info("所有可用皮肤: {}", allSkins);

                // 首先尝试精确匹配
                ResourceLocation skinLocation = skinLoader.getSkinByName(skinNameToUse);
                LOGGER.info("精确匹配结果: {} -> {}", skinNameToUse, skinLocation);

                // 验证皮肤资源是否真实存在
                if (skinLocation != null && isSkinValid(skinLocation)) {
                    skinTexture = skinLocation;
                    skinLoadState = SkinLoadState.LOADED;
                    skinLoadedFromUUID = true;
                    LOGGER.info("✅ 成功从资源包加载皮肤: {}", skinLocation);

                    // 缓存皮肤
                    SKIN_CACHE.put(skinNameToUse, skinLocation);
                    syncSkinStateToServer();
                    return;
                }

                // 如果找不到，尝试外部皮肤前缀
                String externalSkinName = "external_" + skinNameToUse;
                ResourceLocation externalSkin = skinLoader.getSkinByName(externalSkinName);
                if (externalSkin != null && isSkinValid(externalSkin)) {
                    LOGGER.info("✅ 找到外部皮肤: {} -> {}", externalSkinName, externalSkin);
                    skinTexture = externalSkin;
                    skinLoadState = SkinLoadState.LOADED;
                    skinLoadedFromUUID = true;
                    SKIN_CACHE.put(skinNameToUse, externalSkin);
                    syncSkinStateToServer();
                    return;
                }

                // 如果还是找不到，尝试小写匹配
                String lowerName = skinNameToUse.toLowerCase();
                ResourceLocation lowerCaseSkin = skinLoader.getSkinByName(lowerName);
                if (lowerCaseSkin != null && isSkinValid(lowerCaseSkin)) {
                    LOGGER.info("✅ 找到小写匹配皮肤: {} -> {}", lowerName, lowerCaseSkin);
                    skinTexture = lowerCaseSkin;
                    skinLoadState = SkinLoadState.LOADED;
                    skinLoadedFromUUID = true;
                    SKIN_CACHE.put(skinNameToUse, lowerCaseSkin);
                    syncSkinStateToServer();
                    return;
                }

                // 最后尝试万用皮肤格式
                String universalName = "player_" + skinNameToUse.toLowerCase();
                ResourceLocation universalSkin = skinLoader.getSkinByName(universalName);
                if (universalSkin != null && isSkinValid(universalSkin)) {
                    LOGGER.info("✅ 找到万用皮肤: {} -> {}", universalName, universalSkin);
                    skinTexture = universalSkin;
                    skinLoadState = SkinLoadState.LOADED;
                    skinLoadedFromUUID = true;
                    SKIN_CACHE.put(skinNameToUse, universalSkin);
                    syncSkinStateToServer();
                    return;
                }

                // 所有尝试都失败，使用默认皮肤
                LOGGER.warn("❌ 资源包中没有找到有效皮肤: {}, 使用默认皮肤", skinNameToUse);
                skinTexture = DefaultPlayerSkin.getDefaultSkin();
                skinLoadState = SkinLoadState.FAILED;
                syncSkinStateToServer();

            } catch (Exception e) {
                LOGGER.error("❌ 从资源包加载皮肤失败: {}", e.getMessage(), e);
                skinTexture = DefaultPlayerSkin.getDefaultSkin();
                skinLoadState = SkinLoadState.FAILED;
                syncSkinStateToServer();
            }
        }
    }

    // 添加皮肤验证方法
    private boolean isSkinValid(ResourceLocation skinLocation) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(skinLocation);
            if (resource.isPresent()) {
                LOGGER.info("✅ 皮肤资源验证成功: {}", skinLocation);
                return true;
            } else {
                LOGGER.warn("❌ 皮肤资源不存在: {}", skinLocation);
                return false;
            }
        } catch (Exception e) {
            LOGGER.warn("❌ 检查皮肤资源时出错: {}", e.getMessage());
            return false;
        }
    }
    // 添加同步皮肤状态到服务端的方法
    private void syncSkinStateToServer() {
        if (this.level().isClientSide) {
            // 发送皮肤状态更新包到服务端
            try {
                NetworkHandler.INSTANCE.sendToServer(new SkinUpdatePacket(
                        this.getId(),
                        this.skinTexture,
                        this.skinLoadState.ordinal()
                ));
                LOGGER.debug("✅ 已发送皮肤状态到服务端: {}", this.skinLoadState);
            } catch (Exception e) {
                LOGGER.error("❌ 发送皮肤状态到服务端失败", e);
            }
        }
    }

    // 添加处理皮肤模式的方法
    private String processSkinPattern(String pattern) {
        String result = pattern;

        // 替换占位符
        if (result.contains("{USERNAME}")) {
            result = result.replace("{USERNAME}", this.playerName);
        }

        if (result.contains("{UUID}")) {
            String uuidStr = this.skinUUID.toString().replace("-", "");
            result = result.replace("{UUID}", uuidStr);
        }

        if (result.contains("{UUID_SHORT}")) {
            String uuidShort = this.skinUUID.toString().substring(0, 8);
            result = result.replace("{UUID_SHORT}", uuidShort);
        }

        if (result.contains("{ENTITY_ID}")) {
            result = result.replace("{ENTITY_ID}", String.valueOf(this.getId()));
        }

        return result;
    }

    // 添加获取玩家名的方法
    public String getPlayerName() {
        return this.playerName;
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

    public static WeaponMorphEntity create(Level level, String weaponType, ItemStack originalItem, GameProfile playerProfile) {
        // 生成随机玩家名
        String randomName = com.xiaoshi2022.everything_morph.util.RandomNameGenerator.getInstance().generateRandomPlayerName();
        WeaponMorphEntity entity = new WeaponMorphEntity(WEAPON_MORPH_ENTITY.get(), level, weaponType, playerProfile, randomName);
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
        compound.putString("PlayerName", playerName);
        compound.putString("SkinPattern", skinPattern);
        compound.putInt("BlockCount", blockCount);
        compound.put("OriginalItem", originalItem.save(new CompoundTag()));

        // 保存UUID用于皮肤加载
        compound.putUUID("SkinUUID", skinUUID);
        compound.putBoolean("SkinLoaded", skinLoadedFromUUID);

        // 保存自定义皮肤名
        if (customSkinName != null) {
            compound.putString("CustomSkinName", customSkinName);
        }

        // 保存皮肤纹理和状态（服务端需要这些信息）
        if (skinTexture != null) {
            compound.putString("SkinTexture", skinTexture.toString());
        }
        compound.putInt("SkinLoadState", skinLoadState.ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        if (compound.contains("SkinPattern")) {
            skinPattern = compound.getString("SkinPattern");
        }

        if (compound.contains("PlayerName")) {
            playerName = compound.getString("PlayerName");
        }

        if (compound.contains("BlockCount")) {
            blockCount = compound.getInt("BlockCount");
        }

        if (compound.contains("OriginalItem")) {
            this.originalItem = ItemStack.of(compound.getCompound("OriginalItem"));
            applyItemStats(this.originalItem);
        }

        // 加载UUID
        if (compound.contains("SkinUUID")) {
            skinUUID = compound.getUUID("SkinUUID");
            skinLoadedFromUUID = compound.getBoolean("SkinLoaded");
        } else {
            // 如果没有保存的UUID，使用实体UUID
            skinUUID = this.getUUID();
        }

        // 加载自定义皮肤名
        if (compound.contains("CustomSkinName")) {
            customSkinName = compound.getString("CustomSkinName");
        }

        // 在数据加载完成后，重新加载皮肤（只在客户端）
        if (this.level().isClientSide) {
            this.skinLoadState = SkinLoadState.NOT_LOADED;
            this.skinLoadedFromUUID = false;

            // 使用客户端调度器而不是服务端
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (this.isAlive()) {
                    this.loadSkinFromResourcePack();
                }
            });
        } else {
            // 服务端：从NBT恢复皮肤状态
            if (compound.contains("SkinTexture")) {
                String texturePath = compound.getString("SkinTexture");
                this.skinTexture = ResourceLocation.tryParse(texturePath);
            }
            if (compound.contains("SkinLoadState")) {
                int stateOrdinal = compound.getInt("SkinLoadState");
                if (stateOrdinal >= 0 && stateOrdinal < SkinLoadState.values().length) {
                    this.skinLoadState = SkinLoadState.values()[stateOrdinal];
                }
            }
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new FollowOwnerGoalSimple(this, 1.2D, 2.0F, 6.0F));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new PlaceBlockGoal(this));

        this.targetSelector.addGoal(1, new FollowOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new FollowOwnerHurtTargetGoal(this));
        // 移除了NearestAttackableTargetGoal，使NPC不再主动攻击任何生物
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
        // 使用随机生成的玩家名作为显示名称
        return Component.literal(this.playerName);
    }

    public void setOwner(Player owner) {
        this.owner = owner;
    }

    public ResourceLocation getSkinTexture() {
        // 客户端：如果皮肤未加载，尝试加载
        if (this.level().isClientSide && skinLoadState != SkinLoadState.LOADED && !skinLoadedFromUUID) {
            loadSkinFromResourcePack();
        }

        if (skinTexture != null && skinLoadState == SkinLoadState.LOADED) {
            return skinTexture;
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    // 添加设置皮肤状态的方法（服务端调用）
    public void setSkinLoadState(SkinLoadState state) {
        this.skinLoadState = state;
    }

    private void syncSkinToClient() {
        if (this.skinTexture != null && !this.level().isClientSide) {
            NetworkHandler.sendToAllTrackingWithRetry(this,
                    new SkinUpdatePacket(this.getId(), this.skinTexture, this.skinLoadState.ordinal()));
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

        // 检查皮肤是否需要更新
        if (skinNeedsUpdate && skinLoadState == SkinLoadState.LOADED) {
            skinNeedsUpdate = false;
            // 通知渲染器皮肤已更新
            this.setSkinTexture(this.skinTexture);
        }

        // 客户端皮肤加载逻辑 - 只在需要时加载
        if (this.level().isClientSide && skinLoadState == SkinLoadState.NOT_LOADED && !skinLoadedFromUUID) {
            loadSkinFromResourcePack();
        }
    }

    /**
     * 确保皮肤已加载，如果未加载则立即尝试加载
     */
    public void ensureSkinLoaded() {
        if (this.level().isClientSide && skinLoadState == SkinLoadState.NOT_LOADED && !skinLoadedFromUUID) {
            loadSkinFromResourcePack();
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

    public LivingEntity getOwner() {
        return this.owner;
    }

    public BlockPos getLastPlayerPlacementPos() {
        return lastPlayerPlacementPos;
    }

    public UUID getSkinUUID() {
        return this.skinUUID;
    }

    public enum SkinLoadState {
        NOT_LOADED, LOADING, LOADED, FAILED
    }

    /**
     * 调试方法：获取实体信息
     */
    public String getDebugInfo() {
        return String.format("ID: %d, SkinState: %s, Skin: %s, UUID: %s, LoadedFromUUID: %b",
                this.getId(), skinLoadState, skinTexture, skinUUID, skinLoadedFromUUID);
    }
}