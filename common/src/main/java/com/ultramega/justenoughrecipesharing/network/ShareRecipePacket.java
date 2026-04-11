package com.ultramega.justenoughrecipesharing.network;

import com.ultramega.justenoughrecipesharing.Constants;
import com.ultramega.justenoughrecipesharing.client.ClientRecipeShareManager;
import com.ultramega.justenoughrecipesharing.platform.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.common.Internal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public record ShareRecipePacket(Identifier recipeTypeUid, Tag recipeTag) implements CustomPacketPayload {
    public static final Type<ShareRecipePacket> TYPE = new Type<>(Constants.modLoc("share_recipe_data"));

    public static final StreamCodec<ByteBuf, ShareRecipePacket> STREAM_CODEC = StreamCodec.composite(
        Identifier.STREAM_CODEC, ShareRecipePacket::recipeTypeUid,
        ByteBufCodecs.TAG, ShareRecipePacket::recipeTag,
        ShareRecipePacket::new
    );

    private static final String KIND_KEY = "kind";
    private static final String DATA_KEY = "data";

    private static final String KIND_NORMAL = "normal";
    private static final String KIND_FUEL = "fuel";

    private static final String INPUTS_KEY = "inputs";
    private static final String BURN_TIME_KEY = "burn_time";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Nullable
    public static <T> ShareRecipePacket encode(final IJeiHelpers jeiHelpers, final IRecipeCategory<T> category, final Identifier recipeTypeUid, final T recipe) {
        // Unfortunately fuel recipes need to be handled separately
        if (recipe instanceof IJeiFuelingRecipe fuelingRecipe) {
            return encodeFuel(jeiHelpers, recipeTypeUid, fuelingRecipe);
        }

        final ICodecHelper codecHelper = jeiHelpers.getCodecHelper();
        final Codec<T> codec = category.getCodec(codecHelper, Internal.getJeiRuntime().getRecipeManager());

        final DataResult<Tag> encoded = codec.encodeStart(NbtOps.INSTANCE, recipe)
            .map(tag -> tag);
        if (encoded.error().isPresent()) {
            Constants.LOG.warn("Failed to encode JEI recipe {}: {}", recipeTypeUid, encoded.error().get().message());
            return null;
        }

        return new ShareRecipePacket(recipeTypeUid, wrapPayload(KIND_NORMAL, encoded.result().orElseThrow()));
    }

    @Nullable
    private static ShareRecipePacket encodeFuel(final IJeiHelpers jeiHelpers, final Identifier recipeTypeUid, final IJeiFuelingRecipe recipe) {
        final Codec<ITypedIngredient<ItemStack>> itemStackCodec = jeiHelpers.getCodecHelper()
            .getTypedIngredientCodec(VanillaTypes.ITEM_STACK);

        final ListTag inputsTag = new ListTag();
        for (final ItemStack input : recipe.getInputs()) {
            final Optional<ITypedIngredient<ItemStack>> typedIngredient = jeiHelpers.getIngredientManager()
                .createTypedIngredient(VanillaTypes.ITEM_STACK, input, false);
            if (typedIngredient.isEmpty()) {
                Constants.LOG.warn("Failed to encode JEI fuel recipe {}: invalid fuel input {}", recipeTypeUid, input);
                return null;
            }

            final DataResult<Tag> encodedInput = itemStackCodec.encodeStart(NbtOps.INSTANCE, typedIngredient.get())
                .map(tag -> tag);
            if (encodedInput.error().isPresent()) {
                Constants.LOG.warn("Failed to encode JEI fuel recipe {} input {}: {}", recipeTypeUid, input, encodedInput.error().get().message());
                return null;
            }

            inputsTag.add(encodedInput.result().orElseThrow());
        }

        final CompoundTag fuelData = new CompoundTag();
        fuelData.put(INPUTS_KEY, inputsTag);
        fuelData.putInt(BURN_TIME_KEY, recipe.getBurnTime());

        return new ShareRecipePacket(recipeTypeUid, wrapPayload(KIND_FUEL, fuelData));
    }

    public static boolean isFuelPayload(final Tag tag) {
        if (!(tag instanceof CompoundTag compound)) {
            return false;
        }
        return compound.getString(KIND_KEY).isPresent() && KIND_FUEL.equals(compound.getString(KIND_KEY).get());
    }

    public static Tag unwrapNormalPayload(final Tag tag) {
        if (!(tag instanceof CompoundTag compound) || !compound.contains(KIND_KEY)) {
            return tag;
        }

        return compound.get(DATA_KEY);
    }

    public static FuelPayload decodeFuelPayload(final ICodecHelper codecHelper, final Tag tag) {
        if (!(tag instanceof CompoundTag root)) {
            throw new IllegalStateException("Expected wrapped fuel payload to be a CompoundTag");
        }

        final Tag dataTag = root.get(DATA_KEY);
        if (!(dataTag instanceof CompoundTag fuelData)) {
            throw new IllegalStateException("Expected fuel payload data to be a CompoundTag");
        }

        final Codec<ITypedIngredient<ItemStack>> itemStackCodec = codecHelper.getTypedIngredientCodec(VanillaTypes.ITEM_STACK);
        final ListTag inputsTag = fuelData.getList(INPUTS_KEY)
            .orElseThrow(() -> new IllegalStateException("Missing or invalid '" + INPUTS_KEY + "' list"));

        final List<ItemStack> inputs = new ArrayList<>(inputsTag.size());
        for (final Tag inputTag : inputsTag) {
            final ITypedIngredient<ItemStack> typedIngredient = itemStackCodec.parse(NbtOps.INSTANCE, inputTag)
                .getOrThrow(error -> new IllegalStateException("Failed to decode JEI fuel input: " + error));

            final ItemStack stack = typedIngredient.getItemStack()
                .orElseThrow(() -> new IllegalStateException("Decoded JEI fuel input was not an ItemStack"));

            inputs.add(stack);
        }

        final int burnTime = fuelData.getInt(BURN_TIME_KEY)
            .orElseThrow(() -> new IllegalStateException("Missing or invalid '" + BURN_TIME_KEY + "' value"));
        return new FuelPayload(List.copyOf(inputs), burnTime);
    }

    private static CompoundTag wrapPayload(final String kind, final Tag data) {
        final CompoundTag root = new CompoundTag();
        root.putString(KIND_KEY, kind);
        root.put(DATA_KEY, data);
        return root;
    }

    public static void handleServer(final ShareRecipePacket payload, @Nullable final MinecraftServer server) {
        Services.PLATFORM.sendPacketToAllPlayers(server, payload);
    }

    public static void handleClient(final ShareRecipePacket payload, final PacketContext ctx) {
        ClientRecipeShareManager.receive(payload, ctx.getPlayer());
    }

    public record FuelPayload(List<ItemStack> inputs, int burnTime) {
    }
}
