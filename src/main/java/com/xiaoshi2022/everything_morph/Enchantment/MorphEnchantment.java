package com.xiaoshi2022.everything_morph.Enchantment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public class MorphEnchantment extends Enchantment {
    public MorphEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots) {
        super(rarity, category, slots);
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
        // 如果是同类型的化形附魔，不允许共存
        if (other instanceof MorphEnchantment) {
            return false;
        }

        // 对于其他附魔，允许共存（修复铁毡问题）
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

    // 允许任何物品都可以附魔
    @Override
    public boolean canEnchant(ItemStack stack) {
        return true;
    }
}