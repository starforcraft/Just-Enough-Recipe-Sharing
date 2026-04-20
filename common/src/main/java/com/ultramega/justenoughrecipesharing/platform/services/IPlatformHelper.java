package com.ultramega.justenoughrecipesharing.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

public interface IPlatformHelper {
    <T extends CustomPacketPayload> void sendPacketToServer(T packet);

    <T extends CustomPacketPayload> void sendPacketToAllPlayers(@Nullable MinecraftServer server, T packet);
}
