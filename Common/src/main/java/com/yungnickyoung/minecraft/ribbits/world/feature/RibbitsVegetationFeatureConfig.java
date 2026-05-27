package com.yungnickyoung.minecraft.ribbits.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record RibbitsVegetationFeatureConfig(Optional<BlockStateProvider> onSolidStateProvider,
                                             Optional<BlockStateProvider> onLiquidStateProvider,
                                             List<BlockState> cannotPlaceOn,
                                             int tries,
                                             int xzSpread,
                                             int ySpread)
        implements FeatureConfiguration {

    public static final Codec<RibbitsVegetationFeatureConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    BlockStateProvider.CODEC.optionalFieldOf("on_solid_state_provider").forGetter(config -> config.onSolidStateProvider),
                    BlockStateProvider.CODEC.optionalFieldOf("on_liquid_state_provider").forGetter(config -> config.onLiquidStateProvider),
                    BlockState.CODEC.listOf().optionalFieldOf("cannot_place_on", new ArrayList<>()).forGetter(config -> config.cannotPlaceOn),
                    Codec.INT.optionalFieldOf("tries", 1).forGetter(config -> config.tries),
                    Codec.INT.optionalFieldOf("xz_spread", 0).forGetter(config -> config.xzSpread),
                    Codec.INT.optionalFieldOf("y_spread", 0).forGetter(config -> config.ySpread))
            .apply(instance, instance.stable(RibbitsVegetationFeatureConfig::new)));
}
