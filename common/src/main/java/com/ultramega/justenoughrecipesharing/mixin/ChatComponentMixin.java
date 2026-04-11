package com.ultramega.justenoughrecipesharing.mixin;

import com.ultramega.justenoughrecipesharing.client.ClientChatRecipeInteraction;
import com.ultramega.justenoughrecipesharing.client.ClientDrawRecipeInChat;
import com.ultramega.justenoughrecipesharing.client.ClientRecipeShareManager;
import com.ultramega.justenoughrecipesharing.recipes.RecipeChatComponentFactory;
import com.ultramega.justenoughrecipesharing.recipes.RecipeChatLookup;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements RecipeChatLookup {
    @Unique
    @Nullable
    private JersPendingSpacer jers$pendingSpacer;

    @Unique
    private final Map<GuiMessage, UUID> jers$recipesByMessage = new IdentityHashMap<>();

    @Unique
    private final Map<GuiMessage.Line, Integer> jers$spacerIndexFromBottom = new IdentityHashMap<>();

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;"
        + "IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
    private void jers$captureGuiGraphics(final CallbackInfo ci, final @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        ClientChatRecipeInteraction.beginFrame();
        ClientDrawRecipeInChat.beginFrame(graphics);
    }

    @Inject(method = "captureClickableText", at = @At("HEAD"))
    private void jers$noGraphics(final CallbackInfo ci) {
        ClientDrawRecipeInChat.endFrame();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;"
        + "IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("TAIL"))
    private void jers$finishRenderState(final CallbackInfo ci) {
        ClientDrawRecipeInChat.endFrame();
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
    private void jers$addMessageToDisplayQueueHead(final GuiMessage message, final CallbackInfo ci) {
        this.jers$pendingSpacer = null;

        final UUID id = RecipeChatComponentFactory.extractRecipeId(message.content());
        if (id == null) {
            return;
        }
        final ClientRecipeShareManager.SharedRecipeDrawable<?> shared = ClientRecipeShareManager.get(id);
        if (shared == null) {
            return;
        }

        this.jers$recipesByMessage.put(message, id);

        final int recipeHeight = shared.drawable().getRect().getHeight();
        final int padding = 4;
        final int lineHeight = this.getLineHeight();
        final int spacerLines = Mth.ceil((recipeHeight + padding) / (float) lineHeight);
        if (spacerLines <= 0) {
            return;
        }

        this.jers$pendingSpacer = new JersPendingSpacer(message, spacerLines);
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"))
    private void jers$addMessageToDisplayQueueTail(final GuiMessage message, final CallbackInfo ci) {
        final JersPendingSpacer pending = this.jers$pendingSpacer;
        this.jers$pendingSpacer = null;

        if (pending == null || pending.message() != message) {
            return;
        }

        // The newest entry is at the front
        // Its current bottom-most line is the first endOfEntry=true line
        int endIndex = -1;
        for (int i = 0; i < this.trimmedMessages.size(); i++) {
            if (this.trimmedMessages.get(i).endOfEntry()) {
                endIndex = i;
                break;
            }
        }

        if (endIndex < 0) {
            return;
        }

        final GuiMessage.Line originalEnd = this.trimmedMessages.get(endIndex);
        this.trimmedMessages.set(endIndex, new GuiMessage.Line(originalEnd.parent(), originalEnd.content(), false));

        // Insert spacers at the front so they are BELOW the text visually
        // Index 0 is the bottom-most line
        for (int spacerIndexFromBottom = pending.spacerLines() - 1; spacerIndexFromBottom >= 0; spacerIndexFromBottom--) {
            final boolean isBottomMostSpacer = spacerIndexFromBottom == 0;

            final GuiMessage.Line spacerLine = new GuiMessage.Line(message, FormattedCharSequence.EMPTY, isBottomMostSpacer);

            this.trimmedMessages.addFirst(spacerLine);
            this.jers$spacerIndexFromBottom.put(spacerLine, spacerIndexFromBottom);
        }

        while (this.trimmedMessages.size() > 100) {
            final GuiMessage.Line removed = this.trimmedMessages.removeLast();
            this.jers$spacerIndexFromBottom.remove(removed);
        }

        this.jers$recipesByMessage.keySet().removeIf(msg -> {
            for (final GuiMessage.Line line : this.trimmedMessages) {
                if (line.parent() == msg) {
                    return false;
                }
            }
            return true;
        });
    }

    @Unique
    @Override
    @Nullable
    public UUID jers$getRecipeId(final GuiMessage message) {
        return this.jers$recipesByMessage.get(message);
    }

    @Unique
    @Override
    @Nullable
    public Integer jers$getSpacerIndexFromBottom(final GuiMessage.Line line) {
        return this.jers$spacerIndexFromBottom.get(line);
    }

    @Shadow
    protected abstract int getLineHeight();

    @Unique
    private record JersPendingSpacer(GuiMessage message, int spacerLines) {
    }
}
