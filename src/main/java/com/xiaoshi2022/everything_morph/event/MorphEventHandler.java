package com.xiaoshi2022.everything_morph.event;

import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID)
public class MorphEventHandler {
    private static int lastEntitySpawnTime = 0;
    private static final int SPAWN_COOLDOWN = 20; // 20 ticks (1 second)

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        ItemStack weapon = player.getMainHandItem();

        // 检查武器是否具有化形附魔
        if (EnchantmentHelper.getItemEnchantmentLevel(EverythingMorphMod.MORPH_ENCHANTMENT.get(), weapon) > 0) {
            // 有几率触发化形效果
            if (player.level().getRandom().nextFloat() < 0.1f) { // 10%几率
                spawnMorphEntity(player, weapon);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Level level = player.level();

        // 只在服务器端处理
        if (level.isClientSide) {
            return;
        }

        // 如果玩家正在潜行或者在使用GUI界面，不处理
        if (player.isCrouching() || player.containerMenu != player.inventoryMenu) {
            return;
        }

        // 检查物品是否具有化形附魔
        if (EnchantmentHelper.getItemEnchantmentLevel(EverythingMorphMod.MORPH_ENCHANTMENT.get(), stack) > 0) {
            // 右键点击触发化形
            if (spawnMorphEntity(player, stack)) {
                // 成功生成实体后，移除玩家手中的物品
                if (!player.isCreative()) {
                    // 直接设置手中的物品为空
                    player.setItemInHand(event.getHand(), ItemStack.EMPTY);
                }
                event.setCanceled(true);
            }
        }
    }

    // 在 spawnMorphEntity 方法中修改皮肤加载逻辑

    private static boolean spawnMorphEntity(Player player, ItemStack item) {
        if (!player.level().isClientSide) {
            long currentTime = player.level().getGameTime();
            if (currentTime - lastEntitySpawnTime < SPAWN_COOLDOWN) {
                return false; // 冷却时间内不生成实体
            }
            lastEntitySpawnTime = (int) currentTime;

            try {
                WeaponMorphEntity morphEntity = EverythingMorphMod.WEAPON_MORPH_ENTITY.get().create(player.level());
                if (morphEntity != null) {
                    // 先设置位置，再设置所有者
                    morphEntity.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                    morphEntity.setYRot(player.getYRot());

                    // 设置所有者
                    morphEntity.setOwner(player);
                    LOGGER.info("设置实体 {} 的所有者为 {}", morphEntity.getId(), player.getName().getString());

                    // 设置实体属性基于物品
                    setupEntityFromItem(morphEntity, item);

                    // 生成实体
                    if (player.level().addFreshEntity(morphEntity)) {
                        LOGGER.info("化形实体生成成功! ID: " + morphEntity.getId());

                        // 注意：皮肤加载已经在实体构造函数中开始了
                        // 不需要在这里强制加载皮肤

                        // 移除玩家手中的物品（非创造模式）
                        if (!player.isCreative()) {
                            item.shrink(1);
                            if (item.isEmpty()) {
                                player.setItemInHand(player.getUsedItemHand(), ItemStack.EMPTY);
                            }
                        }
                        return true;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("生成化形实体时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return false;
    }

    private static void setupEntityFromItem(WeaponMorphEntity entity, ItemStack item) {
        // 继承物品的伤害属性
        if (item.getTag() != null && item.getTag().contains("Damage")) {
            int damage = item.getTag().getInt("Damage");
            // 根据伤害值设置实体攻击力
            entity.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0 + damage * 0.1);
        }

        // 继承物品的附魔属性
        if (item.isEnchanted()) {
            var enchantments = EnchantmentHelper.getEnchantments(item);
            // 可以在这里处理特定附魔效果
        }

        // 设置实体名称显示
        if (item.hasCustomHoverName()) {
            entity.setCustomName(item.getHoverName());
            entity.setCustomNameVisible(true);
        }
    }
}