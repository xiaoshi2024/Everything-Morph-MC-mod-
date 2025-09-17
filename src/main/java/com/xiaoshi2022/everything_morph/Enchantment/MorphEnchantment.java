package com.xiaoshi2022.everything_morph.Enchantment;

import com.mojang.authlib.GameProfile;
import com.xiaoshi2022.everything_morph.EverythingMorphMod;
import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = EverythingMorphMod.MODID)
public class MorphEnchantment extends Enchantment {
    public MorphEnchantment(Rarity rarity, EnchantmentCategory category, EquipmentSlot[] slots) {
        super(rarity, category, slots);
    }

    // 添加事件监听 - 当实体造成伤害时触发
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
                    // 生成随机玩家名
                    String randomName = com.xiaoshi2022.everything_morph.util.RandomNameGenerator.getInstance().generateRandomPlayerName();

                    // 使用随机玩家名创建GameProfile
                    UUID nameBasedUUID = UUID.nameUUIDFromBytes(randomName.getBytes(StandardCharsets.UTF_8));
                    GameProfile playerProfile = new GameProfile(nameBasedUUID, randomName);

                    WeaponMorphEntity morphEntity = WeaponMorphEntity.create(
                            player.level(),
                            getWeaponType(weapon),
                            weapon.copy(),
                            playerProfile
                    );
                    morphEntity.setOwner(player);
                    morphEntity.moveTo(player.getX(), player.getY() + 1.0, player.getZ());
                    player.level().addFreshEntity(morphEntity);

                    EverythingMorphMod.LOGGER.info("召唤化形NPC，武器: {}, 名称: {}", weapon.getDisplayName().getString(), randomName);
                }
            }
        }
    }

    // 根据物品类型获取武器类型
    private static String getWeaponType(ItemStack itemStack) {
        if (itemStack.getItem() instanceof SwordItem) return "sword";
        if (itemStack.getItem() instanceof DiggerItem) return "tool";
        if (itemStack.getItem() instanceof BlockItem) return "block";
        return "weapon";
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