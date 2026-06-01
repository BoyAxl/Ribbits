package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import com.yungnickyoung.minecraft.ribbits.module.SoundModule;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RibbitApplyBuffGoal extends Goal {
    private static final int TICKS_UNTIL_BUFF_APPLIED = 22;
    static final double TRIGGER_RANGE = 8.0D;

    private final RibbitEntity ribbit;
    private final double range;
    private final int cooldownTicks;

    /**
     * Map of effects to duration, in ticks.
     */
    private final Map<Holder<MobEffect>, Integer> effects;

    private int ticksSinceStart;
    private Player triggeringPlayer;
    private boolean cancelledBeforeBuff;

    public RibbitApplyBuffGoal(RibbitEntity ribbit, double range, int cooldownTicks) {
        this.ribbit = ribbit;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.effects = new HashMap<>();
        this.effects.put(MobEffects.REGENERATION, 1200);
        this.effects.put(MobEffects.RESISTANCE, 2400);
        this.effects.put(MobEffects.STRENGTH, 2400);
        this.effects.put(MobEffects.JUMP_BOOST, 2400);
        this.effects.put(MobEffects.HASTE, 2400);
        this.effects.put(MobEffects.HEALTH_BOOST, 2400);

        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.ribbit.isAutonomousAiPaused() || this.ribbit.isLocalNightShelterWaitActive()) {
            return false;
        }

        this.triggeringPlayer = null;
        if (this.ribbit.getBuffCooldown() > 0 || !this.canStandStillForBuff()) {
            return false;
        }

        if (this.ribbit.isShelterNight() && !this.ribbit.canCastBuffFromCurrentNightRestingPosition()) {
            return false;
        }

        ServerLevel serverLevel = getServerLevel(this.ribbit.level());
        this.triggeringPlayer = this.getTriggeringPlayer(serverLevel);
        return this.triggeringPlayer != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.ribbit.isAutonomousAiPaused()
                && !this.ribbit.isLocalNightShelterWaitActive()
                && this.ticksSinceStart >= 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!this.canStandStillForBuff()) {
            this.cancelBeforeBuff();
            return;
        }

        this.faceTriggeringPlayer();

        if (this.ticksSinceStart >= TICKS_UNTIL_BUFF_APPLIED) {
            this.applyBuffs();
        } else if (this.ticksSinceStart >= 0) {
            this.ticksSinceStart++;
        }
    }

    @Override
    public void start() {
        this.ticksSinceStart = 0;
        this.cancelledBeforeBuff = false;
        this.ribbit.setSeatedBuffing(this.ribbit.shouldUseSeatedBuffingPose());
        this.ribbit.getNavigation().stop();
        this.faceTriggeringPlayer();
        this.ribbit.setBuffing(true);
        this.ribbit.playSound(SoundModule.ENTITY_RIBBIT_MAGIC.get());
    }

    @Override
    public void stop() {
        if (!this.cancelledBeforeBuff) {
            this.ribbit.setBuffCooldown(this.cooldownTicks);
        }
        this.ribbit.setBuffing(false);
        this.triggeringPlayer = null;
        this.cancelledBeforeBuff = false;
        this.ticksSinceStart = 0;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    private void applyBuffs() {
        this.ticksSinceStart = -1;

        ServerLevel serverLevel = getServerLevel(this.ribbit.level());
        List<Player> nearbyPlayers = this.getNearbyPlayers(serverLevel);

        Holder<MobEffect> randomEffect = this.effects.keySet().stream().toList().get(this.ribbit.getRandom().nextInt(this.effects.size()));
        int effectDuration = this.effects.get(randomEffect);
        for (Player player : nearbyPlayers) {
            player.addEffect(new MobEffectInstance(randomEffect, effectDuration, 0));
        }
    }

    private void cancelBeforeBuff() {
        this.cancelledBeforeBuff = true;
        this.ticksSinceStart = -1;
        this.ribbit.setBuffing(false);
    }

    private List<Player> getNearbyPlayers(ServerLevel serverLevel) {
        return this.getNearbyPlayers(serverLevel, this.range);
    }

    private Player getTriggeringPlayer(ServerLevel serverLevel) {
        return this.getNearbyPlayers(serverLevel, TRIGGER_RANGE).stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(this.ribbit)))
                .orElse(null);
    }

    private List<Player> getNearbyPlayers(ServerLevel serverLevel, double range) {
        return serverLevel.getNearbyPlayers(
                TargetingConditions.forNonCombat().range(range),
                this.ribbit,
                this.ribbit.getBoundingBox().inflate(range, 5.0d, range));
    }

    private boolean canStandStillForBuff() {
        return !this.ribbit.isUmbrellaFalling()
                && (this.ribbit.onGround()
                || this.ribbit.isInWater()
                || this.ribbit.getSeatedBuffing()
                || this.ribbit.shouldUseSeatedBuffingPose());
    }

    private void faceTriggeringPlayer() {
        if (this.triggeringPlayer == null || !this.triggeringPlayer.isAlive()) {
            return;
        }

        double dx = this.triggeringPlayer.getX() - this.ribbit.getX();
        double dz = this.triggeringPlayer.getZ() - this.ribbit.getZ();
        if (dx * dx + dz * dz > 1.0E-4D) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0F / (float) Math.PI)) - 90.0F;
            this.ribbit.setYRot(yaw);
            this.ribbit.yBodyRot = yaw;
            this.ribbit.yHeadRot = yaw;
            this.ribbit.setXRot(0.0F);
        }

        this.ribbit.getLookControl().setLookAt(this.triggeringPlayer, 30.0F, 30.0F);
    }
}
