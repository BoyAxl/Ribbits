package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RibbitWaitInShelterGoal extends Goal {
    private static final int PATH_RETRY_TICKS = 40;
    private static final int STUCK_TICKS = 120;
    private static final double MIN_PROGRESS_DISTANCE_SQR = 0.25D;

    private final RibbitEntity ribbit;
    private final double speedModifier;

    private BlockPos targetWaitPosition;
    private int nextPathAttemptTick;
    private int ticksWithoutProgress;
    private double bestDistanceToTargetSqr;

    public RibbitWaitInShelterGoal(RibbitEntity ribbit, double speedModifier) {
        this.ribbit = ribbit;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.ribbit.canUseNightShelterWait();
    }

    @Override
    public boolean canContinueToUse() {
        return this.ribbit.canUseNightShelterWait();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.clearTarget();
    }

    @Override
    public void stop() {
        this.clearTarget();
    }

    @Override
    public void tick() {
        BlockPos waitPosition = this.ribbit.getNightShelterWaitPosition();
        if (waitPosition == null) {
            return;
        }

        if (this.ribbit.isAtNightShelterWaitPosition()) {
            this.ribbit.holdAtNightShelterWaitPosition();
            return;
        }

        Vec3 targetPosition = Vec3.atBottomCenterOf(waitPosition);
        double speed = this.speedModifier * (this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0F);
        this.ribbit.getNavigation().setSpeedModifier(speed);

        if (!waitPosition.equals(this.targetWaitPosition)) {
            this.targetWaitPosition = waitPosition;
            this.nextPathAttemptTick = 0;
            this.startProgressTracking(targetPosition);
        }

        if (!this.ribbit.getNavigation().isDone()) {
            this.trackProgress(waitPosition, targetPosition);
            return;
        }

        if (this.ribbit.tickCount < this.nextPathAttemptTick) {
            return;
        }

        Path path = this.ribbit.createNightShelterWaitNavigationPath(waitPosition);
        boolean moving = path != null && this.ribbit.getNavigation().moveTo(path, speed);
        if (!moving) {
            this.nextPathAttemptTick = this.ribbit.tickCount + PATH_RETRY_TICKS;
            this.ribbit.handleNightShelterWaitPathFailed("path_failed");
            this.clearProgressTracking();
            return;
        }

        this.startProgressTracking(targetPosition);
    }

    private void trackProgress(BlockPos waitPosition, Vec3 targetPosition) {
        double distanceToTargetSqr = this.ribbit.position().distanceToSqr(targetPosition);
        if (distanceToTargetSqr < this.bestDistanceToTargetSqr - MIN_PROGRESS_DISTANCE_SQR) {
            this.bestDistanceToTargetSqr = distanceToTargetSqr;
            this.ticksWithoutProgress = 0;
            return;
        }

        this.ticksWithoutProgress++;
        if (this.ticksWithoutProgress >= STUCK_TICKS) {
            this.ribbit.getNavigation().stop();
            this.ribbit.handleNightShelterWaitPathFailed("stuck");
            this.nextPathAttemptTick = this.ribbit.tickCount + PATH_RETRY_TICKS;
            this.clearProgressTracking();
        }
    }

    private void startProgressTracking(Vec3 targetPosition) {
        this.bestDistanceToTargetSqr = this.ribbit.position().distanceToSqr(targetPosition);
        this.ticksWithoutProgress = 0;
    }

    private void clearTarget() {
        this.targetWaitPosition = null;
        this.nextPathAttemptTick = 0;
        this.clearProgressTracking();
    }

    private void clearProgressTracking() {
        this.ticksWithoutProgress = 0;
        this.bestDistanceToTargetSqr = Double.MAX_VALUE;
    }
}
