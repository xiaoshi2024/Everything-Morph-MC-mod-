package com.xiaoshi2022.everything_morph.event;

import com.mojang.authlib.GameProfile;
import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.Network.NetworkHandler;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
                return false;
            }
            lastEntitySpawnTime = (int) currentTime;

            try {
                // 生成随机玩家名
                String randomName = com.xiaoshi2022.everything_morph.util.RandomNameGenerator.getInstance().generateRandomPlayerName();

                // 使用随机玩家名创建GameProfile
                UUID nameBasedUUID = UUID.nameUUIDFromBytes(randomName.getBytes(StandardCharsets.UTF_8));
                GameProfile playerProfile = new GameProfile(nameBasedUUID, randomName);

                WeaponMorphEntity morphEntity = WeaponMorphEntity.create(player.level(), "weapon", item, playerProfile);
                if (morphEntity != null) {
                    morphEntity.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                    morphEntity.setYRot(player.getYRot());
                    morphEntity.setOwner(player);

                    // 设置实体名称
                    morphEntity.setCustomName(Component.literal(randomName));

                    if (player.level().addFreshEntity(morphEntity)) {
                        LOGGER.info("化形实体生成成功! ID: {}, 名称: {}", morphEntity.getId(), randomName);
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