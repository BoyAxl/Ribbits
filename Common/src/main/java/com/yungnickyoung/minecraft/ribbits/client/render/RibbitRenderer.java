package com.yungnickyoung.minecraft.ribbits.client.render;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.yungnickyoung.minecraft.ribbits.client.model.RibbitModel;
import com.yungnickyoung.minecraft.ribbits.data.RibbitData;
import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import com.yungnickyoung.minecraft.ribbits.module.DataTicketModule;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

public class RibbitRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<RibbitEntity, R> {
    public RibbitRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new RibbitModel());
    }

    @Override
    public void addRenderData(RibbitEntity animatable, Void relatedObject, R renderState, float partialTick) {
        RibbitData data = animatable.getRibbitData();
        renderState.addGeckolibData(DataTicketModule.DT_RIBBIT_DATA, data);
        renderState.addGeckolibData(DataTicketModule.DT_PLAYING_INSTRUMENT, animatable.getPlayingInstrument());
        renderState.addGeckolibData(DataTicketModule.DT_UMBRELLA_FALLING, animatable.isUmbrellaFalling());
        renderState.addGeckolibData(DataTicketModule.DT_IN_RAIN, animatable.isInRain());
        renderState.addGeckolibData(DataTicketModule.DT_IS_PRIDE_RIBBIT, animatable.isPrideRibbit());
    }

    @Override
    public RenderType getRenderType(R renderState, Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    @Override
    public Identifier getTextureLocation(R renderState) {
        return super.getTextureLocation(renderState);
    }

    @Override
    public float getMotionAnimThreshold(RibbitEntity animatable) {
        return 0.0005f;
    }
}
