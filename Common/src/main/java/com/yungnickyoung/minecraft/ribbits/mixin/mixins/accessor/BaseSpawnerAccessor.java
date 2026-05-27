package com.yungnickyoung.minecraft.ribbits.mixin.mixins.accessor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BaseSpawner.class)
public interface BaseSpawnerAccessor {
    @Invoker("setNextSpawnData")
    void ribbits$setNextSpawnData(@Nullable Level level, BlockPos pos, SpawnData spawnData);
}
