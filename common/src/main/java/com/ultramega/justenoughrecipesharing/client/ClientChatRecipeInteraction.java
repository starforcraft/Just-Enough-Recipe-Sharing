package com.ultramega.justenoughrecipesharing.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.Rect2i;
import org.jspecify.annotations.Nullable;

public final class ClientChatRecipeInteraction {
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ClientChatRecipeInteraction() {
    }

    public static void beginFrame() {
        ENTRIES.clear();
    }

    public static void add(final GuiMessage message, final UUID recipeId, final Rect2i rect) {
        ENTRIES.add(new Entry(message, recipeId, rect));
    }

    @Nullable
    public static UUID findRecipeAt(final double mouseX, final double mouseY) {
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            final Entry entry = ENTRIES.get(i);
            final Rect2i rect = entry.rect();
            if (mouseX >= rect.getX() && mouseX < rect.getX() + rect.getWidth()
                && mouseY >= rect.getY() && mouseY < rect.getY() + rect.getHeight()) {
                return entry.recipeId();
            }
        }
        return null;
    }

    private record Entry(GuiMessage message, UUID recipeId, Rect2i rect) {
    }
}
