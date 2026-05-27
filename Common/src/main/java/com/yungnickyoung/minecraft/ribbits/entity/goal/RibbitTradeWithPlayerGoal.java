package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

public class RibbitTradeWithPlayerGoal extends Goal {
    private final RibbitEntity ribbit;

    public RibbitTradeWithPlayerGoal(RibbitEntity ribbit) {
        this.ribbit = ribbit;
        this.setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return this.canTradeWithPlayer();
    }

    @Override
    public boolean canContinueToUse() {
        // TODO(post-1.0): Revisit whether trading Ribbits should stop trading and panic when hurt.
        return this.canTradeWithPlayer();
    }

    @Override
    public void start() {
        this.ribbit.getNavigation().stop();
    }

    @Override
    public void stop() {
        this.ribbit.setTradingPlayer(null);
    }

    private boolean canTradeWithPlayer() {
        Player player = this.ribbit.getTradingPlayer();
        return player != null
                && this.ribbit.isAlive()
                && !this.ribbit.isSleeping()
                && player.isAlive()
                && this.ribbit.stillValid(player);
    }
}
