package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;

public class RibbitLookAtTradingPlayerGoal extends LookAtPlayerGoal {
    private final RibbitEntity ribbit;

    public RibbitLookAtTradingPlayerGoal(RibbitEntity ribbit) {
        super(ribbit, Player.class, 8.0F);
        this.ribbit = ribbit;
    }

    @Override
    public boolean canUse() {
        if (this.ribbit.isTrading()) {
            this.lookAt = this.ribbit.getTradingPlayer();
            return this.lookAt != null;
        }

        return false;
    }
}
