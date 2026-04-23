package com.ultramega.justenoughrecipesharing.network;

import com.ultramega.justenoughrecipesharing.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import static com.ultramega.justenoughrecipesharing.network.ShareRecipePacket.DATA_KEY;
import static com.ultramega.justenoughrecipesharing.network.ShareRecipePacket.KIND_KEY;
import static com.ultramega.justenoughrecipesharing.network.ShareRecipePacket.getSharerName;
import static com.ultramega.justenoughrecipesharing.network.ShareRecipePacket.wrapPayload;

public record FuelPayload(List<ItemStack> inputs, int burnTime) {
    private static final String INPUTS_KEY = "inputs";
    private static final String BURN_TIME_KEY = "burn_time";

    private static final String KIND_FUEL = "fuel";

    @Nullable
    public static ShareRecipePacket encodeFuel(final IJeiHelpers jeiHelpers,
                                               final Identifier recipeTypeUid,
                                               final IJeiFuelingRecipe recipe,
                                               final List<FocusPayload> focuses,
                                               @Nullable final Player player) {
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

        return new ShareRecipePacket(recipeTypeUid, wrapPayload(KIND_FUEL, fuelData), List.copyOf(focuses), getSharerName(player));
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

    public static boolean isFuelPayload(final Tag tag) {
        if (!(tag instanceof CompoundTag compound)) {
            return false;
        }
        return compound.getString(KIND_KEY).isPresent() && KIND_FUEL.equals(compound.getString(KIND_KEY).get());
    }
}
