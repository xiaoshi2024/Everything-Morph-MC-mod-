package com.xiaoshi2022.everything_morph.entity.Goal;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import java.util.function.Predicate;

/** 主人打谁，我就打谁 */
public class FollowOwnerHurtTargetGoal extends TargetGoal {
    private final WeaponMorphEntity morph;
    private LivingEntity ownerLastTarget;
    private int timestamp;

    public FollowOwnerHurtTargetGoal(WeaponMorphEntity e) {
        super(e, false, false);
        this.morph = e;
    }

    @Override
    public boolean canUse() {
        Player owner = (Player) morph.getOwner();
        if (owner == null) return false;
        LivingEntity target = owner.getLastHurtMob();
        if (target == null || target == morph) return false;
        if (target.distanceToSqr(morph) > 64 * 64) return false;   // 64 格内
        this.ownerLastTarget = target;
        this.timestamp = owner.getLastHurtMobTimestamp();
        return true;
    }

    @Override
    public void start() {
        mob.setTarget(ownerLastTarget);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        Player owner = (Player) morph.getOwner();
        return owner != null && owner.getLastHurtMobTimestamp() == timestamp
                && owner.getLastHurtMob() != null && owner.getLastHurtMob().isAlive();
    }
}