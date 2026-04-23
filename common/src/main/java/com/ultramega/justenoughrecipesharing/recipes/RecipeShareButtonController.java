package com.ultramega.justenoughrecipesharing.recipes;

import com.ultramega.justenoughrecipesharing.Constants;
import com.ultramega.justenoughrecipesharing.network.FocusPayload;
import com.ultramega.justenoughrecipesharing.network.ShareRecipePacket;
import com.ultramega.justenoughrecipesharing.platform.Services;

import java.util.List;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class RecipeShareButtonController<T> implements IIconButtonController {
    private static final Identifier TEXTURE = Constants.modLoc("textures/gui/icons/share.png");

    private final IJeiHelpers jeiHelpers;
    private final IRecipeLayoutDrawable<T> recipeLayoutDrawable;
    private final IDrawableStatic icon;

    public RecipeShareButtonController(final IJeiHelpers jeiHelpers, final IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
        this.jeiHelpers = jeiHelpers;
        this.recipeLayoutDrawable = recipeLayoutDrawable;
        this.icon = jeiHelpers.getGuiHelper()
            .drawableBuilder(TEXTURE, 0, 0, 9, 9)
            .setTextureSize(9, 9)
            .build();
    }

    @Override
    public void getTooltips(final ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("tooltip.justenoughrecipesharing.share"));
    }

    @Override
    public void initState(final IButtonState state) {
        state.setIcon(this.icon);
        this.updateState(state);
    }

    @Override
    public boolean onPress(final IJeiUserInput input) {
        if (input.isSimulate()) {
            return true;
        }

        final IRecipeCategory<T> category = this.recipeLayoutDrawable.getRecipeCategory();
        final T recipe = this.recipeLayoutDrawable.getRecipe();
        final Identifier recipeTypeUid = category.getRecipeType().getUid();
        final List<FocusPayload> focuses = ShareRecipePacket.captureDisplayedFocuses(this.recipeLayoutDrawable.getRecipeSlotsView(), this.jeiHelpers);

        final var packet = ShareRecipePacket.encode(this.jeiHelpers, category, recipeTypeUid, recipe, focuses, Minecraft.getInstance().player);
        if (packet != null) {
            Services.PLATFORM.sendPacketToServer(packet);
        }
        return true;
    }
}
