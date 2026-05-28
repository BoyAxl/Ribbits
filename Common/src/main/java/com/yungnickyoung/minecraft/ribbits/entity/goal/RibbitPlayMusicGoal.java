package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.data.RibbitInstrument;
import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import com.yungnickyoung.minecraft.ribbits.module.RibbitInstrumentModule;
import com.yungnickyoung.minecraft.ribbits.module.RibbitProfessionModule;
import com.yungnickyoung.minecraft.ribbits.network.ServerNetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RibbitPlayMusicGoal extends Goal {
    private static final int BAND_REJOIN_COOLDOWN_TICKS = 600;
    private static final double BAND_SCAN_HORIZONTAL_RANGE = 64.0D;
    private static final double BAND_SCAN_VERTICAL_RANGE = 16.0D;
    private static final double BAND_PLAY_DISTANCE_SQR = 9.0D;
    private static final double BAND_MAX_DISTANCE_SQR = BAND_SCAN_HORIZONTAL_RANGE * BAND_SCAN_HORIZONTAL_RANGE;

    private final RibbitEntity ribbit;
    private final double speedModifier;
    private final int minRequiredPlayTicks;
    private final int maxRequiredPlayTicks;

    private int requiredPlayTicks;

    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private boolean resetRequested;
    private int bandJoinBlockedUntilTick;

    public RibbitPlayMusicGoal(RibbitEntity ribbit, double speedModifier, int minRequiredPlayTicks, int maxRequiredPlayTicks) {
        this.ribbit = ribbit;
        this.speedModifier = speedModifier;
        this.minRequiredPlayTicks = minRequiredPlayTicks;
        this.maxRequiredPlayTicks = maxRequiredPlayTicks;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.isNitwit()) {
            return false;
        }

        if (this.resetRequested) {
            this.clearResetRequest();
            return false;
        }

        if (this.ribbit.tickCount < this.bandJoinBlockedUntilTick) {
            return false;
        }

        if (this.ribbit.level().isDarkOutside()) {
            return false;
        }

        if (!this.ribbit.isStableOutdoorActivityPosition()) {
            return false;
        }

        this.syncMasterWithNearbyBand();
        if (this.clearFullBandMaster()) {
            return false;
        }

        return !this.ribbit.isUmbrellaFalling() && !this.ribbit.isDeadOrDying();
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.isNitwit()) {
            return false;
        }

        if (this.resetRequested) {
            return false;
        }

        if (this.ribbit.level().isDarkOutside()) {
            return false;
        }

        if (!this.ribbit.isStableOutdoorActivityPosition()) {
            return false;
        }

        if (this.isMasterTooFar()) {
            this.resetRequested = true;
            return false;
        }

        return !this.ribbit.isUmbrellaFalling()
                && !this.ribbit.isDeadOrDying()
                && (this.ribbit.getPlayingInstrument()
                || this.ribbit.getMasterRibbit() == null
                || !this.ribbit.getMasterRibbit().isBandFull());
    }

    @Override
    public void start() {
        this.ribbit.setInstrument(RibbitInstrumentModule.NONE);

        if (this.ribbit.getMasterRibbit() != null) {
            float waterModifier = this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0f;

            var path = this.ribbit.getNavigation().createPath(this.ribbit.getMasterRibbit(), 0);
            this.ribbit.getNavigation().moveTo(path, this.speedModifier * waterModifier);
        }

        if (this.ribbit.getMasterRibbit() == null) {
            this.ribbit.setMasterRibbit(this.ribbit);
        }

        this.requiredPlayTicks = this.ribbit.getRandom().nextInt(this.minRequiredPlayTicks, this.maxRequiredPlayTicks);
    }

    @Override
    public void stop() {
        this.detachFromBand();

        if (this.resetRequested) {
            this.clearResetRequest();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return this.ribbit.level().isDarkOutside() || this.resetRequested || this.isMasterTooFar() ||
                (this.ribbit.getLastHurtByMob() != null || this.ribbit.isFreezing() || this.ribbit.isOnFire()) ||
                this.ribbit.getTicksPlayingMusic() > this.requiredPlayTicks;
    }

    @Override
    public void tick() {
        if (this.ribbit.getMasterRibbit() == null || this.ribbit.getMasterRibbit().isDeadOrDying() || !this.ribbit.getMasterRibbit().getPlayingInstrument()) {
            this.syncMasterWithNearbyBand();
            if (this.clearFullBandMaster()) {
                return;
            }

            if (this.ribbit.getMasterRibbit() == null) {
                this.ribbit.setMasterRibbit(this.ribbit);
            }
        }

        RibbitEntity masterRibbit = this.ribbit.getMasterRibbit();
        if (masterRibbit == null || this.isMasterTooFar()) {
            this.resetRequested = true;
            return;
        }

        this.ribbit.getLookControl().setLookAt(masterRibbit, 30.0f, 30.0f);
        double d = this.ribbit.distanceToSqr(masterRibbit.getX(), masterRibbit.getY(), masterRibbit.getZ());
        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);

        float waterModifier = this.ribbit.isInWater() ? RibbitEntity.WATER_SPEED_MULTIPLIER : 1.0f;

        this.ribbit.getNavigation().setSpeedModifier(this.speedModifier * waterModifier);

        if (!this.ribbit.getPlayingInstrument() && this.ticksUntilNextPathRecalculation == 0 && (this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0 || masterRibbit.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0 || this.ribbit.getRandom().nextFloat() < 0.05f)) {
            this.pathedTargetX = masterRibbit.getX();
            this.pathedTargetY = masterRibbit.getY();
            this.pathedTargetZ = masterRibbit.getZ();
            this.ticksUntilNextPathRecalculation = 4 + this.ribbit.getRandom().nextInt(7);
            if (d > 1024.0) {
                this.ticksUntilNextPathRecalculation += 10;
            } else if (d > 256.0) {
                this.ticksUntilNextPathRecalculation += 5;
            }
            if (!this.ribbit.getNavigation().moveTo(masterRibbit, this.speedModifier * waterModifier)) {
                this.ticksUntilNextPathRecalculation += 15;
            }
            this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
        }

        if (!this.ribbit.getPlayingInstrument() && d <= BAND_PLAY_DISTANCE_SQR && !this.ribbit.isInWater()) {
            if (!this.ribbit.isStableOutdoorActivityPosition()) {
                this.resetRequested = true;
                return;
            }

            // Set the instrument.
            if (ribbit.getRibbitData().getInstrument() == RibbitInstrumentModule.NONE) {
                RibbitInstrument instrument = RibbitInstrumentModule.getRandomInstrument(masterRibbit.getBandMembers());

                if (instrument == null) {
                    return;
                }

                this.ribbit.setInstrument(instrument);

                this.ribbit.getMasterRibbit().addBandMember(this.ribbit.getRibbitData().getInstrument());
            }

            this.ribbit.getNavigation().stop();
            this.ribbit.setPlayingInstrument(true);
            this.ribbit.setTicksPlayingMusic(0);

            ServerNetworkHandler.onRibbitStartMusicGoal((ServerLevel) this.ribbit.level(), this.ribbit, masterRibbit);

            // If this ribbit is not the master ribbit, add it to the master ribbit's list of ribbits playing music
            masterRibbit.addRibbitToPlayingMusic(this.ribbit);
        }

        if (this.ribbit.getPlayingInstrument()) {
            this.ribbit.getNavigation().stop();

            if (d > BAND_PLAY_DISTANCE_SQR || this.ribbit.isInWater()) {
                this.ribbit.setPlayingInstrument(false);
                masterRibbit.removeBandMember(this.ribbit.getRibbitData().getInstrument());
                this.ribbit.setInstrument(RibbitInstrumentModule.NONE);
                masterRibbit.removeRibbitFromPlayingMusic(this.ribbit);
                this.ribbit.setTicksPlayingMusic(0);
                return;
            }

            // While playing music, only the master ribbit will send music packets to players
            if (this.ribbit.equals(masterRibbit)) {
                Set<Player> playersHearingMusic = new HashSet<>(this.ribbit.getPlayersHearingMusic());

                // Add any new players in range
                List<Player> playersInRange = this.ribbit.level().getEntitiesOfClass(Player.class, this.ribbit.getBoundingBox().inflate(32.0, 32.0, 32.0), EntitySelector.LIVING_ENTITY_STILL_ALIVE);
                for (Player player : playersInRange) {
                    if (!playersHearingMusic.contains(player)) {
                        playersHearingMusic.add(player);
                        ServerNetworkHandler.onPlayerEnterBandRange((ServerPlayer) player, (ServerLevel) this.ribbit.level(), this.ribbit);
                    }
                }

                // Remove any players no longer in the world or out of range
                playersHearingMusic.removeIf(player -> {
                    if (player.isRemoved() || !playersInRange.contains(player)) {
                        ServerNetworkHandler.onPlayerExitBandRange((ServerPlayer) player, (ServerLevel) this.ribbit.level(), this.ribbit);
                        return true;
                    }
                    return false;
                });

                this.ribbit.setPlayersHearingMusic(playersHearingMusic);
            }

            this.ribbit.setTicksPlayingMusic(this.ribbit.getTicksPlayingMusic() + 1);
        }
    }

    public void resetTarget() {
        this.resetTarget(false);
    }

    public void resetTarget(boolean blockBandRejoin) {
        this.resetRequested = true;
        if (blockBandRejoin) {
            this.bandJoinBlockedUntilTick = this.ribbit.tickCount + BAND_REJOIN_COOLDOWN_TICKS;
        }
        this.detachFromBand();
        this.ribbit.getNavigation().stop();
        this.pathedTargetX = 0.0D;
        this.pathedTargetY = 0.0D;
        this.pathedTargetZ = 0.0D;
        this.ticksUntilNextPathRecalculation = 0;
    }

    private void syncMasterWithNearbyBand() {
        this.ribbit.level()
                .getEntitiesOfClass(RibbitEntity.class, this.ribbit.getBoundingBox().inflate(BAND_SCAN_HORIZONTAL_RANGE, BAND_SCAN_VERTICAL_RANGE, BAND_SCAN_HORIZONTAL_RANGE))
                .stream()
                .filter(RibbitEntity::getPlayingInstrument)
                .filter(RibbitEntity::isStableOutdoorActivityPosition)
                .forEach(candidate -> {
                    if (candidate.getMasterRibbit() != null) {
                        this.ribbit.setMasterRibbit(candidate.getMasterRibbit());
                    }
                });
    }

    private boolean clearFullBandMaster() {
        if (this.ribbit.getMasterRibbit() != null && this.ribbit.getMasterRibbit().isBandFull()) {
            this.ribbit.setMasterRibbit(null);
            return true;
        }

        return false;
    }

    private boolean isMasterTooFar() {
        RibbitEntity masterRibbit = this.ribbit.getMasterRibbit();
        return masterRibbit != null
                && masterRibbit != this.ribbit
                && this.ribbit.distanceToSqr(masterRibbit) > BAND_MAX_DISTANCE_SQR;
    }

    private void detachFromBand() {
        RibbitEntity masterRibbit = this.ribbit.getMasterRibbit();
        if (masterRibbit != null && masterRibbit != this.ribbit) {
            masterRibbit.removeRibbitFromPlayingMusic(this.ribbit);
            masterRibbit.removeBandMember(this.ribbit.getRibbitData().getInstrument());
        }

        if (this.ribbit.isMasterRibbit()) {
            this.ribbit.findNewMasterRibbit();
        }

        this.ribbit.setMasterRibbit(null);
        this.ribbit.setPlayingInstrument(false);
        this.ribbit.setTicksPlayingMusic(0);
        this.ribbit.setInstrument(RibbitInstrumentModule.NONE);
    }

    private void clearResetRequest() {
        this.resetRequested = false;
    }

    private boolean isNitwit() {
        return this.ribbit.getRibbitData().getProfession().equals(RibbitProfessionModule.NITWIT);
    }
}
