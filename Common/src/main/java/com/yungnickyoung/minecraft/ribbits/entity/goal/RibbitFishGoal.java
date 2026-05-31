package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

public class RibbitFishGoal extends Goal {
    private static final double FISHING_SPOT_REACHED_DISTANCE = 0.18D;
    private static final double CLOSE_APPROACH_DISTANCE = 1.5D;
    private static final double MIN_APPROACH_PROGRESS_DISTANCE_SQR = 0.04D;
    private static final int MAX_APPROACH_TICKS_WITHOUT_PROGRESS = 120;
    private static final int NAVIGATION_DONE_APPROACH_GRACE_TICKS = 40;
    private static final int FISHING_SPOT_RETRY_DELAY_TICKS = 1200;

    private final RibbitEntity ribbit;
    private final double range;
    private final int minRequiredFishTicks;
    private final int maxRequiredFishTicks;
    private final float speedModifier;

    private int requiredFishTicks;
    private int ticksFishing;
    private int ticksWithoutApproachProgress;
    private int nextFishingSpotAttemptTick;
    private double bestApproachDistanceSqr;
    private BlockPos waterPos;
    private BlockPos dryBlockPos;
    private Vec3 dryPos;

    public RibbitFishGoal(RibbitEntity ribbit, double range, float speedModifier, int minRequiredFishTicks, int maxRequiredFishTicks) {
        this.ribbit = ribbit;
        this.range = range;
        this.speedModifier = speedModifier;
        this.minRequiredFishTicks = minRequiredFishTicks;
        this.maxRequiredFishTicks = maxRequiredFishTicks;

        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public void start() {
        this.requiredFishTicks = this.ribbit.getRandom().nextInt(this.minRequiredFishTicks, this.maxRequiredFishTicks);
        this.ticksFishing = 0;
        this.ticksWithoutApproachProgress = 0;
        this.bestApproachDistanceSqr = this.ribbit.distanceToSqr(this.dryPos);

        float waterModifier = this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0f;
        this.ribbit.getNavigation().moveTo(this.dryPos.x(), this.dryPos.y(), this.dryPos.z(), this.speedModifier * waterModifier);
    }

    @Override
    public void stop() {
        this.resetTarget();
    }

    public void resetTarget() {
        this.waterPos = null;
        this.dryBlockPos = null;
        this.dryPos = null;
        this.ticksFishing = 0;

        this.ribbit.setFishing(false);
    }

    @Override
    public boolean isInterruptable() {
        return this.ticksFishing >= this.requiredFishTicks
                || !this.ribbit.isDayActivityTime();
    }

    @Override
    public boolean canUse() {
        if (this.ribbit.isAutonomousAiPaused()) {
            return false;
        }

        if (!this.ribbit.isDayActivityTime()) {
            return false;
        }

        if (this.ribbit.shouldLeaveUpperShelter() || !this.ribbit.isOutdoorActivityPosition()) {
            return false;
        }

        if (this.ribbit.tickCount < this.nextFishingSpotAttemptTick) {
            return false;
        }

        this.waterPos = null;
        this.dryBlockPos = null;
        this.dryPos = null;

        Optional<FishingSpot> fishingSpot = this.findFishingSpot();
        if (fishingSpot.isEmpty()) {
            return false;
        }

        this.waterPos = fishingSpot.get().waterPos();
        this.dryBlockPos = fishingSpot.get().dryPos();
        this.dryPos = Vec3.atBottomCenterOf(this.dryBlockPos.above());
        return true;
    }

    private Optional<FishingSpot> findFishingSpot() {
        Optional<BlockPos> waterPos = BlockPos.findClosestMatch(this.ribbit.getOnPos(), (int) this.range, 5, this::isValidWaterPos);
        return waterPos.flatMap(pos -> this.findDryFishingPos(pos).map(dryPos -> new FishingSpot(pos, dryPos)));
    }

    private boolean isValidWaterPos(BlockPos waterPos) {
        return this.ribbit.level().getFluidState(waterPos).is(FluidTags.WATER)
                && this.hasEmptyCollision(waterPos.above())
                && this.hasDryFishingPos(waterPos);
    }

    private boolean hasDryFishingPos(BlockPos waterPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (this.isValidDryFishingPos(waterPos.relative(dir))) {
                return true;
            }
        }

        return false;
    }

    private Optional<BlockPos> findDryFishingPos(BlockPos waterPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL.shuffledCopy(this.ribbit.getRandom())) {
            BlockPos testedDryPos = waterPos.relative(dir);

            if (this.isValidDryFishingPos(testedDryPos)) {
                return Optional.of(testedDryPos);
            }
        }

        return Optional.empty();
    }

    private boolean isValidDryFishingPos(BlockPos dryPos) {
        return this.hasEmptyCollision(dryPos.above())
                && Block.isFaceFull(this.ribbit.level().getBlockState(dryPos).getCollisionShape(this.ribbit.level(), dryPos), Direction.UP);
    }

    private boolean hasEmptyCollision(BlockPos pos) {
        return this.ribbit.level().getBlockState(pos).getCollisionShape(this.ribbit.level(), pos).isEmpty();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.ribbit.isAutonomousAiPaused()) {
            return false;
        }

        if (this.waterPos == null || this.dryBlockPos == null || this.dryPos == null) {
            return false;
        }

        if (!this.ribbit.isDayActivityTime()) {
            return false;
        }

        if (this.ribbit.shouldLeaveUpperShelter()) {
            return false;
        }

        return this.ticksFishing < this.requiredFishTicks
                && (this.ribbit.distanceToSqr(this.dryPos) >= FISHING_SPOT_REACHED_DISTANCE * FISHING_SPOT_REACHED_DISTANCE || this.hasNearbyWater());
    }

    private boolean hasNearbyWater() {
        Iterable<BlockPos> nearbyPositions = BlockPos.betweenClosed(Mth.floor(this.ribbit.getX() - 1.5), Mth.floor(this.ribbit.getY() - 1.5), Mth.floor(this.ribbit.getZ() - 1.5), Mth.floor(this.ribbit.getX() + 1.5), this.ribbit.getBlockY(), Mth.floor(this.ribbit.getZ() + 1.5));

        for (BlockPos nearbyPos : nearbyPositions) {
            if (this.ribbit.level().getFluidState(nearbyPos).is(FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void tick() {
        if (this.waterPos == null || this.dryBlockPos == null || this.dryPos == null) {
            return;
        }

        float waterModifier = this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0f;
        this.ribbit.getNavigation().setSpeedModifier(this.speedModifier * waterModifier);

        if (!this.isAtFishingSpot()) {
            this.tryRetargetToCurrentShorePosition();
        }

        if (this.isAtFishingSpot()) {
            this.ticksWithoutApproachProgress = 0;
            this.bestApproachDistanceSqr = 0.0D;
            this.ticksFishing++;
            this.ribbit.setFishing(true);
            this.holdFishingPose();
        } else if (this.ribbit.distanceToSqr(this.dryPos) < CLOSE_APPROACH_DISTANCE * CLOSE_APPROACH_DISTANCE) {
            this.ribbit.setFishing(false);
            this.ribbit.getMoveControl().setWantedPosition(this.dryPos.x(), this.dryPos.y(), this.dryPos.z(), this.speedModifier * waterModifier);
        } else {
            this.ribbit.setFishing(false);
            this.ribbit.getNavigation().moveTo(this.dryPos.x(), this.dryPos.y(), this.dryPos.z(), this.speedModifier * waterModifier);
        }

        this.trackApproachProgress();
    }

    private boolean isAtFishingSpot() {
        return this.ribbit.distanceToSqr(this.dryPos) <= FISHING_SPOT_REACHED_DISTANCE * FISHING_SPOT_REACHED_DISTANCE;
    }

    private void holdFishingPose() {
        this.ribbit.getNavigation().stop();
        this.ribbit.setDeltaMovement(Vec3.ZERO);
        this.ribbit.setPos(this.dryPos.x(), this.dryPos.y(), this.dryPos.z());

        double dx = this.waterPos.getX() + 0.5D - this.ribbit.getX();
        double dz = this.waterPos.getZ() + 0.5D - this.ribbit.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0F / (float) Math.PI)) - 90.0F;
        this.ribbit.setYRot(yaw);
        this.ribbit.yBodyRot = yaw;
        this.ribbit.yHeadRot = yaw;
        this.ribbit.setXRot(0.0F);
        this.ribbit.getLookControl().setLookAt(this.waterPos.getX() + 0.5D, this.ribbit.getEyeY(), this.waterPos.getZ() + 0.5D);
    }

    private void tryRetargetToCurrentShorePosition() {
        BlockPos standingPos = this.ribbit.getOnPos();
        if (!this.isValidDryFishingPos(standingPos)) {
            return;
        }

        Optional<BlockPos> adjacentWaterPos = this.findAdjacentWaterPos(standingPos);
        if (adjacentWaterPos.isEmpty()) {
            return;
        }

        this.waterPos = adjacentWaterPos.get();
        this.dryBlockPos = standingPos.immutable();
        this.dryPos = Vec3.atBottomCenterOf(this.dryBlockPos.above());
        this.ticksWithoutApproachProgress = 0;
        this.bestApproachDistanceSqr = this.ribbit.distanceToSqr(this.dryPos);
    }

    private Optional<BlockPos> findAdjacentWaterPos(BlockPos dryPos) {
        for (Direction dir : Direction.Plane.HORIZONTAL.shuffledCopy(this.ribbit.getRandom())) {
            BlockPos testedWaterPos = dryPos.relative(dir);

            if (this.ribbit.level().getFluidState(testedWaterPos).is(FluidTags.WATER)
                    && this.hasEmptyCollision(testedWaterPos.above())) {
                return Optional.of(testedWaterPos);
            }
        }

        return Optional.empty();
    }

    private void trackApproachProgress() {
        if (this.dryBlockPos == null || this.waterPos == null || this.dryPos == null || this.isAtFishingSpot()) {
            return;
        }

        double distanceSqr = this.ribbit.distanceToSqr(this.dryPos);
        if (distanceSqr < this.bestApproachDistanceSqr - MIN_APPROACH_PROGRESS_DISTANCE_SQR) {
            this.bestApproachDistanceSqr = distanceSqr;
            this.ticksWithoutApproachProgress = 0;
            return;
        }

        this.ticksWithoutApproachProgress++;
        if (this.shouldKeepApproaching(distanceSqr)) {
            return;
        }

        this.abandonFishingSpot();
    }

    private boolean shouldKeepApproaching(double distanceSqr) {
        if (this.ticksWithoutApproachProgress >= MAX_APPROACH_TICKS_WITHOUT_PROGRESS) {
            return false;
        }

        return !this.ribbit.getNavigation().isDone()
                || distanceSqr < CLOSE_APPROACH_DISTANCE * CLOSE_APPROACH_DISTANCE
                || this.ticksWithoutApproachProgress < NAVIGATION_DONE_APPROACH_GRACE_TICKS;
    }

    private void abandonFishingSpot() {
        this.nextFishingSpotAttemptTick = this.ribbit.tickCount + FISHING_SPOT_RETRY_DELAY_TICKS;
        this.ribbit.getNavigation().stop();
        this.resetTarget();
    }

    private record FishingSpot(BlockPos waterPos, BlockPos dryPos) {
    }
}
