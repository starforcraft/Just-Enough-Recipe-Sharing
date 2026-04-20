package com.ultramega.justenoughrecipesharing.recipes;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

public final class RecipeChatComponentFactory {
    private static final String PREFIX = "jers:";

    private RecipeChatComponentFactory() {
    }

    public static Component makeSharedRecipeMessage(final Component senderName, final UUID recipeId) {
        final MutableComponent recipeMarker = Component.literal("  ")
            .withStyle(style -> style
                .withInsertion(PREFIX + recipeId)
                .withColor(ChatFormatting.WHITE)
            );

        return Component.translatable(
            "misc.justenoughrecipesharing.shared_recipe",
            senderName,
            recipeMarker
        );
    }

    @Nullable
    public static UUID extractRecipeId(final Component component) {
        return extractRecipeId(component.getVisualOrderText());
    }

    @Nullable
    public static UUID extractRecipeId(final FormattedCharSequence sequence) {
        final UUID[] found = new UUID[1];

        sequence.accept((index, style, codePoint) -> {
            final String insertion = style.getInsertion();
            if (insertion != null && insertion.startsWith(PREFIX)) {
                try {
                    found[0] = UUID.fromString(insertion.substring(PREFIX.length()));
                    return false;
                } catch (IllegalArgumentException ignored) {
                }
            }
            return true;
        });

        return found[0];
    }
}
