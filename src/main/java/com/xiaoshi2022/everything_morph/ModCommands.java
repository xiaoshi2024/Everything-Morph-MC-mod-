package com.xiaoshi2022.everything_morph;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // 1. 简化召唤指令
        event.getDispatcher().register(
                Commands.literal("morphsummon")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (player == null) return 0;

                            ItemStack heldItem = player.getMainHandItem();
                            if (heldItem.isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("❌ 请手持一个物品"));
                                return 0;
                            }

                            boolean hasEnchant = EnchantmentHelper.getItemEnchantmentLevel(
                                    EverythingMorphMod.MORPH_ENCHANTMENT.get(), heldItem) > 0;

                            if (!hasEnchant && player.hasPermissions(2)) {
                                heldItem.enchant(EverythingMorphMod.MORPH_ENCHANTMENT.get(), 1);
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("§e已临时添加化形附魔"), false);
                            } else if (!hasEnchant) {
                                ctx.getSource().sendFailure(
                                        Component.literal("❌ 物品需要化形附魔"));
                                return 0;
                            }

                            WeaponMorphEntity morphEntity = WeaponMorphEntity.create(
                                    player.level(), heldItem, player);

                            morphEntity.setPos(player.getX(), player.getY() + 1.0, player.getZ());
                            player.level().addFreshEntity(morphEntity);

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("✅ 已召唤化形: " +
                                            heldItem.getHoverName().getString()), false);
                            return 1;
                        })
        );

        // 2. 查看物品名称信息
        event.getDispatcher().register(
                Commands.literal("itemnameinfo")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (player == null) return 0;

                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                ctx.getSource().sendFailure(Component.literal("❌ 请手持物品"));
                                return 0;
                            }

                            String displayName = held.getDisplayName().getString();
                            String customName = held.hasCustomHoverName() ?
                                    held.getHoverName().getString() : null;

                            // 直接使用原始名称，不做任何处理
                            String skinName = "default";
                            if (customName != null && !customName.isEmpty()) {
                                skinName = customName;  // 直接使用原始名称
                            } else {
                                String itemId = WeaponMorphEntity.getRegistryName(held.getItem());
                                if (itemId != null && !itemId.isEmpty()) {
                                    skinName = itemId;
                                }
                            }

                            String itemId = WeaponMorphEntity.getRegistryName(held.getItem());

                            String info = String.format(
                                    "物品: %s\n物品ID: %s\n有自定义名称: %b\n自定义名称: %s\n皮肤将使用: %s\n将向 LittleSkin 查询: %s",
                                    displayName, itemId,
                                    held.hasCustomHoverName(),
                                    customName != null ? customName : "无",
                                    skinName,
                                    skinName
                            );

                            ctx.getSource().sendSuccess(() -> Component.literal(info), false);
                            return 1;
                        })
        );

        // 3. 手动设置皮肤名
        event.getDispatcher().register(
                Commands.literal("morphsetname")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .then(Commands.argument("skin_name", StringArgumentType.string())
                                        .executes(ctx -> {
                                            int entityId = IntegerArgumentType.getInteger(ctx, "id");
                                            String skinName = StringArgumentType.getString(ctx, "skin_name");

                                            Entity entity = ctx.getSource().getLevel().getEntity(entityId);
                                            if (entity instanceof WeaponMorphEntity morph) {
                                                morph.setCustomSkinName(skinName);
                                                morph.reloadSkin();

                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("✅ 已设置实体使用皮肤: " + skinName + " (将向 LittleSkin 查询)"), false);
                                                return 1;
                                            }
                                            ctx.getSource().sendFailure(Component.literal("❌ 实体不存在"));
                                            return 0;
                                        })
                                )
                        )
        );

        // 4. 恢复使用物品名
        event.getDispatcher().register(
                Commands.literal("morphresetname")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int entityId = IntegerArgumentType.getInteger(ctx, "id");
                                    Entity entity = ctx.getSource().getLevel().getEntity(entityId);

                                    if (entity instanceof WeaponMorphEntity morph) {
                                        morph.customSkinName = null;
                                        morph.reloadSkin();

                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("✅ 实体将恢复使用物品名称作为皮肤 (将向 LittleSkin 查询)"), false);
                                        return 1;
                                    }
                                    ctx.getSource().sendFailure(Component.literal("❌ 实体不存在"));
                                    return 0;
                                })
                        )
        );

        // 5. 查看实体信息
        event.getDispatcher().register(
                Commands.literal("morphinfo")
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    int entityId = IntegerArgumentType.getInteger(ctx, "id");
                                    Entity entity = ctx.getSource().getLevel().getEntity(entityId);

                                    if (entity instanceof WeaponMorphEntity morph) {
                                        String info = String.format(
                                                "实体ID: %d\n名称: %s\n皮肤名称: %s\n皮肤状态: %s\n自定义皮肤: %s\n物品ID: %s",
                                                morph.getId(),
                                                morph.getCustomName().getString(),
                                                morph.determineSkinName(),
                                                morph.getSkinLoadState(),
                                                morph.customSkinName != null ? morph.customSkinName : "无",
                                                WeaponMorphEntity.getRegistryName(morph.getOriginalItem().getItem())
                                        );
                                        ctx.getSource().sendSuccess(() -> Component.literal(info), false);
                                        return 1;
                                    }
                                    ctx.getSource().sendFailure(Component.literal("❌ 实体不存在"));
                                    return 0;
                                })
                        )
        );

        // 6. 清除 LittleSkin 缓存（新增）
        event.getDispatcher().register(
                Commands.literal("lsclear")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (ctx.getSource().getLevel().isClientSide()) {
                                ctx.getSource().sendFailure(
                                        Component.literal("§c请在客户端使用此命令"));
                                return 0;
                            }

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§a请在客户端使用 /littleskinclear 命令清除缓存"), false);
                            return 1;
                        })
        );

        // 7. 重新加载皮肤
        event.getDispatcher().register(
                Commands.literal("reloadmorphskins")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            if (ctx.getSource().getLevel().isClientSide()) {
                                ctx.getSource().sendFailure(
                                        Component.literal("§c请在客户端使用此命令"));
                                return 0;
                            }

                            List<WeaponMorphEntity> allMorphs = ctx.getSource().getLevel()
                                    .getEntitiesOfClass(WeaponMorphEntity.class,
                                            new AABB(-30000000, -256, -30000000, 30000000, 256, 30000000));

                            for (WeaponMorphEntity morph : allMorphs) {
                                morph.reloadSkin();
                            }

                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("✅ 已重新加载 " + allMorphs.size() + " 个实体的皮肤 (将重新向 LittleSkin 查询)"), false);
                            return 1;
                        })
        );

        // 8. 帮助命令（简化版）
        event.getDispatcher().register(
                Commands.literal("morphhelp")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§6===== 万物化形使用指南 ====="), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§e1. 在铁砧给物品重命名"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§e2. 附魔化形附魔"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§e3. 右键召唤伙伴，自动从 LittleSkin 查询皮肤"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/morphsummon §7- 直接召唤手持物品"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/itemnameinfo §7- 查看物品名称信息"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/morphsetname <ID> <皮肤名> §7- 手动设置皮肤名"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/morphresetname <ID> §7- 恢复使用物品名"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/morphinfo <ID> §7- 查看实体信息"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/reloadmorphskins §7- 重新加载实体皮肤"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/debugmorph §7- 获取化形附魔书"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§a/debugflysword §7- 获取飞行剑附魔书"), false);
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§6=========================="), false);
                            return 1;
                        })
        );

        // 9. 调试命令
        event.getDispatcher().register(Commands.literal("debugmorph")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null) {
                        ItemStack enchantedBook = EverythingMorphMod.createEnchantedBookStack(EverythingMorphMod.MORPH_ENCHANTMENT.get());
                        player.addItem(enchantedBook);
                        context.getSource().sendSuccess(() ->
                                Component.literal("给了你一本化形附魔书"), false);
                    }
                    return 1;
                }));

        event.getDispatcher().register(Commands.literal("debugflysword")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player != null) {
                        ItemStack enchantedBook = EverythingMorphMod.createEnchantedBookStack(EverythingMorphMod.FLY_SWORD_ENCHANTMENT.get());
                        player.addItem(enchantedBook);
                        context.getSource().sendSuccess(() ->
                                Component.literal("给了你一本飞行剑附魔书"), false);
                    }
                    return 1;
                }));
    }
}