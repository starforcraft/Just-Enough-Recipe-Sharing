package com.ultramega.justenoughrecipesharing.fabric;

import com.ultramega.justenoughrecipesharing.network.ShareRecipePacket;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ClientModInitializerImpl implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        this.registerPacketHandlers();
    }

    private void registerPacketHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(
            ShareRecipePacket.TYPE,
            (packet, ctx) -> ShareRecipePacket.handleClient(packet, ctx::player)
        );
    }
}
