package com.ultramega.justenoughrecipesharing;

import com.ultramega.justenoughrecipesharing.client.ClientRecipeShareManager;
import com.ultramega.justenoughrecipesharing.recipes.RecipeShareButtonController;

import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.common.Internal;
import net.minecraft.resources.Identifier;

@JeiPlugin
public class JeiRecipeSharingPlugin implements IModPlugin {
    private static final Identifier UID = Constants.modLoc("jei_plugin");

    @Override
    public void registerAdvanced(final IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(new IRecipeButtonControllerFactory() {
            @Override
            public <T> IIconButtonController createButtonController(final IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
                return new RecipeShareButtonController<>(registration.getJeiHelpers(), recipeLayoutDrawable);
            }
        });
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    public static <T> void openSharedRecipe(final ClientRecipeShareManager.SharedRecipeDrawable<T> shared) {
        Internal.getJeiRuntime().getRecipesGui().showRecipes(shared.category(), List.of(shared.recipe()), shared.focuses());
    }
}
