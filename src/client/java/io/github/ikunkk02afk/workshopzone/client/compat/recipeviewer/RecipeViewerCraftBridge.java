package io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.util.Identifier;

public final class RecipeViewerCraftBridge {
	private static final Identifier AIR_ID = Identifier.ofVanilla("air");
	private static final RecipeViewerTransferGuard GUARD = new RecipeViewerTransferGuard();

	private RecipeViewerCraftBridge() {
	}

	public static RecipeViewerTransferResult request(
		RecipeViewerSource source,
		Identifier recipeId,
		boolean batch
	) {
		try {
			return request(source, recipeId, batch, new MinecraftClientAccess(MinecraftClient.getInstance()), GUARD);
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.error("Recipe viewer crafting bridge failed safely for {}", recipeId, exception);
			return RecipeViewerTransferResult.INTERNAL_ERROR;
		}
	}

	static RecipeViewerTransferResult request(
		RecipeViewerSource source,
		Identifier recipeId,
		boolean batch,
		ClientAccess client,
		RecipeViewerTransferGuard guard
	) {
		if (source == null) {
			return RecipeViewerTransferResult.NOT_APPLICABLE;
		}
		if (client == null || !client.hasClient()) {
			return RecipeViewerTransferResult.NO_CLIENT;
		}
		if (!client.hasPlayer()) {
			return RecipeViewerTransferResult.NO_PLAYER;
		}
		if (!client.hasInteractionManager()) {
			return RecipeViewerTransferResult.NO_INTERACTION_MANAGER;
		}
		if (!client.isCraftingScreen() || !client.hasCraftingScreenHandler()) {
			return RecipeViewerTransferResult.INVALID_SCREEN;
		}
		if (recipeId == null || AIR_ID.equals(recipeId)) {
			return RecipeViewerTransferResult.INVALID_RECIPE;
		}

		RecipeStatus recipeStatus = client.resolveRecipe(recipeId);
		if (recipeStatus == RecipeStatus.UNKNOWN || recipeStatus == RecipeStatus.INVALID_OUTPUT) {
			return RecipeViewerTransferResult.INVALID_RECIPE;
		}
		if (recipeStatus != RecipeStatus.VALID) {
			return RecipeViewerTransferResult.UNSUPPORTED_RECIPE;
		}
		if (!client.isCraftingGridEmpty()) {
			return RecipeViewerTransferResult.GRID_NOT_EMPTY;
		}
		if (!guard.allow(
			source, recipeId, client.syncId(), batch, client.screenIdentity(), client.clientTick()
		)) {
			return RecipeViewerTransferResult.DUPLICATE_REQUEST;
		}

		client.clickRecipe(batch);
		return RecipeViewerTransferResult.REQUEST_SENT;
	}

	public static void reset() {
		GUARD.clear();
	}

	enum RecipeStatus {
		VALID,
		UNKNOWN,
		UNSUPPORTED,
		INVALID_OUTPUT
	}

	interface ClientAccess {
		boolean hasClient();

		boolean hasPlayer();

		boolean hasInteractionManager();

		boolean isCraftingScreen();

		boolean hasCraftingScreenHandler();

		RecipeStatus resolveRecipe(Identifier recipeId);

		boolean isCraftingGridEmpty();

		int syncId();

		long clientTick();

		Object screenIdentity();

		void clickRecipe(boolean batch);
	}

	private static final class MinecraftClientAccess implements ClientAccess {
		private final MinecraftClient client;
		private RecipeEntry<?> resolvedRecipe;

		private MinecraftClientAccess(MinecraftClient client) {
			this.client = client;
		}

		@Override
		public boolean hasClient() {
			return client != null;
		}

		@Override
		public boolean hasPlayer() {
			return client.player != null;
		}

		@Override
		public boolean hasInteractionManager() {
			return client.interactionManager != null;
		}

		@Override
		public boolean isCraftingScreen() {
			return client.currentScreen instanceof CraftingScreen;
		}

		@Override
		public boolean hasCraftingScreenHandler() {
			CraftingScreenHandler handler = currentHandler();
			return handler != null
				&& handler == client.player.currentScreenHandler
				&& handler.getCraftingWidth() == 3
				&& handler.getCraftingHeight() == 3
				&& handler.getCraftingSlotCount() == 9;
		}

		@Override
		public RecipeStatus resolveRecipe(Identifier recipeId) {
			if (client.getNetworkHandler() == null) {
				return RecipeStatus.UNKNOWN;
			}
			RecipeEntry<?> entry = client.getNetworkHandler().getRecipeManager().get(recipeId).orElse(null);
			if (entry == null) {
				return RecipeStatus.UNKNOWN;
			}
			if (!(entry.value() instanceof CraftingRecipe craftingRecipe)) {
				return RecipeStatus.UNSUPPORTED;
			}
			ItemStack output = craftingRecipe.getResult(client.player.getRegistryManager());
			if (output.isEmpty() || output.isOf(Items.AIR)) {
				return RecipeStatus.INVALID_OUTPUT;
			}
			resolvedRecipe = entry;
			return RecipeStatus.VALID;
		}

		@Override
		public boolean isCraftingGridEmpty() {
			CraftingScreenHandler handler = currentHandler();
			if (handler == null) {
				return false;
			}
			for (int slot = 1; slot <= handler.getCraftingSlotCount(); slot++) {
				if (!handler.getSlot(slot).getStack().isEmpty()) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int syncId() {
			CraftingScreenHandler handler = currentHandler();
			return handler == null ? -1 : handler.syncId;
		}

		@Override
		public long clientTick() {
			return client.player.age;
		}

		@Override
		public Object screenIdentity() {
			return client.currentScreen;
		}

		@Override
		public void clickRecipe(boolean batch) {
			CraftingScreenHandler handler = currentHandler();
			if (handler == null || resolvedRecipe == null) {
				throw new IllegalStateException("Recipe viewer request lost its validated crafting context");
			}
			client.interactionManager.clickRecipe(handler.syncId, resolvedRecipe, batch);
		}

		private CraftingScreenHandler currentHandler() {
			if (client == null || !(client.currentScreen instanceof CraftingScreen screen)) {
				return null;
			}
			return screen.getScreenHandler();
		}
	}
}
