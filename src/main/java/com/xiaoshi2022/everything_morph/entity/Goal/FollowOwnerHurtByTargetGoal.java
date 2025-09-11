package com.xiaoshi2022.everything_morph.entity.Goal;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

/** 主人被谁打，我就打谁 */
public class FollowOwnerHurtByTargetGoal extends TargetGoal {
    private final WeaponMorphEntity morph;
    private LivingEntity ownerAttacker;
    private int timestamp;

    public FollowOwnerHurtByTargetGoal(WeaponMorphEntity e) {
        super(e, false);
        this.morph = e;
    }

    @Override
    public boolean canUse() {
        Player owner = (Player) morph.getOwner();
        if (owner == null) return false;
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker == null || attacker == morph) return false;
        this.ownerAttacker = attacker;
        this.timestamp = owner.getLastHurtByMobTimestamp();
        return true;
    }

    @Override
    public void start() {
        mob.setTarget(ownerAttacker);
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        Player owner = (Player) morph.getOwner();
        return owner != null && owner.getLastHurtByMobTimestamp() == timestamp
                && owner.getLastHurtByMob() != null && owner.getLastHurtByMob().isAlive();
    }
}