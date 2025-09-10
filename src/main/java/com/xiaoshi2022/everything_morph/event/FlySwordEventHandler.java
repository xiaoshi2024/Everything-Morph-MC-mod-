package com.xiaoshi2022.everything_morph.event;

import com.mojang.logging.LogUtils;
import com.xiaoshi2022.everything_morph.Enchantment.FlySwordEnchantment;
import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.entity.FlyingSwordEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID)
public class FlySwordEventHandler {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Level world = event.getLevel();
        if (!world.isClientSide) {
            Player player = event.getEntity();
            if (player != null && !player.isPassenger()) {
                ItemStack stack = event.getItemStack();
                
                // 检查物品是否有飞行剑附魔
                if (stack.getEnchantmentLevel(EverythingMorphMod.FLY_SWORD_ENCHANTMENT.get()) > 0) {
                    // 消耗耐久
                    stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(event.getHand()));
                    
                    // 创建飞行剑实体
                    FlyingSwordEntity entitySword = EverythingMorphMod.FLYING_SWORD_ENTITY.get().create(world);
                    
                    if (entitySword != null) {
                        // 设置物品栈和所有者
                        entitySword.setItemStack(stack.copy());
                        entitySword.setOwnerId(player.getUUID());
                        entitySword.setPos(player.getX(), player.getY(), player.getZ());
                        
                        // 生成实体
                        world.addFreshEntity(entitySword);
                        
                        // 让玩家骑乘实体
                        player.startRiding(entitySword);
                        
                        // 从玩家手中移除物品
                        player.setItemInHand(event.getHand(), ItemStack.EMPTY);
                        
                        // 记录日志
                        LOGGER.debug("玩家 {} 激活了飞行剑！", player.getScoreboardName());
                    }
                }
            }
        }
    }
}