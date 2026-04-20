package com.ultramega.justenoughrecipesharing.neoforge.platform;

import com.ultramega.justenoughrecipesharing.platform.services.IPlatformHelper;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.PacketDistributor;

public class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public <T extends CustomPacketPayload> void sendPacketToServer(final T packet) {
        ClientPacketDistributor.sendToServer(packet);
    }

    @Override
    public <T extends CustomPacketPayload> void sendPacketToAllPlayers(final MinecraftServer server, final T packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }
}
