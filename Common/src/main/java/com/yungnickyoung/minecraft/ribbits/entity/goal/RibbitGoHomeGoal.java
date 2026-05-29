package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class RibbitGoHomeGoal extends Goal {
    private static final int PATH_RETRY_TICKS = 40;
    private static final int STUCK_TICKS = 120;
    private static final int BLOCKER_CHECK_START_TICKS = 40;
    private static final int BLOCKER_CHECK_INTERVAL_TICKS = 20;
    private static final int BLOCKER_YIELD_TICKS = 30;
    private static final int NAVIGATION_PRIORITY_HOME = 2;
    private static final double MIN_PROGRESS_DISTANCE_SQR = 0.25D;

    private final RibbitEntity ribbit;
    private final float homePointRange;
    private final float speedModifier;

    private BlockPos targetHome;
    private Vec3 targetPosition;
    private int nextPathAttemptTick;
    private int nextBlockerCheckTick;
    private int ticksWithoutProgress;
    private double bestDistanceToTargetSqr;

    public RibbitGoHomeGoal(RibbitEntity ribbit, float homePointRange, float speedModifier) {
        this.ribbit = ribbit;
        this.homePointRange = homePointRange;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.ribbit.isShelterNight() || this.ribbit.isVehicle()) {
            return false;
        }

        this.ribbit.tryAssignShelterHome();
        BlockPos homePosition = this.ribbit.getShelterNavigationPosition();
        return homePosition != null
                && this.ribbit.hasUsableHomePosition()
                && !this.isHomeReached(homePosition);
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos homePosition = this.ribbit.getShelterNavigationPosition();
        return homePosition != null
                && this.ribbit.isShelterNight()
                && !this.ribbit.isVehicle()
                && this.ribbit.hasUsableHomePosition()
                && !this.isHomeReached(homePosition);
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
        this.ribbit.tryAssignShelterHome();
        this.ribbit.openNearbyShelterDoors();

        if (this.ribbit.tryRestAtHomeBed()) {
            return;
        }

        BlockPos homePosition = this.ribbit.getShelterNavigationPosition();
        if (homePosition == null) {
            return;
        }

        Vec3 navigationTarget = this.ribbit.getHomeNavigationTarget(homePosition);
        float waterModifier = this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0F;
        double speed = this.speedModifier * waterModifier;
        this.ribbit.getNavigation().setSpeedModifier(speed);

        if (!homePosition.equals(this.targetHome) || !navigationTarget.equals(this.targetPosition)) {
            this.targetHome = homePosition;
            this.targetPosition = navigationTarget;
            this.nextPathAttemptTick = 0;
            this.startProgressTracking(navigationTarget);
        }

        if (!this.ribbit.getNavigation().isDone()) {
            this.trackProgress(homePosition, navigationTarget);
            return;
        }

        if (this.ribbit.tickCount < this.nextPathAttemptTick) {
            return;
        }

        Path path = this.ribbit.createShelterNavigationPath(homePosition);
        boolean moving = path != null && this.ribbit.getNavigation().moveTo(path, speed);
        if (!moving) {
            this.nextPathAttemptTick = this.ribbit.tickCount + PATH_RETRY_TICKS;
            this.ribbit.handleShelterPathFailed(homePosition, "path_failed");
            this.clearProgressTracking();
            return;
        }

        this.startProgressTracking(navigationTarget);
        this.ribbit.handleShelterPathStarted(homePosition);
    }

    private boolean isHomeReached(BlockPos homePosition) {
        if (this.ribbit.hasPlayerSetHomePosition()) {
            return homePosition.closerToCenterThan(this.ribbit.position(), this.homePointRange);
        }

        return this.ribbit.isAtShelterTarget(homePosition);
    }

    private void trackProgress(BlockPos homePosition, Vec3 navigationTarget) {
        double distanceToTargetSqr = this.ribbit.position().distanceToSqr(navigationTarget);
        if (distanceToTargetSqr < this.bestDistanceToTargetSqr - MIN_PROGRESS_DISTANCE_SQR) {
            this.bestDistanceToTargetSqr = distanceToTargetSqr;
            this.ticksWithoutProgress = 0;
            return;
        }

        this.ticksWithoutProgress++;
        if (this.ticksWithoutProgress >= BLOCKER_CHECK_START_TICKS
                && this.ribbit.tickCount >= this.nextBlockerCheckTick) {
            this.nextBlockerCheckTick = this.ribbit.tickCount + BLOCKER_CHECK_INTERVAL_TICKS;
            if (this.ribbit.tryYieldToBlockingRibbit(navigationTarget, NAVIGATION_PRIORITY_HOME, "go_home")) {
                this.nextPathAttemptTick = this.ribbit.tickCount + BLOCKER_YIELD_TICKS;
                this.clearProgressTracking();
                return;
            }
        }

        if (this.ticksWithoutProgress >= STUCK_TICKS) {
            this.ribbit.getNavigation().stop();
            this.ribbit.handleShelterPathFailed(homePosition, "stuck");
            this.nextPathAttemptTick = this.ribbit.tickCount + PATH_RETRY_TICKS;
            this.clearProgressTracking();
        }
    }

    private void startProgressTracking(Vec3 navigationTarget) {
        this.bestDistanceToTargetSqr = this.ribbit.position().distanceToSqr(navigationTarget);
        this.ticksWithoutProgress = 0;
    }

    private void clearTarget() {
        this.targetHome = null;
        this.targetPosition = null;
        this.nextPathAttemptTick = 0;
        this.nextBlockerCheckTick = 0;
        this.clearProgressTracking();
    }

    private void clearProgressTracking() {
        this.ticksWithoutProgress = 0;
        this.bestDistanceToTargetSqr = Double.MAX_VALUE;
    }
}
