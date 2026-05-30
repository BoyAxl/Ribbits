package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import net.minecraft.world.entity.ai.goal.PanicGoal;

public class RibbitPanicGoal extends PanicGoal {
    private final RibbitEntity ribbit;

    public RibbitPanicGoal(RibbitEntity ribbit, double speedModifier) {
        super(ribbit, speedModifier);
        this.ribbit = ribbit;
    }

    @Override
    public boolean canUse() {
        return !this.ribbit.isAutonomousAiPaused() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !this.ribbit.isAutonomousAiPaused() && super.canContinueToUse();
    }
}
