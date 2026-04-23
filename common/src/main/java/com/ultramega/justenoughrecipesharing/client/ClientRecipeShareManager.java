package com.ultramega.justenoughrecipesharing.client;

import com.ultramega.justenoughrecipesharing.Constants;
import com.ultramega.justenoughrecipesharing.network.FuelPayload;
import com.ultramega.justenoughrecipesharing.network.ShareRecipePacket;
import com.ultramega.justenoughrecipesharing.recipes.RecipeChatComponentFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ClientRecipeShareManager {
    private static final int MAX_SHARED_RECIPES = 50;
    private static final Map<UUID, SharedRecipeDrawable<?>> DRAWABLES =
        new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<UUID, SharedRecipeDrawable<?>> eldest) {
                return this.size() > MAX_SHARED_RECIPES;
            }
        };

    private ClientRecipeShareManager() {
    }

    public static void receive(final ShareRecipePacket payload, final Player player) {
        Minecraft.getInstance().execute(() -> {
            final IJeiRuntime runtime = Internal.getJeiRuntime();
            final IJeiHelpers jeiHelpers = runtime.getJeiHelpers();
            final IRecipeManager recipeManager = runtime.getRecipeManager();
            final ICodecHelper codecHelper = runtime.getJeiHelpers().getCodecHelper();
            final Optional<IRecipeType<?>> recipeTypeOpt = runtime.getJeiHelpers().getRecipeType(payload.recipeTypeUid());
            if (recipeTypeOpt.isEmpty()) {
                return;
            }

            final IRecipeType<?> recipeType = recipeTypeOpt.get();
            final IRecipeCategory<?> category = recipeManager.getRecipeCategory(recipeType);
            final Object recipe;

            if (FuelPayload.isFuelPayload(payload.recipeTag())) {
                try {
                    final FuelPayload fuelPayload = FuelPayload.decodeFuelPayload(codecHelper, payload.recipeTag());
                    recipe = new SharedFuelRecipe(fuelPayload.inputs(), fuelPayload.burnTime());
                } catch (IllegalStateException e) {
                    Constants.LOG.error("Failed to decode FuelPayload from recipeTag: {}", payload.recipeTag(), e);
                    return;
                }
            } else {
                final Tag recipeTag = ShareRecipePacket.unwrapNormalPayload(payload.recipeTag());
                final Codec<?> codec = category.getCodec(codecHelper, recipeManager);
                final DataResult<?> parsed = codec.parse(NbtOps.INSTANCE, recipeTag);
                if (parsed.error().isPresent()) {
                    Constants.LOG.warn("Failed to decode shared JEI recipe {}: {}", payload.recipeTypeUid(), parsed.error().get().message());
                    return;
                }

                recipe = parsed.result().orElseThrow();
            }

            final List<? extends IFocus<?>> focuses = payload.focuses().stream()
                .map(f -> {
                    final ITypedIngredient<?> ingredient = codecHelper.getTypedIngredientCodec()
                        .codec()
                        .parse(NbtOps.INSTANCE, f.ingredientTag())
                        .getOrThrow(error -> new IllegalStateException("Failed to decode JEI focus ingredient: " + error));

                    return jeiHelpers.getFocusFactory().createFocus(f.role(), ingredient);
                })
                .toList();

            createAndStoreDrawable(runtime, (IRecipeCategory<Object>) category, recipe, focuses)
                .ifPresent(id -> player.sendSystemMessage(
                    RecipeChatComponentFactory.makeSharedRecipeMessage(Component.literal(payload.sharerName()), id)
                ));
        });
    }

    private static <T> Optional<UUID> createAndStoreDrawable(final IJeiRuntime runtime,
                                                             final IRecipeCategory<T> category,
                                                             final T recipe,
                                                             final List<? extends IFocus<?>> focuses) {
        if (!category.isHandled(recipe)) {
            return Optional.empty();
        }

        final IFocusGroup focusGroup = runtime.getJeiHelpers().getFocusFactory().createFocusGroup(focuses);

        return runtime.getRecipeManager()
            .createRecipeLayoutDrawable(category, recipe, focusGroup)
            .map(drawable -> {
                final UUID id = UUID.randomUUID();
                DRAWABLES.put(id, new SharedRecipeDrawable<>(category, recipe, drawable, List.copyOf(focuses)));
                return id;
            });
    }

    @Nullable
    public static SharedRecipeDrawable<?> get(final UUID id) {
        return DRAWABLES.get(id);
    }

    public record SharedRecipeDrawable<T>(IRecipeCategory<T> category, T recipe, IRecipeLayoutDrawable<T> drawable, List<IFocus<?>> focuses) {
    }

    public record SharedFuelRecipe(List<ItemStack> inputs, int burnTime) implements IJeiFuelingRecipe {
        @Override
        public List<ItemStack> getInputs() {
            return this.inputs;
        }

        @Override
        public int getBurnTime() {
            return this.burnTime;
        }
    }
}
