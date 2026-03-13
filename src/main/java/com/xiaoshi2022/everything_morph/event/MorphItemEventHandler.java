package com.xiaoshi2022.everything_morph.event;

import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Config;
import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.Network.SummonMorphPacket;
import com.xiaoshi2022.everything_morph.entity.FlyingSwordEntity;
import com.xiaoshi2022.everything_morph.entity.SmartFlyingSwordEntity;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID, value = Dist.CLIENT)
public class MorphItemEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean summonKeyWasDown = false;
    private static int lastEntitySpawnTime = 0;
    private static final int SPAWN_COOLDOWN = 20; // 1秒冷却（20 ticks）

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)  // 添加这个注解确保只在客户端执行
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // 只在客户端处理按键逻辑
        handleKeyPress(player);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleKeyPress(Player player) {
        // 注意：这里需要导入 FlySwordKeyBindings，但它本身已经是客户端类
        boolean summonKeyDown = com.xiaoshi2022.everything_morph.client.FlySwordKeyBindings.summonMorph.isDown();

        if (summonKeyDown && !summonKeyWasDown) {
            // 按键刚被按下
            ItemStack stack = player.getMainHandItem();

            // 检查冷却
            Level level = player.level();
            long currentTime = level.getGameTime();
            if (currentTime - lastEntitySpawnTime < SPAWN_COOLDOWN) {
                player.displayClientMessage(
                        Component.literal("§c⏳ 技能冷却中，请稍后再试"),
                        true
                );
                summonKeyWasDown = summonKeyDown;
                return;
            }

            // 获取附魔等级
            int morphLevel = EnchantmentHelper.getItemEnchantmentLevel(EverythingMorphMod.MORPH_ENCHANTMENT.get(), stack);
            int flySwordLevel = EnchantmentHelper.getItemEnchantmentLevel(EverythingMorphMod.FLY_SWORD_ENCHANTMENT.get(), stack);

            // 检查是否拥有附魔
            boolean hasMorph = morphLevel > 0;
            boolean hasFlySword = flySwordLevel > 0;

            // 如果没有附魔，直接返回
            if (!hasMorph && !hasFlySword) {
                player.displayClientMessage(
                        Component.literal("§c⚠ 当前手持物品没有相关附魔"),
                        true
                );
                summonKeyWasDown = summonKeyDown;
                return;
            }

            // 发送数据包到服务器端执行实际逻辑
            sendSummonPacket(player, stack, hasMorph, hasFlySword);

            lastEntitySpawnTime = (int) currentTime;
        }

        summonKeyWasDown = summonKeyDown;
    }

    @OnlyIn(Dist.CLIENT)
    private static void sendSummonPacket(Player player, ItemStack stack, boolean hasMorph, boolean hasFlySword) {
        if (player.level().isClientSide) {
            // 使用静态方法检查是否为局域网世界或单人模式
            if (isSinglePlayer()) {
                // 单机模式：直接调用
                if (hasMorph && hasFlySword) {
                    spawnSmartFlyingSwordServer(player, stack);
                } else if (hasMorph) {
                    spawnMorphEntityServer(player, stack);
                } else if (hasFlySword) {
                    spawnFlyingSwordServer(player, stack);
                }
            } else {
                // 联机模式：发送网络包（带配置的重试机制）
                try {
                    NetworkHandler.sendToServer(new SummonMorphPacket(stack, hasMorph, hasFlySword));
                    LOGGER.debug("已发送召唤网络包到服务器（重试次数: {}, 重试延迟: {}ms）",
                            Config.networkRetryCount, Config.networkRetryDelay);

                    player.displayClientMessage(
                            Component.literal("§a📦 已发送召唤请求到服务器..."),
                            true
                    );
                } catch (Exception e) {
                    LOGGER.error("发送网络包时出错: {}", e.getMessage());
                    player.displayClientMessage(
                            Component.literal("§c❌ 网络包发送失败: " + e.getMessage()),
                            true
                    );
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean isSinglePlayer() {
        try {
            return net.minecraft.client.Minecraft.getInstance().isSingleplayer();
        } catch (NoClassDefFoundError | Exception e) {
            // 如果在服务器环境，返回 false
            return false;
        }
    }

    // 服务器端的方法（需要在网络包接收端调用）
    // 这个方法在服务器端运行，所以不能有 @OnlyIn(Dist.CLIENT)
    public static void handleSummonPacket(Player player, ItemStack stack, boolean hasMorph, boolean hasFlySword) {
        if (player == null || player.level().isClientSide) return;

        if (hasMorph && hasFlySword) {
            spawnSmartFlyingSwordServer(player, stack);
        } else if (hasMorph) {
            spawnMorphEntityServer(player, stack);
        } else if (hasFlySword) {
            spawnFlyingSwordServer(player, stack);
        }
    }

    private static void spawnMorphEntityServer(Player player, ItemStack item) {
        try {
            // 检查是否已存在相同物品的化形实体
            boolean hasExistingMorph = player.level().getEntitiesOfClass(
                    WeaponMorphEntity.class,
                    player.getBoundingBox().inflate(16.0),
                    entity -> entity.getOwner() == player &&
                            ItemStack.matches(entity.getOriginalItem(), item)
            ).isEmpty();

            if (!hasExistingMorph) {
                player.displayClientMessage(
                        Component.literal("§c⚠ 附近已存在相同物品的化形实体"),
                        false
                );
                return;
            }

            WeaponMorphEntity morphEntity = WeaponMorphEntity.create(
                    player.level(), item.copy(), player
            );

            if (morphEntity != null) {
                morphEntity.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                morphEntity.setYRot(player.getYRot());

                String displayName = String.valueOf(morphEntity.getCustomName());

                if (player.level().addFreshEntity(morphEntity)) {
                    // 非创造模式消耗物品
                    if (!player.isCreative()) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }

                    player.displayClientMessage(
                            Component.literal("§a✨ 化形实体 " + displayName + " 已召唤！"),
                            false
                    );

                    LOGGER.info("化形实体生成成功! 名称: {}, 来自物品: {}",
                            displayName, item.getDisplayName().getString());
                }
            }
        } catch (Exception e) {
            LOGGER.error("生成化形实体时出错: " + e.getMessage(), e);
            player.displayClientMessage(
                    Component.literal("§c❌ 化形实体生成失败: " + e.getMessage()),
                    false
            );
        }
    }

    private static void spawnFlyingSwordServer(Player player, ItemStack item) {
        try {
            if (player.isPassenger()) {
                player.displayClientMessage(
                        Component.literal("§c⚠ 你已经在骑乘状态"),
                        false
                );
                return;
            }

            // 消耗耐久
            item.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));

            FlyingSwordEntity entitySword = EverythingMorphMod.FLYING_SWORD_ENTITY.get().create(player.level());

            if (entitySword != null) {
                entitySword.setItemStack(item.copy());
                entitySword.setOwnerId(player.getUUID());
                entitySword.setPos(player.getX(), player.getY(), player.getZ());

                if (player.level().addFreshEntity(entitySword)) {
                    player.startRiding(entitySword);

                    // 非创造模式消耗物品
                    if (!player.isCreative()) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }

                    player.displayClientMessage(
                            Component.literal("§b⚔ 御剑飞行已激活！使用 " +
                                    (player.isCreative() ? "潜行" : "Shift") + " 下降"),
                            false
                    );

                    LOGGER.debug("玩家 {} 激活了飞行剑！", player.getScoreboardName());
                }
            }
        } catch (Exception e) {
            LOGGER.error("生成飞行剑时出错: " + e.getMessage(), e);
            player.displayClientMessage(
                    Component.literal("§c❌ 飞行剑生成失败"),
                    false
            );
        }
    }

    private static void spawnSmartFlyingSwordServer(Player player, ItemStack item) {
        try {
            if (player.isPassenger()) {
                player.displayClientMessage(
                        Component.literal("§c⚠ 你已经在骑乘状态"),
                        false
                );
                return;
            }

            // 消耗耐久
            item.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));

            SmartFlyingSwordEntity smartSword = EverythingMorphMod.SMART_FLYING_SWORD_ENTITY.get().create(player.level());

            if (smartSword != null) {
                smartSword.setItemStack(item.copy());
                smartSword.setOwnerId(player.getUUID());
                smartSword.teleportTo(player.getX(), player.getY() + 1.0, player.getZ());

                if (player.level().addFreshEntity(smartSword)) {
                    player.startRiding(smartSword);

                    // 非创造模式消耗物品
                    if (!player.isCreative()) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    }

                    player.displayClientMessage(
                            Component.literal("§d✨ 智能飞行剑已激活！可自由控制飞行方向"),
                            false
                    );

                    LOGGER.info("召唤智能飞行剑成功，武器: {}", item.getDisplayName().getString());
                }
            }
        } catch (Exception e) {
            LOGGER.error("召唤智能飞行剑时出错: {}", e.getMessage(), e);
            player.displayClientMessage(
                    Component.literal("§c❌ 智能飞行剑生成失败"),
                    false
            );
        }
    }
}