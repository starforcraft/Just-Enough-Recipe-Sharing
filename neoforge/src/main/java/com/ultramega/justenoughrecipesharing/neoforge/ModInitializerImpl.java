package com.ultramega.justenoughrecipesharing.neoforge;

import com.ultramega.justenoughrecipesharing.network.ShareRecipePacket;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import static com.ultramega.justenoughrecipesharing.Constants.MOD_ID;

@Mod(MOD_ID)
public class ModInitializerImpl {
    public ModInitializerImpl(final IEventBus eventBus) {
        eventBus.addListener(this::registerPackets);
    }

    private void registerPackets(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);

        registrar.playBidirectional(
            ShareRecipePacket.TYPE,
            ShareRecipePacket.STREAM_CODEC,
            (packet, ctx) -> ShareRecipePacket.handleServer(packet, null),
            (packet, ctx) -> ShareRecipePacket.handleClient(packet, ctx::player)
        );
    }
}
