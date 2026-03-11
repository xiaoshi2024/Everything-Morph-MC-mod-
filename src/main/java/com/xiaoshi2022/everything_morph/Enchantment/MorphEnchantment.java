package com.xiaoshi2022.everything_morph.Enchantment;

import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID)
public class MorphEnchantment extends Enchantment {
    public MorphEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots) {
        super(rarity, category, slots);
    }

    // 当实体造成伤害时触发
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            ItemStack weapon = player.getMainHandItem();

            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(weapon);
            if (enchantments.containsKey(EverythingMorphMod.MORPH_ENCHANTMENT.get())) {
                boolean hasExistingMorph = player.level().getEntitiesOfClass(
                        WeaponMorphEntity.class,
                        player.getBoundingBox().inflate(16.0),
                        entity -> entity.getOwner() == player &&
                                ItemStack.matches(entity.getOriginalItem(), weapon)
                ).isEmpty();

                if (hasExistingMorph) {
                    // 使用物品的自定义名称，如果没有则使用物品注册名
                    WeaponMorphEntity morphEntity = WeaponMorphEntity.create(
                            player.level(),
                            weapon.copy(),
                            player
                    );
                    morphEntity.setOwner(player);
                    morphEntity.moveTo(player.getX(), player.getY() + 1.0, player.getZ());
                    player.level().addFreshEntity(morphEntity);

                    EverythingMorphMod.LOGGER.info("召唤化形NPC，武器: {}, 名称: {}",
                            weapon.getDisplayName().getString(), morphEntity.getCustomNameString());
                }
            }
        }
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public Component getFullname(int level) {
        return Component.translatable("enchantment.everything_morph.morph")
                .withStyle(ChatFormatting.AQUA);
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        if (other instanceof MorphEnchantment) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return canEnchant(stack);
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    @Override
    public boolean isTreasureOnly() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 9;
    }

    @Override
    public int getMaxCost(int level) {
        return super.getMinCost(level) + 50;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return true;
    }
}