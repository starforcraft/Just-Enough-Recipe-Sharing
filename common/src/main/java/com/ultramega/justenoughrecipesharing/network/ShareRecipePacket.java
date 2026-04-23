package com.ultramega.justenoughrecipesharing.network;

import com.ultramega.justenoughrecipesharing.Constants;
import com.ultramega.justenoughrecipesharing.client.ClientRecipeShareManager;
import com.ultramega.justenoughrecipesharing.platform.Services;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.common.Internal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import static com.ultramega.justenoughrecipesharing.network.FuelPayload.encodeFuel;

public record ShareRecipePacket(Identifier recipeTypeUid, Tag recipeTag, List<FocusPayload> focuses, String sharerName) implements CustomPacketPayload {
    public static final Type<ShareRecipePacket> TYPE = new Type<>(Constants.modLoc("share_recipe_data"));

    public static final StreamCodec<ByteBuf, ShareRecipePacket> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, ShareRecipePacket::recipeTypeUid,
        ByteBufCodecs.TAG, ShareRecipePacket::recipeTag,
        FocusPayload.STREAM_CODEC.apply(ByteBufCodecs.list()), ShareRecipePacket::focuses,
        ByteBufCodecs.stringUtf8(16), ShareRecipePacket::sharerName,
        ShareRecipePacket::new
    );

    public static final String KIND_KEY = "kind";
    public static final String DATA_KEY = "data";

    private static final String KIND_NORMAL = "normal";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Nullable
    public static <T> ShareRecipePacket encode(final IJeiHelpers jeiHelpers,
                                               final IRecipeCategory<T> category,
                                               final Identifier recipeTypeUid,
                                               final T recipe,
                                               final List<FocusPayload> focuses,
                                               @Nullable final Player player) {
        // Unfortunately fuel recipes need to be handled separately
        if (recipe instanceof IJeiFuelingRecipe fuelingRecipe) {
            return encodeFuel(jeiHelpers, recipeTypeUid, fuelingRecipe, focuses, player);
        }

        final ICodecHelper codecHelper = jeiHelpers.getCodecHelper();
        final Codec<T> codec = category.getCodec(codecHelper, Internal.getJeiRuntime().getRecipeManager());

        final DataResult<Tag> encoded = codec.encodeStart(NbtOps.INSTANCE, recipe)
            .map(tag -> tag);
        if (encoded.error().isPresent()) {
            Constants.LOG.warn("Failed to encode JEI recipe {}: {}", recipeTypeUid, encoded.error().get().message());
            return null;
        }

        return new ShareRecipePacket(recipeTypeUid, wrapPayload(KIND_NORMAL, encoded.result().orElseThrow()), List.copyOf(focuses), getSharerName(player));
    }

    public static List<FocusPayload> captureDisplayedFocuses(final IRecipeSlotsView slotsView, final IJeiHelpers jeiHelpers) {
        final List<FocusPayload> result = new ArrayList<>();
        final ICodecHelper codecHelper = jeiHelpers.getCodecHelper();

        for (final IRecipeSlotView slotView : slotsView.getSlotViews()) {
            slotView.getDisplayedIngredient().ifPresent(typed -> {
                final Tag ingredientTag = codecHelper.getTypedIngredientCodec()
                    .codec()
                    .encodeStart(NbtOps.INSTANCE, typed)
                    .getOrThrow(error -> new IllegalStateException("Failed to encode JEI focus ingredient: " + error));

                result.add(new FocusPayload(slotView.getRole(), ingredientTag));
            });
        }

        return List.copyOf(result);
    }

    public static CompoundTag wrapPayload(final String kind, final Tag data) {
        final CompoundTag root = new CompoundTag();
        root.putString(KIND_KEY, kind);
        root.put(DATA_KEY, data);
        return root;
    }

    public static Tag unwrapNormalPayload(final Tag tag) {
        if (!(tag instanceof CompoundTag compound) || !compound.contains(KIND_KEY)) {
            return tag;
        }

        return compound.get(DATA_KEY);
    }

    public static String getSharerName(@Nullable final Player player) {
        if (player != null) {
            final String name = player.getName().getString();
            // Safety in case some modded heuristics allows player names longer than 16 chars
            return name.substring(0, Math.min(16, name.length()));
        }
        return Component.translatable("misc.justenoughrecipesharing.unknown").getString();
    }

    public static void handleServer(final ShareRecipePacket payload, @Nullable final MinecraftServer server) {
        Services.PLATFORM.sendPacketToAllPlayers(server, payload);
    }

    public static void handleClient(final ShareRecipePacket payload, final PacketContext ctx) {
        ClientRecipeShareManager.receive(payload, ctx.getPlayer());
    }
}
