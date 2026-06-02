package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class RibbitStrollGoal extends RandomStrollGoal {
    private static final int OUTDOOR_TARGET_ATTEMPTS = 12;
    private static final int HORIZONTAL_OFFSET_ATTEMPTS = 16;

    private final int dayHomeRange;
    private final RibbitEntity ribbit;

    public RibbitStrollGoal(RibbitEntity mob, double speedModifier, int dayHomeRange) {
        super(mob, speedModifier);
        this.ribbit = mob;
        this.dayHomeRange = dayHomeRange;

        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    public RibbitStrollGoal(RibbitEntity mob, double speedModifier, int interval, int dayHomeRange) {
        super(mob, speedModifier, interval);
        this.ribbit = mob;
        this.dayHomeRange = dayHomeRange;
    }

    public RibbitStrollGoal(RibbitEntity mob, double speedModifier, int interval, boolean checkNoActionTime, int dayHomeRange) {
        super(mob, speedModifier, interval, checkNoActionTime);
        this.ribbit = mob;
        this.dayHomeRange = dayHomeRange;
    }

    @Override
    public void start() {
        float waterModifier = this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0f;
        this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier * waterModifier);
    }

    @Override
    public boolean canUse() {
        if (this.ribbit.isAutonomousAiPaused() || this.ribbit.isLocalNightShelterWaitActive()) {
            return false;
        }

        if (this.ribbit.isShelterNight() && !this.ribbit.canUpdateNightShelterNavigationNow()) {
            return false;
        }

        this.ribbit.clearAutomaticBedHomeIfTooFar();

        if (this.ribbit.isShelterNight() && this.ribbit.hasUsableHomePosition()) {
            return false;
        }

        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !this.ribbit.isAutonomousAiPaused()
                && !this.ribbit.isLocalNightShelterWaitActive()
                && (!this.ribbit.isShelterNight() || this.ribbit.canUpdateNightShelterNavigationNow())
                && super.canContinueToUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        super.tick();

        float waterModifier = this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0f;
        this.ribbit.getNavigation().setSpeedModifier(this.speedModifier * waterModifier);
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        Vec3 firstCandidate = null;
        for (int attempt = 0; attempt < OUTDOOR_TARGET_ATTEMPTS; attempt++) {
            Vec3 position = this.getRandomDayHomePosition();
            if (firstCandidate == null) {
                firstCandidate = position;
            }

            if (this.ribbit.isStableOutdoorActivityPosition(BlockPos.containing(position))) {
                return position;
            }
        }

        return firstCandidate;
    }

    private Vec3 getRandomDayHomePosition() {
        BlockPos horizontalOffset = this.getRandomHorizontalHomeOffset();
        int yOffset = this.getRandomHomeRangeOffset();

        BlockPos dayHomePosition = this.ribbit.getStrollAnchorPosition();
        return new Vec3(horizontalOffset.getX() + dayHomePosition.getX(),
                yOffset + dayHomePosition.getY(),
                horizontalOffset.getZ() + dayHomePosition.getZ());
    }

    private BlockPos getRandomHorizontalHomeOffset() {
        int maxDistanceSqr = this.dayHomeRange * this.dayHomeRange;
        for (int attempt = 0; attempt < HORIZONTAL_OFFSET_ATTEMPTS; attempt++) {
            int xOffset = this.getRandomHomeRangeOffset();
            int zOffset = this.getRandomHomeRangeOffset();
            if (xOffset * xOffset + zOffset * zOffset <= maxDistanceSqr) {
                return new BlockPos(xOffset, 0, zOffset);
            }
        }

        RandomSource random = this.mob.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int xOffset = (int) (Math.cos(angle) * this.dayHomeRange);
        int zOffset = (int) (Math.sin(angle) * this.dayHomeRange);
        return new BlockPos(xOffset, 0, zOffset);
    }

    private int getRandomHomeRangeOffset() {
        return this.mob.getRandom().nextInt(this.dayHomeRange * 2 + 1) - this.dayHomeRange;
    }
}
