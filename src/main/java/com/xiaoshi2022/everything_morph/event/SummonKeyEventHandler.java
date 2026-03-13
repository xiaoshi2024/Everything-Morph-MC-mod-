package com.xiaoshi2022.everything_morph.event;

import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.client.FlySwordKeyBindings;
import com.xiaoshi2022.everything_morph.entity.FlyingSwordEntity;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID)
public class SummonKeyEventHandler {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static boolean summonKeyWasDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack mainHandItem = player.getMainHandItem();

        boolean summonKeyDown = FlySwordKeyBindings.summonMorph.isDown();
        if (summonKeyDown && !summonKeyWasDown) {
            // 检查化形附魔
            Map<net.minecraft.world.item.enchantment.Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(mainHandItem);
            if (enchantments.containsKey(EverythingMorphMod.MORPH_ENCHANTMENT.get())) {
                boolean hasExistingMorph = player.level().getEntitiesOfClass(
                        WeaponMorphEntity.class,
                        player.getBoundingBox().inflate(16.0),
                        entity -> entity.getOwner() == player &&
                                ItemStack.matches(entity.getOriginalItem(), mainHandItem)
                ).isEmpty();

                if (hasExistingMorph) {
                    WeaponMorphEntity morphEntity = WeaponMorphEntity.create(
                            player.level(),
                            mainHandItem.copy(),
                            player
                    );
                    morphEntity.setOwner(player);
                    morphEntity.moveTo(player.getX(), player.getY() + 1.0, player.getZ());
                    player.level().addFreshEntity(morphEntity);

                    LOGGER.info("召唤化形NPC，武器: {}, 名称: {}",
                            mainHandItem.getDisplayName().getString(), morphEntity.getCustomNameString());
                }
            }
            
            // 检查御剑飞行附魔
            if (mainHandItem.getEnchantmentLevel(EverythingMorphMod.FLY_SWORD_ENCHANTMENT.get()) > 0) {
                mainHandItem.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));

                FlyingSwordEntity entitySword = EverythingMorphMod.FLYING_SWORD_ENTITY.get().create(player.level());

                if (entitySword != null) {
                    entitySword.setItemStack(mainHandItem.copy());
                    entitySword.setOwnerId(player.getUUID());
                    entitySword.setPos(player.getX(), player.getY(), player.getZ());

                    player.level().addFreshEntity(entitySword);

                    player.startRiding(entitySword);

                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                    LOGGER.debug("玩家 {} 激活了飞行剑！", player.getScoreboardName());
                }
            }
        }
        summonKeyWasDown = summonKeyDown;
    }
}
