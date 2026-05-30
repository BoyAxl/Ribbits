package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;

public class RibbitLookAtPlayerGoal extends LookAtPlayerGoal {
    private final RibbitEntity ribbit;

    public RibbitLookAtPlayerGoal(RibbitEntity ribbit, Class<? extends LivingEntity> lookAtType, float lookDistance) {
        super(ribbit, lookAtType, lookDistance);
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
