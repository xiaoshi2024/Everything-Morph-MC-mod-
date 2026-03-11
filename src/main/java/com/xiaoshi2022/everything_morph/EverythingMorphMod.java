package com.xiaoshi2022.everything_morph;

import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Enchantment.FlySwordEnchantment;
import com.xiaoshi2022.everything_morph.Enchantment.MorphEnchantment;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.Renderer.FlyingSwordRenderer;
import com.xiaoshi2022.everything_morph.Renderer.WeaponMorphModel;
import com.xiaoshi2022.everything_morph.Renderer.WeaponMorphRenderer;
import com.xiaoshi2022.everything_morph.client.FlySwordKeyBindings;
import com.xiaoshi2022.everything_morph.entity.FlyingSwordEntity;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
                    () -> EntityType.Builder.<WeaponMorphEntity>of(
                                    WeaponMorphEntity::new,  // 使用方法引用，而不是lambda
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
    static ItemStack createEnchantedBookStack(Enchantment enchantment) {
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
    }

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

            });
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