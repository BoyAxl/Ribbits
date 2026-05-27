package com.yungnickyoung.minecraft.ribbits.network;

import com.yungnickyoung.minecraft.ribbits.RibbitsCommon;
import com.yungnickyoung.minecraft.ribbits.client.sound.PlayerInstrumentSoundInstance;
import com.yungnickyoung.minecraft.ribbits.client.sound.RibbitInstrumentSoundInstance;
import com.yungnickyoung.minecraft.ribbits.client.supporters.RibbitOptionsJSON;
import com.yungnickyoung.minecraft.ribbits.client.supporters.SupportersListClient;
import com.yungnickyoung.minecraft.ribbits.data.RibbitInstrument;
import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import com.yungnickyoung.minecraft.ribbits.mixin.interfaces.client.ISoundManagerDuck;
import com.yungnickyoung.minecraft.ribbits.mixin.mixins.client.accessor.ClientLevelAccessor;
import com.yungnickyoung.minecraft.ribbits.module.RibbitInstrumentModule;
import com.yungnickyoung.minecraft.ribbits.module.SoundModule;
import com.yungnickyoung.minecraft.ribbits.network.payload.*;
import com.yungnickyoung.minecraft.ribbits.platform.PlatformHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ClientNetworkHandler {
    private static final Map<UUID, List<Consumer<Entity>>> PENDING_ENTITY_ACTIONS = new HashMap<>();

    public static void onEntityLoad(Entity entity) {
        List<Consumer<Entity>> actions = PENDING_ENTITY_ACTIONS.remove(entity.getUUID());
        if (actions == null) return;

        for (Consumer<Entity> action : actions) {
            action.accept(entity);
        }
    }

    public static void clearPendingActions() {
        PENDING_ENTITY_ACTIONS.clear();
    }

    private static void queueOrExecute(UUID entityId, Consumer<Entity> action) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Entity entity = ((ClientLevelAccessor) client.level).callGetEntities().get(entityId);
        if (entity == null) {
            PENDING_ENTITY_ACTIONS.computeIfAbsent(entityId, ignored -> new ArrayList<>()).add(action);
            return;
        }

        action.accept(entity);
    }

    public static void handleStartMusicSingleS2C(RibbitStartMusicSinglePayload payload) {
        playRibbitMusic(payload.ribbitUUID(), payload.instrumentId(), payload.tickOffset(), "StartMusicSingle");
    }

    public static void handleStopMusicSingleS2C(RibbitStopMusicSinglePayload payload) {
        queueOrExecute(payload.ribbitUUID(), entity -> Minecraft.getInstance().execute(() ->
                ((ISoundManagerDuck) Minecraft.getInstance().getSoundManager()).ribbits$stopRibbitsMusic(payload.ribbitUUID())));
    }

    public static void handleStartMusicAllS2C(RibbitStartMusicAllPayload payload) {
        if (payload.ribbitUUIDs().size() != payload.instrumentIds().size()) {
            RibbitsCommon.LOGGER.error("StartMusicAll: {} ribbits != {} instruments",
                    payload.ribbitUUIDs().size(), payload.instrumentIds().size());
            return;
        }
        for (int i = 0; i < payload.ribbitUUIDs().size(); i++) {
            playRibbitMusic(payload.ribbitUUIDs().get(i), payload.instrumentIds().get(i), payload.tickOffset(), "StartMusicAll");
        }
    }

    public static void handleStartHearingMaracaS2C(StartHearingMaracaPayload payload) {
        queueOrExecute(payload.performerUUID(), performer -> {
            if (!(performer instanceof Player player)) {
                RibbitsCommon.LOGGER.error("StartMaraca: performer {} invalid", payload.performerUUID());
                return;
            }

            Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().play(
                    new PlayerInstrumentSoundInstance(player, -1, SoundModule.MUSIC_MARACA.get())));
        });
    }

    public static void handleStopHearingMaracaS2C(StopHearingMaracaPayload payload) {
        queueOrExecute(payload.performerUUID(), performer -> Minecraft.getInstance().execute(() ->
                ((ISoundManagerDuck) Minecraft.getInstance().getSoundManager()).ribbits$stopMaraca(payload.performerUUID())));
    }

    public static void handleRequestSupporterHatStateS2C(RequestSupporterHatStatePayload payload) {
        SupportersListClient.clear();
        payload.enabledSupporterHatPlayers().forEach(uuid -> SupportersListClient.toggleSupporterHat(uuid, true));
        notifyServerOfSupporterHatState(RibbitOptionsJSON.get().isSupporterHatEnabled());
    }

    public static void handleToggleSupporterHatS2C(ToggleSupporterHatPayloadS2C payload) {
        SupportersListClient.toggleSupporterHat(payload.playerUUID(), payload.enabled());
    }

    public static void notifyServerOfSupporterHatState(boolean enabled) {
        UUID playerUUID = Minecraft.getInstance().getUser().getProfileId();
        if (Minecraft.getInstance().getConnection() == null) return;
        PlatformHelper.sendToServer(new ToggleSupporterHatPayloadC2S(playerUUID, enabled));
    }

    private static void playRibbitMusic(UUID ribbitUUID, Identifier instrumentId, int tickOffset, String source) {
        RibbitInstrument instrument = RibbitInstrumentModule.getInstrument(instrumentId);
        if (instrument == null || instrument == RibbitInstrumentModule.NONE) {
            RibbitsCommon.LOGGER.error("{}: invalid instrument {}", source, instrumentId);
            return;
        }

        SoundEvent event = instrument.soundEvent();
        queueOrExecute(ribbitUUID, entity -> {
            if (!(entity instanceof RibbitEntity ribbit)) {
                RibbitsCommon.LOGGER.error("{}: entity {} is not a ribbit", source, ribbitUUID);
                return;
            }

            Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().play(
                    new RibbitInstrumentSoundInstance(ribbit, tickOffset, event)));
        });
    }
}
