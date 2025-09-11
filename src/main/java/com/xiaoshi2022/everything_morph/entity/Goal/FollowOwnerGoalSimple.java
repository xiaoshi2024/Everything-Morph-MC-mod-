package com.xiaoshi2022.everything_morph.entity.Goal;

import com.xiaoshi2022.everything_morph.entity.WeaponMorphEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import java.util.EnumSet;

public class FollowOwnerGoalSimple extends Goal {
    private final WeaponMorphEntity entity;
    private final double speed;
    private final float maxDist;
    private final float minDist;
    private Path path;
    private int timeToRecalcPath;
    private Player owner;

    public FollowOwnerGoalSimple(WeaponMorphEntity entity, double speed, float minDist, float maxDist) {
        this.entity = entity;
        this.speed = speed;
        this.minDist = minDist;
        this.maxDist = maxDist;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity living = entity.getOwner();
        if (living == null || !(living instanceof Player)) return false;
        this.owner = (Player) living;
        if (living.isSpectator()) return false;
        if (entity.distanceToSqr(owner) < (double) (minDist * minDist)) return false;
        this.path = entity.getNavigation().createPath(owner, 0);
        return path != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (entity.getNavigation().isDone()) return false;
        return !(entity.distanceToSqr(owner) <= (double) (minDist * minDist));
    }

    @Override
    public void start() {
        entity.getNavigation().moveTo(path, speed);
        timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        entity.getNavigation().stop();
        owner = null;
    }

    @Override
    public void tick() {
        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = 10;
            entity.getNavigation().moveTo(owner, speed);
        }
    }
}