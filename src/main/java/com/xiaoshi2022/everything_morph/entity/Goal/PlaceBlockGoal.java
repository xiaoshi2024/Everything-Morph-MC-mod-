package com.xiaoshi2022.everything_morph.entity.Goal;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.world.entity.ai.goal.Goal;

public class PlaceBlockGoal extends Goal {
    private final WeaponMorphEntity entity;
    private int cooldown = 0;

    public PlaceBlockGoal(WeaponMorphEntity entity) {
        this.entity = entity;
    }

    @Override
    public boolean canUse() {
        // 只有当持有方块物品、有方块数量、有主人、并且有记录的玩家放置位置时才使用
        return entity.getBlockCount() > 0 &&
                entity.getOriginalItem().getItem() instanceof net.minecraft.world.item.BlockItem &&
                entity.getOwner() != null &&
                entity.getLastPlayerPlacementPos() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse(); // 持续使用条件与开始使用条件相同
    }

    @Override
    public void start() {
        this.cooldown = 0; // 开始时重置冷却
    }

    @Override
    public void tick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // 直接调用实体的放置方块方法
        // 实体会在自己的 tick() 方法中处理具体的放置逻辑
        // 这里只需要触发行为即可

        // 设置冷却时间，避免每tick都尝试放置
        cooldown = 20;
    }
}