package com.xiaoshi2022.everything_morph.Enchantment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class FlySwordEnchantment extends Enchantment {
    public FlySwordEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots) {
        super(rarity, category, slots);
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public Component getFullname(int level) {
        return Component.translatable("enchantment.everything_morph.fly_sword")
                .withStyle(ChatFormatting.GOLD);
    }

    @Override
    public boolean checkCompatibility(Enchantment other) {
        // 与其他化形附魔共存
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
        return 25 + (level - 1) * 9; // 比普通化形附魔更难获得
    }

    @Override
    public int getMaxCost(int level) {
        return super.getMinCost(level) + 75;
    }

    // 允许任何物品都可以附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return true;
    }
}