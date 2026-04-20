package com.ultramega.justenoughrecipesharing.fabric;

import com.ultramega.justenoughrecipesharing.network.ShareRecipePacket;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class ModInitializerImpl implements ModInitializer {
    @Override
    public void onInitialize() {
        this.registerPackets();
        this.registerPacketHandlers();
    }

    private void registerPackets() {
        PayloadTypeRegistry.clientboundPlay().register(ShareRecipePacket.TYPE, ShareRecipePacket.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ShareRecipePacket.TYPE, ShareRecipePacket.STREAM_CODEC);
    }

    private void registerPacketHandlers() {
        ServerPlayNetworking.registerGlobalReceiver(
            ShareRecipePacket.TYPE,
            (packet, ctx) -> ShareRecipePacket.handleServer(packet, ctx.server())
        );
    }
}
