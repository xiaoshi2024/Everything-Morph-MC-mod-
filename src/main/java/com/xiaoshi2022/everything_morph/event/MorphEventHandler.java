package com.xiaoshi2022.everything_morph.event;

import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID)
public class MorphEventHandler {
    private static int lastEntitySpawnTime = 0;
    private static final int SPAWN_COOLDOWN = 20;

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        Level level = player.level();

        if (level.isClientSide) return;
        if (player.isCrouching() || player.containerMenu != player.inventoryMenu) return;

        if (EnchantmentHelper.getItemEnchantmentLevel(EverythingMorphMod.MORPH_ENCHANTMENT.get(), stack) > 0) {
            if (spawnMorphEntity(player, stack)) {
                if (!player.isCreative()) {
                    player.setItemInHand(event.getHand(), ItemStack.EMPTY);
                }
                event.setCanceled(true);
            }
        }
    }

    private static boolean spawnMorphEntity(Player player, ItemStack item) {
        if (!player.level().isClientSide) {
            long currentTime = player.level().getGameTime();
            if (currentTime - lastEntitySpawnTime < SPAWN_COOLDOWN) {
                return false;
            }
            lastEntitySpawnTime = (int) currentTime;

            try {
                // 直接调用新的工厂方法，不再需要GameProfile
                WeaponMorphEntity morphEntity = WeaponMorphEntity.create(
                        player.level(), item, player
                );

                if (morphEntity != null) {
                    morphEntity.setPos(player.getX(), player.getY() + 0.5, player.getZ());
                    morphEntity.setYRot(player.getYRot());

                    // 显示成功消息
                    String displayName = String.valueOf(morphEntity.getCustomName());
                    player.displayClientMessage(
                            Component.literal("§a✨ 化形实体 " + displayName + " 已召唤！"),
                            false
                    );

                    if (player.level().addFreshEntity(morphEntity)) {
                        EverythingMorphMod.LOGGER.info("化形实体生成成功! 名称: {}, 来自物品: {}",
                                displayName, item.getDisplayName().getString());
                        return true;
                    }
                }
            } catch (Exception e) {
                EverythingMorphMod.LOGGER.error("生成化形实体时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return false;
    }
}