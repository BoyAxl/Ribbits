package com.yungnickyoung.minecraft.ribbits.client.model;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.yungnickyoung.minecraft.ribbits.RibbitsCommon;
import com.yungnickyoung.minecraft.ribbits.data.RibbitData;
import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import com.yungnickyoung.minecraft.ribbits.module.DataTicketModule;
import com.yungnickyoung.minecraft.ribbits.module.RibbitInstrumentModule;
import com.yungnickyoung.minecraft.ribbits.module.RibbitProfessionModule;
import net.minecraft.resources.Identifier;

public class RibbitModel extends GeoModel<RibbitEntity> {
    private static final Identifier TEXTURE = RibbitsCommon.id("textures/entity/ribbit.png");
    private static final Identifier ANIMATIONS = RibbitsCommon.id("ribbit");
    private static final Identifier PRIDE_MODEL = RibbitsCommon.id("geo/pride_ribbit");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        RibbitData data = renderState.getGeckolibData(DataTicketModule.DT_RIBBIT_DATA);
        if (data == null || data.getProfession() == null) {
            return RibbitProfessionModule.NITWIT.modelLocation();
        }

        boolean playingInstrument = Boolean.TRUE.equals(renderState.getGeckolibData(DataTicketModule.DT_PLAYING_INSTRUMENT));
        if (playingInstrument && data.getInstrument() != null && !RibbitInstrumentModule.NONE.equals(data.getInstrument())) {
            return data.getInstrument().modelId();
        }

        boolean umbrellaFalling = Boolean.TRUE.equals(renderState.getGeckolibData(DataTicketModule.DT_UMBRELLA_FALLING));
        boolean inRain = Boolean.TRUE.equals(renderState.getGeckolibData(DataTicketModule.DT_IN_RAIN));
        if ((umbrellaFalling || inRain) && data.getUmbrellaType() != null) {
            return RibbitsCommon.id("geo/umbrella/" + data.getProfession().id().getPath() + "/" + data.getUmbrellaType().modelLocationSuffix());
        }

        if (Boolean.TRUE.equals(renderState.getGeckolibData(DataTicketModule.DT_IS_PRIDE_RIBBIT))) {
            return PRIDE_MODEL;
        }

        return data.getProfession().modelLocation();
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(RibbitEntity animatable) {
        return ANIMATIONS;
    }
}
