package com.ultramega.justenoughrecipesharing.client;

import com.ultramega.justenoughrecipesharing.mixin.InvokerChatComponent;
import com.ultramega.justenoughrecipesharing.recipes.RecipeChatLookup;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

public final class ClientDrawRecipeInChat {
    @Nullable
    private static GuiGraphicsExtractor graphics = null;
    private static final Set<GuiMessage> DRAWN_THIS_FRAME = Collections.newSetFromMap(new IdentityHashMap<>());

    private ClientDrawRecipeInChat() {
    }

    public static void beginFrame(@Nullable final GuiGraphicsExtractor graphicsExtractor) {
        graphics = graphicsExtractor;
        DRAWN_THIS_FRAME.clear();
    }

    public static void endFrame() {
        graphics = null;
        DRAWN_THIS_FRAME.clear();
    }

    // TODO: I don't know if there's a good way to implement opacity, we will just keep it opaque for now
    public static void renderRecipeForLine(final GuiMessage.Line line, final float textTop, final float opacity) {
        if (graphics == null) {
            return;
        }

        // Only draw on spacer lines
        if (line.content() != FormattedCharSequence.EMPTY) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        final RecipeChatLookup lookup = (RecipeChatLookup) mc.gui.getChat();

        final Integer spacerIndexFromBottom = lookup.jers$getSpacerIndexFromBottom(line);
        if (spacerIndexFromBottom == null) {
            return;
        }
        final GuiMessage message = line.parent();
        if (!DRAWN_THIS_FRAME.add(message)) {
            return;
        }
        final UUID id = lookup.jers$getRecipeId(line.parent());
        if (id == null) {
            return;
        }
        final ClientRecipeShareManager.SharedRecipeDrawable<?> shared = ClientRecipeShareManager.get(id);
        if (shared == null) {
            return;
        }

        final IRecipeLayoutDrawable<?> drawable = shared.drawable();

        final int x = mc.font.width(" ");
        final int y = (int) textTop - drawable.getRect().getHeight() + mc.font.lineHeight / 2 + spacerIndexFromBottom * mc.font.lineHeight;

        final boolean isChatFocused = mc.screen instanceof ChatScreen;
        final int mouseX = isChatFocused ? getGuiMouseX(mc) : Integer.MIN_VALUE;
        final int mouseY = isChatFocused ? getGuiMouseY(mc) : Integer.MIN_VALUE;

        drawable.setPosition(x, y);
        ClientChatRecipeInteraction.add(message, id, drawable.getRectWithBorder());

        final double scale = mc.options.chatScale().get();

        final int visibleHeight = Mth.floor((float) ((InvokerChatComponent) mc.gui.getChat()).jers$getHeight() / (float) scale);
        final int chatBottom = Mth.floor((float) (graphics.guiHeight() - 40) / (float) scale);
        final int chatTop = chatBottom - visibleHeight;

        final int clipLeft = -4;
        final int clipRight = ((InvokerChatComponent) mc.gui.getChat()).jers$getWidth() + 4;

        graphics.enableScissor(clipLeft, chatTop, clipRight, chatBottom);
        drawable.drawRecipe(graphics, mouseX, mouseY);
        drawable.drawOverlays(graphics, mouseX, mouseY);
        graphics.disableScissor();
    }

    private static int getGuiMouseX(final Minecraft mc) {
        final var window = mc.getWindow();
        return (int) Math.floor(
            mc.mouseHandler.xpos() * window.getGuiScaledWidth() / (double) window.getScreenWidth()
        );
    }

    private static int getGuiMouseY(final Minecraft mc) {
        final var window = mc.getWindow();
        return (int) Math.floor(
            mc.mouseHandler.ypos() * window.getGuiScaledHeight() / (double) window.getScreenHeight()
        );
    }
}
