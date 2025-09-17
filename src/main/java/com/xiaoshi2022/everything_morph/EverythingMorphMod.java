package com.xiaoshi2022.everything_morph;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Enchantment.MorphEnchantment;
import com.xiaoshi2022.everything_morph.Enchantment.FlySwordEnchantment;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.Network.SkinUpdatePacket;
import com.xiaoshi2022.everything_morph.Renderer.WeaponMorphModel;
import com.xiaoshi2022.everything_morph.Renderer.WeaponMorphRenderer;
import com.xiaoshi2022.everything_morph.Renderer.FlyingSwordRenderer;
import com.xiaoshi2022.everything_morph.client.FlySwordKeyBindings;
import com.xiaoshi2022.everything_morph.client.ResourcePackSkinLoader;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import com.xiaoshi2022.everything_morph.entity.FlyingSwordEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(EverythingMorphMod.MODID)
public class EverythingMorphMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "everything_morph";

    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EverythingMorphMod.MODID);

    public static final RegistryObject<Enchantment> MORPH_ENCHANTMENT = ENCHANTMENTS.register("morph",
            () -> new MorphEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}));
    
    // 飞行剑附魔注册
    public static final RegistryObject<Enchantment> FLY_SWORD_ENCHANTMENT = ENCHANTMENTS.register("fly_sword",
            () -> new FlySwordEnchantment(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.WEAPON,
                    new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}));

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, EverythingMorphMod.MODID);

    public static final RegistryObject<EntityType<WeaponMorphEntity>> WEAPON_MORPH_ENTITY =
            ENTITIES.register("weapon_morph_entity",
                    () -> EntityType.Builder.of((EntityType.EntityFactory<WeaponMorphEntity>)
                                            (type, level) -> new WeaponMorphEntity(type, level, "weapon", new GameProfile(UUID.randomUUID(), "default_player"), null),
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .build("weapon_morph_entity"));

    // 飞行剑实体注册
    public static final RegistryObject<EntityType<FlyingSwordEntity>> FLYING_SWORD_ENTITY = 
            ENTITIES.register("flying_sword_entity",
                    () -> EntityType.Builder.of(FlyingSwordEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .setTrackingRange(80)
                            .setUpdateInterval(3)
                            .setShouldReceiveVelocityUpdates(true)
                            .build("flying_sword_entity"));

    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // 创造模式标签页注册
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EverythingMorphMod.MODID);

    // 创建模组专属的创造模式标签页
    public static final RegistryObject<CreativeModeTab> MORPH_TAB = CREATIVE_MODE_TABS.register("morph_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> createEnchantedBookStack(MORPH_ENCHANTMENT.get()))
                    .displayItems((parameters, output) -> {
                        // 添加所有化形附魔书到标签页
                        output.accept(createEnchantedBookStack(MORPH_ENCHANTMENT.get()));
        output.accept(createEnchantedBookStack(FLY_SWORD_ENCHANTMENT.get()));
                    })
                    .title(Component.translatable("itemGroup.everything_morph.morph_tab"))
                    .build());

    // 创建带有指定附魔的附魔书物品栈
    // 创建带有指定附魔的附魔书物品栈
    private static ItemStack createEnchantedBookStack(Enchantment enchantment) {
        // 使用 EnchantedBookItem 的静态方法创建附魔书
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);

        // 创建一个新的复合标签来存储附魔
        CompoundTag tag = book.getOrCreateTag();
        CompoundTag storedEnchantments = new CompoundTag();

        // 添加附魔到存储标签
        storedEnchantments.putString("id", ForgeRegistries.ENCHANTMENTS.getKey(enchantment).toString());
        storedEnchantments.putInt("lvl", 1);

        // 创建StoredEnchantments列表
        ListTag enchantmentsList = new ListTag();
        enchantmentsList.add(storedEnchantments);

        // 将附魔列表设置到物品标签中
        tag.put("StoredEnchantments", enchantmentsList);

        return book;
    }

    public EverythingMorphMod()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::onEntityAttributeCreation);

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // 注册所有延迟注册器
        ENCHANTMENTS.register(modEventBus);
        ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register config
        Config.register();

        // Some debugging info
        LOGGER.info("万物化形模组已加载！");
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(WEAPON_MORPH_ENTITY.get(), WeaponMorphEntity.createAttributes().build());
        event.put(FLYING_SWORD_ENTITY.get(), FlyingSwordEntity.createAttributes().build());
    }

    // 添加化形附魔书到现有的创造模式标签页（如战斗标签页）
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(createEnchantedBookStack(MORPH_ENCHANTMENT.get()));
            event.accept(createEnchantedBookStack(FLY_SWORD_ENCHANTMENT.get()));
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Some common setup code
        NetworkHandler.register();
        LOGGER.info("万物化形 - 通用设置完成");

        // 在 commonSetup 方法中添加
        event.enqueueWork(() -> {
            // 注册调试命令
            MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        });
    }


// 在 EverythingMorphMod.java 中修改 onRegisterCommands 方法

    private void onRegisterCommands(RegisterCommandsEvent event) {

        event.getDispatcher().register(
                Commands.literal("morphinfo")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (player != null) {
                                // 获取玩家周围的所有 WeaponMorphEntity
                                List<WeaponMorphEntity> morphs = player.level().getEntitiesOfClass(
                                        WeaponMorphEntity.class,
                                        player.getBoundingBox().inflate(10.0),
                                        entity -> entity.getOwner() == player
                                );

                                if (morphs.isEmpty()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ 你周围没有化形实体"));
                                    return 0;
                                }

                                for (WeaponMorphEntity morph : morphs) {
                                    String info = String.format("实体ID: %d, 名称: %s, 皮肤状态: %s",
                                            morph.getId(),
                                            morph.getDisplayName().getString(),
                                            morph.getSkinLoadState().toString());
                                    ctx.getSource().sendSuccess(() -> Component.literal(info), false);
                                }
                                return 1;
                            }
                            return 0;
                        })
        );


        event.getDispatcher().register(
                Commands.literal("setmorphskin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .then(Commands.argument("skin_pattern", StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayer();
                                            int entityId = IntegerArgumentType.getInteger(ctx, "id");
                                            String skinPattern = StringArgumentType.getString(ctx, "skin_pattern");

                                            Level level = player.level();
                                            Entity entity = level.getEntity(entityId);

                                            if (entity instanceof WeaponMorphEntity morph) {
                                                // 使用占位符替换
                                                String finalPattern = replacePlaceholders(skinPattern, morph);
                                                morph.setSkinPattern(finalPattern);
                                                morph.reloadSkin();

                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("✅ 已设置实体 " + entityId + " 的皮肤模式为: " + finalPattern),
                                                        false
                                                );
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("❌ 实体 " + entityId + " 不是 WeaponMorphEntity 或不存在")
                                                );
                                                return 0;
                                            }
                                        })
                                )
                        )
        );

        // 给指定实体设置皮肤UUID和玩家名
        event.getDispatcher().register(
                Commands.literal("setmorphskinwithname")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .then(Commands.argument("uuid", StringArgumentType.string())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayer();
                                                    int entityId = IntegerArgumentType.getInteger(ctx, "id");
                                                    String uuidString = StringArgumentType.getString(ctx, "uuid");
                                                    String playerName = StringArgumentType.getString(ctx, "name");

                                                    Level level = player.level();
                                                    Entity entity = level.getEntity(entityId);

                                                    if (entity instanceof WeaponMorphEntity morph) {
                                                        try {
                                                            UUID skinUUID = UUID.fromString(uuidString);
                                                            morph.setSkinUUIDAndName(skinUUID, playerName);
                                                            morph.reloadSkin();
                                                            ctx.getSource().sendSuccess(
                                                                    () -> Component.literal("✅ 已设置实体 " + entityId + " 的皮肤UUID为: " + skinUUID + ", 玩家名: " + playerName),
                                                                    false
                                                            );
                                                            return 1;
                                                        } catch (IllegalArgumentException e) {
                                                            ctx.getSource().sendFailure(
                                                                    Component.literal("❌ 无效的UUID格式: " + uuidString)
                                                            );
                                                            return 0;
                                                        }
                                                    } else {
                                                        ctx.getSource().sendFailure(
                                                                Component.literal("❌ 实体 " + entityId + " 不是 WeaponMorphEntity 或不存在")
                                                        );
                                                        return 0;
                                                    }
                                                })
                                        )
                                )
                        )
        );

        event.getDispatcher().register(Commands.literal("debugmorph")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null) {
                        // 使用正确的方法创建附魔书
                        ItemStack enchantedBook = createEnchantedBookStack(MORPH_ENCHANTMENT.get());
                        player.addItem(enchantedBook);

                        context.getSource().sendSuccess(() ->
                                Component.literal("给了你一本武器化形附魔书"), false);
                    }
                    return 1;
                }));

        // 添加飞行剑测试命令 - 同样需要修复
        event.getDispatcher().register(Commands.literal("debugflysword")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null) {
                        // 使用正确的方法创建附魔书
                        ItemStack enchantedBook = createEnchantedBookStack(FLY_SWORD_ENCHANTMENT.get());
                        player.addItem(enchantedBook);

                        context.getSource().sendSuccess(() ->
                                Component.literal("给了你一本飞行剑附魔书"), false);
                    }
                    return 1;
                }));

        // 在 onRegisterCommands 方法中添加
        event.getDispatcher().register(Commands.literal("summonmorph")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null) {
                        ItemStack heldItem = player.getMainHandItem();
                        if (heldItem.isEmpty()) {
                            context.getSource().sendFailure(Component.literal("请手持一个物品"));
                            return 0;
                        }

                        // 获取玩家的 GameProfile
                        GameProfile playerProfile = player.getGameProfile();

                        WeaponMorphEntity morphEntity = WeaponMorphEntity.create(
                                player.level(),
                                getWeaponType(heldItem),
                                heldItem.copy(),
                                playerProfile // 传递玩家的 GameProfile
                        );
                        morphEntity.setOwner(player);
                        morphEntity.moveTo(player.getX(), player.getY() + 1.0, player.getZ());
                        player.level().addFreshEntity(morphEntity);

                        context.getSource().sendSuccess(() ->
                                        Component.literal("成功召唤化形NPC，物品: " + heldItem.getDisplayName().getString()),
                                false
                        );
                        return 1;
                    }
                    return 0;
                }));

        // 添加皮肤调试命令
        event.getDispatcher().register(
                Commands.literal("debugskin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("skin_name", StringArgumentType.string())
                                .executes(ctx -> {
                                    String skinName = StringArgumentType.getString(ctx, "skin_name");
                                    ResourceLocation skin = ResourcePackSkinLoader.getInstance().getSkinByName(skinName);

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("皮肤查找结果: " + skinName + " -> " + skin),
                                            false
                                    );
                                    return 1;
                                })
                        )
        );

        // 在指令注册中添加调试指令
        event.getDispatcher().register(
                Commands.literal("debugskininfo")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    int entityId = IntegerArgumentType.getInteger(ctx, "id");

                                    Level level = player.level();
                                    Entity entity = level.getEntity(entityId);

                                    if (entity instanceof WeaponMorphEntity morph) {
                                        String debugInfo = String.format(
                                                "实体ID: %d, 名称: %s, 自定义皮肤名: %s, 状态: %s, 皮肤纹理: %s, 已加载: %b",
                                                morph.getId(),
                                                morph.getPlayerName(),
                                                morph.customSkinName != null ? morph.customSkinName : "null",
                                                morph.getSkinLoadState(),
                                                morph.getSkinTexture(),
                                                morph.skinLoadedFromUUID
                                        );

                                        ctx.getSource().sendSuccess(() -> Component.literal(debugInfo), false);
                                        return 1;
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("❌ 实体不存在"));
                                        return 0;
                                    }
                                })
                        )
        );

// 修改 setexternalskin 指令
        event.getDispatcher().register(
                Commands.literal("setexternalskin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .then(Commands.argument("skin_name", StringArgumentType.string())
                                        .executes(ctx -> {
                                            try {
                                                ServerPlayer player = ctx.getSource().getPlayer();
                                                if (player == null) {
                                                    ctx.getSource().sendFailure(Component.literal("❌ 只有玩家可以执行此命令"));
                                                    return 0;
                                                }

                                                int entityId = IntegerArgumentType.getInteger(ctx, "id");
                                                String skinName = StringArgumentType.getString(ctx, "skin_name");

                                                Level level = player.level();
                                                Entity entity = level.getEntity(entityId);

                                                if (entity instanceof WeaponMorphEntity morph) {
                                                    LOGGER.info("开始设置皮肤: 实体 {} -> 皮肤 {}", entityId, skinName);

                                                    // 设置自定义皮肤名
                                                    morph.setCustomSkinName(skinName);

                                                    // 强制重新加载皮肤（在服务端设置状态）
                                                    morph.skinLoadState = WeaponMorphEntity.SkinLoadState.NOT_LOADED;
                                                    morph.skinLoadedFromUUID = false;

                                                    ctx.getSource().sendSuccess(
                                                            () -> Component.literal("✅ 已设置实体 " + entityId + " 使用外部皮肤: " + skinName),
                                                            false
                                                    );
                                                    return 1;
                                                } else {
                                                    ctx.getSource().sendFailure(
                                                            Component.literal("❌ 实体 " + entityId + " 不是 WeaponMorphEntity 或不存在")
                                                    );
                                                    return 0;
                                                }
                                            } catch (Exception e) {
                                                LOGGER.error("setexternalskin 指令执行错误", e);
                                                ctx.getSource().sendFailure(
                                                        Component.literal("❌ 命令执行错误: " + e.getMessage())
                                                );
                                                return 0;
                                            }
                                        })
                                )
                        )
        );

        // 添加皮肤验证命令
        event.getDispatcher().register(
                Commands.literal("validateskin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("skin_name", StringArgumentType.string())
                                .executes(ctx -> {
                                    String skinName = StringArgumentType.getString(ctx, "skin_name");
                                    ResourceLocation skin = ResourcePackSkinLoader.getInstance().getSkinByName(skinName);

                                    // 验证皮肤
                                    boolean isValid = false;
                                    try {
                                        var resource = Minecraft.getInstance().getResourceManager().getResource(skin);
                                        isValid = resource.isPresent();
                                    } catch (Exception e) {
                                        isValid = false;
                                    }

                                    boolean finalIsValid = isValid;
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("皮肤验证: " + skinName + " -> " + skin + " (有效: " + finalIsValid + ")"),
                                            false
                                    );
                                    return 1;
                                })
                        )
        );

// 3. 使用玩家名自动匹配皮肤指令
        event.getDispatcher().register(
                Commands.literal("setplayername")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .then(Commands.argument("player_name", StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayer();
                                            int entityId = IntegerArgumentType.getInteger(ctx, "id");
                                            String playerName = StringArgumentType.getString(ctx, "player_name");

                                            Level level = player.level();
                                            Entity entity = level.getEntity(entityId);

                                            if (entity instanceof WeaponMorphEntity morph) {
                                                // 设置玩家名，皮肤加载器会自动匹配对应的皮肤文件
                                                morph.setSkinUUIDAndName(
                                                        UUID.nameUUIDFromBytes(playerName.getBytes(StandardCharsets.UTF_8)),
                                                        playerName
                                                );
                                                morph.reloadSkin();

                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("✅ 已设置实体 " + entityId + " 的玩家名为: " + playerName + "，将自动匹配皮肤"),
                                                        false
                                                );
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFailure(
                                                        Component.literal("❌ 实体 " + entityId + " 不是 WeaponMorphEntity 或不存在")
                                                );
                                                return 0;
                                            }
                                        })
                                )
                        )
        );

// 4. 重新加载所有皮肤指令
        event.getDispatcher().register(
                Commands.literal("reloadmorphskins")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            // 重新加载资源包皮肤
                            if (ctx.getSource().getLevel().isClientSide()) {
                                ResourcePackSkinLoader.getInstance().reloadExternalSkins();
                            }

                            // 重新加载所有实体的皮肤
                            List<WeaponMorphEntity> allMorphs = ctx.getSource().getLevel()
                                    .getEntitiesOfClass(WeaponMorphEntity.class, new AABB(-30000000, -256, -30000000, 30000000, 256, 30000000));

                            for (WeaponMorphEntity morph : allMorphs) {
                                morph.reloadSkin();
                            }

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("✅ 已重新加载 " + allMorphs.size() + " 个化形实体的皮肤"),
                                    false
                            );
                            return 1;
                        })
        );

// 5. 查看可用皮肤列表指令
        event.getDispatcher().register(
                Commands.literal("listmorphskins")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (ctx.getSource().getLevel().isClientSide()) {
                                ResourcePackSkinLoader skinLoader = ResourcePackSkinLoader.getInstance();
                                Set<String> skinNames = skinLoader.getAllSkinNames();

                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("✅ 可用皮肤列表 (" + skinNames.size() + " 个):"),
                                        false
                                );

                                // 分页显示皮肤名称
                                List<String> skinList = new ArrayList<>(skinNames);
                                Collections.sort(skinList);

                                for (int i = 0; i < Math.min(20, skinList.size()); i++) {
                                    String skinName = skinList.get(i);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§7- §e" + skinName),
                                            false
                                    );
                                }

                                if (skinList.size() > 20) {
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§7... 还有 " + (skinList.size() - 20) + " 个皮肤未显示"),
                                            false
                                    );
                                }

                                return 1;
                            }
                            return 0;
                        })
        );

// 在 EverythingMorphMod.java 的 onRegisterCommands 方法末尾添加帮助命令
        event.getDispatcher().register(
                Commands.literal("morphhelp")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§6===== §e万物化形模组使用说明 §6====="), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/morphinfo §7- 查看周围的化形实体信息"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/summonmorph §7- 召唤手持物品的化形实体"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/setmorphskin <实体ID> <皮肤模式> §7- 设置实体皮肤模式"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§e可用占位符: {USERNAME}, {UUID}, {UUID_SHORT}, {ENTITY_ID}"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§e示例: /setmorphskin 123 textures/entity/skins/{USERNAME}.png"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/setexternalskin <实体ID> <皮肤名> §7- 使用外部皮肤文件夹中的皮肤"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§e示例: /setexternalskin 123 dio (使用 config/everything_morph/skins/dio.png)"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/setplayername <实体ID> <玩家名> §7- 设置玩家名并自动匹配皮肤"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/listmorphskins §7- 查看所有可用皮肤列表"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/reloadmorphskins §7- 重新加载所有皮肤"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/debugmorph §7- 获取武器化形附魔书"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/debugflysword §7- 获取飞行剑附魔书"), false);

                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§6=================================="), false);

                            return 1;
                        })
        );

    }

    // 添加辅助方法处理占位符
    // 添加辅助方法处理占位符
    private static String replacePlaceholders(String pattern, WeaponMorphEntity entity) {
        String result = pattern;

        // 替换用户名占位符
        if (result.contains("{USERNAME}")) {
            result = result.replace("{USERNAME}", entity.getPlayerName());
        }

        // 替换UUID占位符
        if (result.contains("{UUID}")) {
            String uuidStr = entity.getSkinUUID().toString().replace("-", "");
            result = result.replace("{UUID}", uuidStr);
        }

        // 替换短UUID占位符（前8位）
        if (result.contains("{UUID_SHORT}")) {
            String uuidShort = entity.getSkinUUID().toString().substring(0, 8);
            result = result.replace("{UUID_SHORT}", uuidShort);
        }

        // 替换实体ID占位符
        if (result.contains("{ENTITY_ID}")) {
            result = result.replace("{ENTITY_ID}", String.valueOf(entity.getId()));
        }

        return result;
    }

    // 添加辅助方法到 EverythingMorphMod 类
    private static String getWeaponType(ItemStack itemStack) {
        if (itemStack.getItem() instanceof SwordItem) return "sword";
        if (itemStack.getItem() instanceof DiggerItem) return "tool";
        if (itemStack.getItem() instanceof BlockItem) return "block";
        return "weapon";
    }

    // 更简单的版本，避免参数问题
    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            Path skinDir = Paths.get("config", "everything_morph", "skins");
            if (Files.exists(skinDir)) {
                try {
                    // 使用 Pack.readMetaAndCreate 方法，更稳定
                    Pack pack = Pack.readMetaAndCreate(
                            "everything_morph_external_skins",
                            Component.literal("Everything Morph External Skins"),
                            true,
                            (name) -> new PathPackResources(name, skinDir, true),
                            PackType.CLIENT_RESOURCES,
                            Pack.Position.TOP,
                            PackSource.BUILT_IN
                    );

                    if (pack != null) {
                        event.addRepositorySource((consumer) -> consumer.accept(pack));
                        LOGGER.info("已注册外部皮肤资源包");
                    }

                } catch (Exception e) {
                    LOGGER.error("注册外部皮肤资源包失败", e);
                }
            }
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("万物化形 - 服务器已启动！");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("万物化形 - 客户端设置完成！");

            // 注册实体渲染器
//            event.enqueueWork(() -> {
//                EntityRenderers.register(WEAPON_MORPH_ENTITY.get(),
//                        context -> new WeaponMorphRenderer(context,
//                                new WeaponMorphModel(context.bakeLayer(WeaponMorphModel.LAYER_LOCATION)),
//                                0.5f));

            event.enqueueWork(() -> {
                EntityRenderers.register(EverythingMorphMod.WEAPON_MORPH_ENTITY.get(),
                        WeaponMorphRenderer::new);

                // 注册飞行剑实体渲染器
                EntityRenderers.register(EverythingMorphMod.FLYING_SWORD_ENTITY.get(),
                        FlyingSwordRenderer::new);

                // 注册键位绑定
                FlySwordKeyBindings.init();

                // 初始化外部皮肤加载器
                ResourcePackSkinLoader.getInstance().initialize();
            });
        }

        // 注册资源重载监听器 - 使用 Forge 的正确事件
        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(ResourcePackSkinLoader.getInstance());
            LOGGER.info("注册了皮肤资源重载监听器");
        }

        // 注册模型层的事件 - 使用正确的事件类型
        @SubscribeEvent
        public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
            // 注册模型层
            event.registerLayerDefinition(WeaponMorphModel.LAYER_LOCATION,
                    WeaponMorphModel::createBodyLayer);
            event.registerLayerDefinition(WeaponMorphModel.INNER_ARMOR_LOCATION,
                    () -> WeaponMorphModel.createArmorLayer(CubeDeformation.NONE));
            event.registerLayerDefinition(WeaponMorphModel.OUTER_ARMOR_LOCATION,
                    () -> WeaponMorphModel.createArmorLayer(new CubeDeformation(0.5F)));

            LOGGER.info("万物化形 - 模型层注册完成！");
        }
    }
}