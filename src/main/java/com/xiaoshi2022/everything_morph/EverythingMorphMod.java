package com.xiaoshi2022.everything_morph;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Enchantment.MorphEnchantment;
import com.xiaoshi2022.everything_morph.Enchantment.FlySwordEnchantment;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.Renderer.WeaponMorphModel;
import com.xiaoshi2022.everything_morph.Renderer.WeaponMorphRenderer;
import com.xiaoshi2022.everything_morph.Renderer.FlyingSwordRenderer;
import com.xiaoshi2022.everything_morph.client.FlySwordKeyBindings;
import com.xiaoshi2022.everything_morph.client.ResourcePackSkinLoader;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import com.xiaoshi2022.everything_morph.entity.FlyingSwordEntity;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
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
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.HashMap;

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
                                            (type, level) -> new WeaponMorphEntity(type, level, "weapon"),
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

        // 给指定实体设置玩家皮肤名
        event.getDispatcher().register(
                Commands.literal("setmorphskin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .then(Commands.argument("skinName", StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayer();
                                            int entityId = IntegerArgumentType.getInteger(ctx, "id");
                                            String skinName = StringArgumentType.getString(ctx, "skinName");

                                            Level level = player.level();
                                            Entity entity = level.getEntity(entityId);

                                            if (entity instanceof WeaponMorphEntity morph) {
                                                morph.setGeneratedSkinName(skinName);
                                                morph.loadSkinFromSavedName(); // 立即重载皮肤
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("✅ 已设置实体 " + entityId + " 的皮肤名为: " + skinName),
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
                                ))
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

                        WeaponMorphEntity morphEntity = WeaponMorphEntity.create(
                                player.level(),
                                getWeaponType(heldItem),
                                heldItem.copy()
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
    
    }


    // 添加辅助方法到 EverythingMorphMod 类
    private static String getWeaponType(ItemStack itemStack) {
        if (itemStack.getItem() instanceof SwordItem) return "sword";
        if (itemStack.getItem() instanceof DiggerItem) return "tool";
        if (itemStack.getItem() instanceof BlockItem) return "block";
        return "weapon";
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
            event.enqueueWork(() -> {
                EntityRenderers.register(EverythingMorphMod.WEAPON_MORPH_ENTITY.get(),
                        WeaponMorphRenderer::new);

                // 注册飞行剑实体渲染器
                EntityRenderers.register(EverythingMorphMod.FLYING_SWORD_ENTITY.get(),
                        FlyingSwordRenderer::new);

                // 注册键位绑定
                FlySwordKeyBindings.init();
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