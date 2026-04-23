package com.ultramega.justenoughrecipesharing.network;

import io.netty.buffer.ByteBuf;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record FocusPayload(RecipeIngredientRole role, Tag ingredientTag) {
    public static final StreamCodec<ByteBuf, FocusPayload> STREAM_CODEC = ByteBufCodecs.TAG.map(
        FocusPayload::fromNetworkTag,
        FocusPayload::toNetworkTag
    );

    private static final String ROLE_KEY = "role";
    private static final String INGREDIENT_KEY = "ingredient";

    public Tag toNetworkTag() {
        final CompoundTag tag = new CompoundTag();
        tag.putString(ROLE_KEY, this.role.name());
        tag.put(INGREDIENT_KEY, this.ingredientTag.copy());
        return tag;
    }

    public static FocusPayload fromNetworkTag(final Tag rawTag) {
        if (!(rawTag instanceof CompoundTag tag)) {
            throw new IllegalStateException("Expected FocusPayload to be a CompoundTag");
        }

        final RecipeIngredientRole role = RecipeIngredientRole.valueOf(
            tag.getString(ROLE_KEY)
                .orElseThrow(() -> new IllegalStateException("Missing focus role"))
        );

        final Tag ingredientTag = tag.get(INGREDIENT_KEY);
        if (ingredientTag == null) {
            throw new IllegalStateException("Missing focus ingredient");
        }

        return new FocusPayload(role, ingredientTag.copy());
    }
}
