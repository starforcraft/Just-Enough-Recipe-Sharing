package com.ultramega.justenoughrecipesharing.recipes;

import java.util.UUID;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.jspecify.annotations.Nullable;

public interface RecipeChatLookup {
    @Nullable
    UUID jers$getRecipeId(GuiMessage message);

    @Nullable
    Integer jers$getSpacerIndexFromBottom(GuiMessage.Line line);
}
