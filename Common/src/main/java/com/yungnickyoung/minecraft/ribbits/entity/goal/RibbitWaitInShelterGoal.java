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
    private static final int BLOCKER_CHECK_START_TICKS = 40;
    private static final int BLOCKER_CHECK_INTERVAL_TICKS = 20;
    private static final int BLOCKER_YIELD_TICKS = 30;
    private static final int OPPORTUNISTIC_BED_CHECK_INITIAL_MIN_TICKS = 60;
    private static final int OPPORTUNISTIC_BED_CHECK_INITIAL_JITTER_TICKS = 40;
    private static final int OPPORTUNISTIC_BED_CHECK_MIN_TICKS = 120;
    private static final int OPPORTUNISTIC_BED_CHECK_JITTER_TICKS = 80;
    private static final int NAVIGATION_PRIORITY_SHELTER_WAIT = 1;
    private static final double MIN_PROGRESS_DISTANCE_SQR = 0.25D;

    private final RibbitEntity ribbit;
    private final double speedModifier;

    private BlockPos targetWaitPosition;
    private int nextPathAttemptTick;
    private int nextBlockerCheckTick;
    private int nextOpportunisticBedCheckTick;
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
        this.scheduleInitialOpportunisticBedCheck();
    }

    @Override
    public void stop() {
        this.ribbit.clearNavigationEntityBlockerMemory();
        this.clearTarget();
    }

    @Override
    public void tick() {
        BlockPos waitPosition = this.ribbit.getNightShelterWaitPosition();
        if (waitPosition == null) {
            return;
        }

        double speed = this.speedModifier * (this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0F);
        if (this.ribbit.tickNavigationEntityBlockerDetour(speed)) {
            return;
        }

        if (this.ribbit.isAtNightShelterWaitPosition()) {
            if (this.ribbit.tryFineApproachToNightShelterWaitPosition(speed)) {
                return;
            }

            this.ribbit.holdAtNightShelterWaitPosition();
            return;
        }

        if (this.trySwitchToAvailableBed()) {
            return;
        }

        Vec3 targetPosition = Vec3.atBottomCenterOf(waitPosition);
        this.ribbit.getNavigation().setSpeedModifier(speed);

        if (!waitPosition.equals(this.targetWaitPosition)) {
            this.targetWaitPosition = waitPosition;
            this.nextPathAttemptTick = 0;
            this.scheduleInitialOpportunisticBedCheck();
            this.ribbit.clearNavigationEntityBlockerMemory();
            this.startProgressTracking(targetPosition);
        }

        if (!this.ribbit.getNavigation().isDone()) {
            this.trackProgress(waitPosition, targetPosition, speed);
            return;
        }

        if (this.ribbit.tickCount < this.nextPathAttemptTick) {
            return;
        }

        Path path = this.ribbit.createNightShelterWaitNavigationPath(waitPosition);
        boolean moving = path != null && this.ribbit.getNavigation().moveTo(path, speed);
        if (!moving) {
            this.nextPathAttemptTick = this.ribbit.tickCount + PATH_RETRY_TICKS;
            this.ribbit.handleNightShelterWaitPathFailed();
            this.clearProgressTracking();
            return;
        }

        this.startProgressTracking(targetPosition);
    }

    private void trackProgress(BlockPos waitPosition, Vec3 targetPosition, double speed) {
        double distanceToTargetSqr = this.ribbit.position().distanceToSqr(targetPosition);
        if (distanceToTargetSqr < this.bestDistanceToTargetSqr - MIN_PROGRESS_DISTANCE_SQR) {
            this.bestDistanceToTargetSqr = distanceToTargetSqr;
            this.ticksWithoutProgress = 0;
            this.ribbit.clearNavigationEntityBlockerMemory();
            return;
        }

        this.ticksWithoutProgress++;
        if (this.ticksWithoutProgress >= BLOCKER_CHECK_START_TICKS
                && this.ribbit.tickCount >= this.nextBlockerCheckTick) {
            this.nextBlockerCheckTick = this.ribbit.tickCount + BLOCKER_CHECK_INTERVAL_TICKS;
            RibbitEntity.NavigationBlockerHandling blockerHandling = this.ribbit.tryHandleNavigationBlocker(
                    targetPosition,
                    NAVIGATION_PRIORITY_SHELTER_WAIT,
                    "night_shelter_wait",
                    speed);
            switch (blockerHandling) {
                case YIELDED -> {
                    this.nextPathAttemptTick = this.ribbit.tickCount + BLOCKER_YIELD_TICKS;
                    this.clearProgressTracking();
                    return;
                }
                case DETOURING -> {
                    this.nextPathAttemptTick = this.ribbit.getNavigationEntityBlockerDetourUntilTick();
                    this.clearProgressTracking();
                    return;
                }
                case EXHAUSTED -> {
                    this.ribbit.getNavigation().stop();
                    this.ribbit.handleNightShelterWaitPathFailed();
                    this.nextPathAttemptTick = this.ribbit.tickCount + PATH_RETRY_TICKS;
                    this.clearProgressTracking();
                    return;
                }
                case NONE -> {
                }
            }
        }

        if (this.ticksWithoutProgress >= STUCK_TICKS) {
            this.ribbit.getNavigation().stop();
            this.ribbit.handleNightShelterWaitPathFailed();
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
        this.nextBlockerCheckTick = 0;
        this.nextOpportunisticBedCheckTick = 0;
        this.clearProgressTracking();
    }

    private void clearProgressTracking() {
        this.ticksWithoutProgress = 0;
        this.bestDistanceToTargetSqr = Double.MAX_VALUE;
    }

    private boolean trySwitchToAvailableBed() {
        if (this.ribbit.tickCount < this.nextOpportunisticBedCheckTick) {
            return false;
        }

        this.scheduleNextOpportunisticBedCheck();
        return this.ribbit.tryAssignShelterHomeWhileWaiting();
    }

    private void scheduleInitialOpportunisticBedCheck() {
        this.nextOpportunisticBedCheckTick = this.ribbit.tickCount
                + OPPORTUNISTIC_BED_CHECK_INITIAL_MIN_TICKS
                + this.ribbit.getRandom().nextInt(OPPORTUNISTIC_BED_CHECK_INITIAL_JITTER_TICKS);
    }

    private void scheduleNextOpportunisticBedCheck() {
        this.nextOpportunisticBedCheckTick = this.ribbit.tickCount
                + OPPORTUNISTIC_BED_CHECK_MIN_TICKS
                + this.ribbit.getRandom().nextInt(OPPORTUNISTIC_BED_CHECK_JITTER_TICKS);
    }
}
