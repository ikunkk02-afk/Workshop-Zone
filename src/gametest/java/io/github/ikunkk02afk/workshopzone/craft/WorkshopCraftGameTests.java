package io.github.ikunkk02afk.workshopzone.craft;

import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import io.github.ikunkk02afk.workshopzone.label.WorkshopContainerResolver;
import io.github.ikunkk02afk.workshopzone.network.ConfirmWorkshopCraftPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftExecutionResultPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftPreviewPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.ContainerLock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

public final class WorkshopCraftGameTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(2, 1, 2);
	private static final BlockPos STORAGE = new BlockPos(4, 1, 2);
	private static final Identifier CRAFTING_TABLE_RECIPE = Identifier.ofVanilla("crafting_table");

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void storageOnlyPreviewDoesNotMoveAnyItems(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		int storageBefore = fixture.container.inventory().count(Items.OAK_PLANKS);
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Storage should make the recipe available");
		context.assertEquals(4, preview.storageItemCount(), "All four planks should come from storage");
		context.assertEquals(storageBefore, fixture.container.inventory().count(Items.OAK_PLANKS), "Preview must not extract storage items");
		context.assertEquals(0, gridCount(fixture.handler), "Preview must not fill the crafting grid");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void cancelConfirmationMovesNothing(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		WorkshopCraftExecutionResultPayload result = fixture.service.confirm(
			fixture.player, new ConfirmWorkshopCraftPayload(preview.previewId(), false)
		);
		context.assertEquals(WorkshopCraftExecutionResultCode.CANCELLED, result.resultId(), "Cancellation should be explicit");
		context.assertEquals(4, fixture.container.inventory().count(Items.OAK_PLANKS), "Cancellation must not alter storage");
		context.assertEquals(0, gridCount(fixture.handler), "Cancellation must not alter the grid");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void confirmationFillsGridAndVanillaCreatesOutput(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, result.resultId(), "Confirmation should succeed");
		context.assertEquals(4, gridCount(fixture.handler), "Exactly one craft should be placed in the grid");
		context.assertEquals(Items.CRAFTING_TABLE, fixture.handler.getSlot(0).getStack().getItem(), "Vanilla output slot should contain a crafting table");
		context.assertEquals(0, fixture.player.getInventory().count(Items.CRAFTING_TABLE), "Output must not be inserted into the player inventory");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void playerMaterialsArePreferredAndStorageOnlyFillsDeficit(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		fixture.player.getInventory().setStack(9, new ItemStack(Items.BIRCH_PLANKS, 2));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(2, preview.storageItemCount(), "Only the two missing planks should come from storage");
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(2, result.usedPlayerItemCount(), "Two player planks should be used first");
		context.assertEquals(2, result.usedStorageItemCount(), "Storage should supply only the deficit");
		context.assertEquals(2, fixture.container.inventory().count(Items.OAK_PLANKS), "Storage should lose exactly two planks");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void playerEnoughKeepsOriginalPathAndDoesNotCreatePreview(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 16));
		fixture.player.getInventory().setStack(9, new ItemStack(Items.BIRCH_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.NOT_NEEDED, preview.resultId(), "Player-only recipe should not need Workshop confirmation");
		context.assertEquals(16, fixture.container.inventory().count(Items.OAK_PLANKS), "Storage should remain untouched");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void combinedMaterialsInsufficientMoveNothing(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 3));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.INSUFFICIENT, preview.resultId(), "Combined shortage should remain unavailable");
		context.assertEquals(3, fixture.container.inventory().count(Items.OAK_PLANKS), "Failed preview must not extract anything");
		context.assertEquals(0, gridCount(fixture.handler), "Failed preview must not fill anything");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void nonEmptyGridRejectsPreviewWithoutOverwriting(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		fixture.handler.getSlot(1).setStack(new ItemStack(Items.DIRT));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.GRID_NOT_EMPTY, preview.resultId(), "Real grid contents must reject refill");
		context.assertEquals(Items.DIRT, fixture.handler.getSlot(1).getStack().getItem(), "Existing grid item must remain unchanged");
		context.assertEquals(4, fixture.container.inventory().count(Items.OAK_PLANKS), "Storage must remain unchanged");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void storageRemovalAfterPreviewFailsWithoutPartialFill(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		fixture.container.inventory().clear();
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.MATERIALS_CHANGED, result.resultId(), "Changed materials should invalidate confirmation");
		context.assertEquals(0, gridCount(fixture.handler), "Changed materials must not partially fill the grid");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void gridChangeAfterPreviewRejectsConfirmation(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		fixture.handler.getSlot(5).setStack(new ItemStack(Items.STONE));
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.GRID_CHANGED, result.resultId(), "Grid changes should reject confirmation");
		context.assertEquals(Items.STONE, fixture.handler.getSlot(5).getStack().getItem(), "Grid change must never be overwritten");
		context.assertEquals(4, fixture.container.inventory().count(Items.OAK_PLANKS), "Storage must remain unchanged");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void destroyedContainerAfterPreviewFailsSafely(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.setBlockState(STORAGE, Blocks.AIR.getDefaultState());
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.MATERIALS_CHANGED, result.resultId(), "Destroyed storage should invalidate confirmation");
		context.assertEquals(0, gridCount(fixture.handler), "Destroyed storage must not cause a partial fill");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void duplicateConfirmationCanOnlySucceedOnce(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		WorkshopCraftExecutionResultPayload first = confirm(fixture, preview);
		WorkshopCraftExecutionResultPayload second = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, first.resultId(), "First confirmation should succeed");
		context.assertEquals(WorkshopCraftExecutionResultCode.INVALID_CONFIRMATION, second.resultId(), "Repeated nonce must be rejected");
		context.assertEquals(4, gridCount(fixture.handler), "Repeated confirmation must not duplicate inputs");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void recipeRevokedAfterPreviewRejectsConfirmation(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		RecipeEntry<?> recipe = fixture.player.getServer().getRecipeManager().get(CRAFTING_TABLE_RECIPE).orElseThrow();
		fixture.player.lockRecipes(List.of(recipe));
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(result.resultId(), WorkshopCraftExecutionResultCode.RECIPE_CHANGED, "A recipe removed after preview must not be filled");
		context.assertEquals(4, fixture.container.inventory().count(Items.OAK_PLANKS), "Revoked recipe must not extract storage");
		context.assertEquals(0, gridCount(fixture.handler), "Revoked recipe must not fill the grid");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void componentBearingMaterialKeepsItsComponentsInGrid(TestContext context) {
		ItemStack named = new ItemStack(Items.OAK_PLANKS, 4);
		named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Workshop material"));
		Fixture fixture = fixture(context, named);
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, result.resultId(), "Named planks should safely match a normal Ingredient");
		for (int slot = 1; slot <= 9; slot++) {
			ItemStack stack = fixture.handler.getSlot(slot).getStack();
			if (!stack.isEmpty()) {
				context.assertTrue(stack.contains(DataComponentTypes.CUSTOM_NAME), "Source Data Components must be preserved");
			}
		}
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void previewPayloadRoundTripsComponentStacks(TestContext context) {
		ItemStack named = new ItemStack(Items.OAK_PLANKS, 4);
		named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Network component"));
		Fixture fixture = fixture(context, named);
		WorkshopCraftPreviewPayload original = preview(fixture, CRAFTING_TABLE_RECIPE);
		RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
		WorkshopCraftPreviewPayload.CODEC.encode(buffer, original);
		WorkshopCraftPreviewPayload decoded = WorkshopCraftPreviewPayload.CODEC.decode(buffer);
		context.assertEquals(original.previewId(), decoded.previewId(), "Preview nonce should round trip");
		context.assertTrue(ItemStack.areEqual(original.materials().getFirst().stack(), decoded.materials().getFirst().stack()),
			"Component-bearing preview stack should round trip");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void doubleChestIsOneLogicalSourceAndCanUseBothHalves(TestContext context) {
		LogicalContainer doubleChest = doubleChest(context, STORAGE);
		doubleChest.inventory().setStack(0, new ItemStack(Items.OAK_PLANKS, 64));
		doubleChest.inventory().setStack(40, new ItemStack(Items.BIRCH_PLANKS, 64));
		Fixture fixture = fixture(context, doubleChest);
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Both chest halves should contribute");
		context.assertEquals(32, preview.plannedIterations(), "Both chest halves should combine into the maximum safe table batch");
		context.assertEquals(1, preview.usedContainerCount(), "Double chest must count as one logical container");
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, result.resultId(), "Double-chest extraction should succeed");
		context.assertEquals(1, result.usedContainerCount(), "Execution should still count one logical container");
		for (int slot : List.of(1, 2, 4, 5)) {
			ItemStack stack = fixture.handler.getSlot(slot).getStack();
			context.assertEquals(32, stack.getCount(), "Every target slot should contain one complete variant stack");
			context.assertTrue(stack.isOf(Items.OAK_PLANKS) || stack.isOf(Items.BIRCH_PLANKS),
				"Each target slot must contain one exact plank variant");
		}
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void craftAllRequestPlansAndAtomicallyFillsTheMaximumBatch(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 64));
		fixture.container.inventory().setStack(1, new ItemStack(Items.OAK_PLANKS, 56));
		fixture.player.getInventory().setStack(9, new ItemStack(Items.OAK_PLANKS, 8));
		fixture.player.unlockRecipes(List.of(
			fixture.player.getServer().getRecipeManager().get(CRAFTING_TABLE_RECIPE).orElseThrow()
		));
		WorkshopCraftPreviewPayload preview = fixture.service.preview(
			fixture.player, fixture.handler.syncId, CRAFTING_TABLE_RECIPE, true
		);
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Storage should extend the Shift batch");
		context.assertEquals(WorkshopCraftMode.BATCH, preview.craftMode(), "Shift request should produce a batch preview");
		context.assertEquals(2, preview.playerOnlyMaxIterations(), "Eight player planks support two table crafts");
		context.assertEquals(32, preview.combinedMaxIterations(), "Combined 128 planks support thirty-two crafts");
		context.assertEquals(32, preview.plannedIterations(), "Preview should plan the combined maximum");
		context.assertEquals(32L, preview.totalOutputCount(), "Total output should remain separate from the one-item icon stack");
		context.assertEquals(120, preview.storageItemCount(), "Preview should count only the planned storage deficit");
		context.assertEquals(120, fixture.container.inventory().count(Items.OAK_PLANKS), "Preview must not reserve storage");

		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, result.resultId(), "Batch confirmation should succeed");
		context.assertEquals(WorkshopCraftMode.BATCH, result.craftMode(), "Execution should retain batch mode");
		context.assertEquals(32, result.plannedIterations(), "Execution should retain the preview quantity");
		context.assertEquals(128, result.movedIngredientCount(), "Four input slots should each receive thirty-two items");
		context.assertEquals(8, result.usedPlayerItemCount(), "Player materials should be consumed first");
		context.assertEquals(120, result.usedStorageItemCount(), "Storage should supply only the deficit");
		for (int slot : List.of(1, 2, 4, 5)) {
			context.assertEquals(32, fixture.handler.getSlot(slot).getStack().getCount(), "Each shaped input slot should contain the full batch");
		}
		context.assertEquals(0, fixture.player.getInventory().count(Items.OAK_PLANKS), "All planned player planks should move to the grid");
		context.assertEquals(0, fixture.container.inventory().count(Items.OAK_PLANKS), "All planned storage planks should move to the grid");
		fixture.handler.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, fixture.player);
		context.assertEquals(32, fixture.player.getInventory().count(Items.CRAFTING_TABLE), "Vanilla Shift output should craft all prepared inputs");
		context.assertEquals(0, gridCount(fixture.handler), "Vanilla result slot should consume the complete prepared batch");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void craftAllKeepsVanillaWhenStorageCannotIncreasePlayerMaximum(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.DIRT, 64));
		fixture.player.getInventory().setStack(9, new ItemStack(Items.OAK_PLANKS, 64));
		fixture.player.getInventory().setStack(10, new ItemStack(Items.OAK_PLANKS, 64));
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.NOT_NEEDED, preview.resultId(), "Equal player and combined maxima should keep vanilla craft-all");
		context.assertEquals(64, fixture.container.inventory().count(Items.DIRT), "Unrelated storage must remain untouched");
		context.assertEquals(128, fixture.player.getInventory().count(Items.OAK_PLANKS), "Preview comparison must not move player inputs");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void craftAllWithOnlyOneFeasibleIterationDegradesToSingleConfirmation(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "One feasible storage craft should remain confirmable");
		context.assertEquals(WorkshopCraftMode.SINGLE, preview.craftMode(), "A one-iteration Shift plan should use the single confirmation UI");
		context.assertEquals(1, preview.plannedIterations(), "Degraded single plan should prepare one craft");
		context.assertEquals(1, preview.combinedMaxIterations(), "Combined maximum should remain visible as one");
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Degraded single confirmation should execute");
		context.assertEquals(4, gridCount(fixture.handler), "Degraded single confirmation should place one item per recipe position");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void batchMaterialReductionRejectsWithoutAutomaticDowngrade(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 64));
		fixture.container.inventory().setStack(1, new ItemStack(Items.OAK_PLANKS, 56));
		fixture.player.getInventory().setStack(9, new ItemStack(Items.OAK_PLANKS, 8));
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, CRAFTING_TABLE_RECIPE);
		fixture.container.inventory().setStack(1, new ItemStack(Items.OAK_PLANKS, 55));
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.BATCH_CHANGED, result.resultId(), "Reduced materials must invalidate the advertised batch");
		context.assertEquals(0, gridCount(fixture.handler), "Invalid batch must not partially fill the grid");
		context.assertEquals(8, fixture.player.getInventory().count(Items.OAK_PLANKS), "Invalid batch must not consume player materials");
		context.assertEquals(119, fixture.container.inventory().count(Items.OAK_PLANKS), "Invalid batch must not consume storage materials");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void outputCountAboveOneUsesLongTotalAndVanillaSingleResultStack(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_LOG, 64));
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, Identifier.ofVanilla("oak_planks"));
		context.assertEquals(WorkshopCraftMode.BATCH, preview.craftMode(), "Log-to-planks Shift request should be a batch");
		context.assertEquals(64, preview.plannedIterations(), "One log per slot should reach the hard limit");
		context.assertEquals(4, preview.outputPerIteration(), "Output icon should retain the recipe's per-craft count");
		context.assertEquals(256L, preview.totalOutputCount(), "Total output above one stack should use the separate long field");
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Batch log refill should execute");
		context.assertEquals(64, fixture.handler.getSlot(1).getStack().getCount(), "Input slot should receive sixty-four real logs");
		context.assertEquals(4, fixture.handler.getSlot(0).getStack().getCount(), "Vanilla result slot should still show one recipe result");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void sixteenStackInputCapsBatchAtSixteenIterations(TestContext context) {
		LogicalContainer container = place(context, STORAGE, Blocks.CHEST.getDefaultState());
		container.inventory().setStack(0, new ItemStack(Items.ENDER_PEARL, 16));
		container.inventory().setStack(1, new ItemStack(Items.BLAZE_POWDER, 64));
		Fixture fixture = fixture(context, container);
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, Identifier.ofVanilla("ender_eye"));
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Eye of Ender should support workshop batching");
		context.assertEquals(WorkshopCraftMode.BATCH, preview.craftMode(), "Sixteen crafts should remain a batch");
		context.assertEquals(16, preview.plannedIterations(), "Ender pearl stack limit should cap the batch at sixteen");
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Sixteen-stack batch should execute");
		context.assertEquals(16, fixture.handler.getSlot(1).getStack().getCount(), "First shapeless input should hold sixteen items");
		context.assertEquals(16, fixture.handler.getSlot(2).getStack().getCount(), "Second shapeless input should hold sixteen items");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void pistonRecipeFillsAllNineShapedSlotsForThePlannedBatch(TestContext context) {
		LogicalContainer container = place(context, STORAGE, Blocks.CHEST.getDefaultState());
		container.inventory().setStack(0, new ItemStack(Items.OAK_PLANKS, 64));
		container.inventory().setStack(1, new ItemStack(Items.OAK_PLANKS, 32));
		container.inventory().setStack(2, new ItemStack(Items.COBBLESTONE, 64));
		container.inventory().setStack(3, new ItemStack(Items.COBBLESTONE, 64));
		container.inventory().setStack(4, new ItemStack(Items.IRON_INGOT, 32));
		container.inventory().setStack(5, new ItemStack(Items.REDSTONE, 32));
		Fixture fixture = fixture(context, container);
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, Identifier.ofVanilla("piston"));
		context.assertEquals(32, preview.plannedIterations(), "Piston ingredients should support thirty-two complete recipes");
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Piston batch should execute");
		for (int slot = 1; slot <= 9; slot++) {
			context.assertEquals(32, fixture.handler.getSlot(slot).getStack().getCount(), "Every piston recipe position should contain thirty-two items");
		}
		context.assertEquals(Items.PISTON, fixture.handler.getSlot(0).getStack().getItem(), "Vanilla should compute the piston output");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void materialIncreaseAfterPreviewDoesNotSilentlyExpandBatch(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 64));
		fixture.container.inventory().setStack(1, new ItemStack(Items.OAK_PLANKS, 8));
		fixture.player.getInventory().setStack(9, new ItemStack(Items.OAK_PLANKS, 8));
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(20, preview.plannedIterations(), "Initial combined inventory should advertise twenty crafts");
		fixture.container.inventory().setStack(2, new ItemStack(Items.OAK_PLANKS, 40));
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, result.resultId(), "Additional materials should not invalidate the preview");
		context.assertEquals(20, result.plannedIterations(), "Confirmation must retain the preview quantity instead of expanding it");
		for (int slot : List.of(1, 2, 4, 5)) {
			context.assertEquals(20, fixture.handler.getSlot(slot).getStack().getCount(), "Only the advertised twenty crafts should be filled");
		}
		context.assertEquals(40, fixture.container.inventory().count(Items.OAK_PLANKS), "Newly added surplus should remain in storage");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void playerInventoryChangeReallocatesSourcesWithoutChangingPlannedBatch(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 64));
		fixture.container.inventory().setStack(1, new ItemStack(Items.OAK_PLANKS, 56));
		fixture.player.getInventory().setStack(9, new ItemStack(Items.OAK_PLANKS, 8));
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, CRAFTING_TABLE_RECIPE);
		fixture.container.inventory().setStack(1, new ItemStack(Items.OAK_PLANKS, 48));
		fixture.player.getInventory().setStack(10, new ItemStack(Items.OAK_PLANKS, 8));
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, result.resultId(), "Unchanged combined total should remain feasible");
		context.assertEquals(32, result.plannedIterations(), "Reallocation must retain the planned batch");
		context.assertEquals(16, result.usedPlayerItemCount(), "Confirmation should recalculate and prefer the new player materials");
		context.assertEquals(112, result.usedStorageItemCount(), "Storage should supply the recalculated deficit only");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void takingOutputUsesVanillaConsumptionAndLeavesNoDuplicate(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Refill should succeed");
		fixture.handler.onSlotClick(0, 0, SlotActionType.PICKUP, fixture.player);
		context.assertEquals(Items.CRAFTING_TABLE, fixture.handler.getCursorStack().getItem(), "Vanilla output click should put result on cursor");
		context.assertEquals(0, gridCount(fixture.handler), "Vanilla result slot should consume the four input planks");
		context.assertEquals(0, fixture.container.inventory().count(Items.OAK_PLANKS), "Storage should have exactly one craft removed");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void shapedRecipeUsesVanillaTopLeftAlignment(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Shaped refill should succeed");
		context.assertTrue(fixture.handler.getSlot(1).hasStack(), "2x2 recipe should use upper-left slot 1");
		context.assertTrue(fixture.handler.getSlot(2).hasStack(), "2x2 recipe should use upper-left slot 2");
		context.assertTrue(fixture.handler.getSlot(4).hasStack(), "2x2 recipe should use second-row slot 4");
		context.assertTrue(fixture.handler.getSlot(5).hasStack(), "2x2 recipe should use second-row slot 5");
		context.assertFalse(fixture.handler.getSlot(3).hasStack(), "2x2 recipe should not spill into slot 3");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void shapelessBatchUsesStableFirstInputSlotsAndKeepsSingleOutputStack(TestContext context) {
		Identifier recipe = Identifier.ofVanilla("flint_and_steel");
		LogicalContainer container = place(context, STORAGE, Blocks.CHEST.getDefaultState());
		container.inventory().setStack(0, new ItemStack(Items.FLINT, 64));
		container.inventory().setStack(1, new ItemStack(Items.IRON_INGOT, 64));
		Fixture fixture = fixture(context, container);
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, recipe);
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Vanilla shapeless recipe should be supported");
		context.assertEquals(64, preview.plannedIterations(), "Shapeless batch should reach the safe limit");
		context.assertEquals(1, preview.output().getCount(), "Static recipe output count should be preserved");
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Shapeless refill should succeed");
		context.assertEquals(64, fixture.handler.getSlot(1).getStack().getCount(), "Shapeless input should begin at the first stable grid slot");
		context.assertEquals(64, fixture.handler.getSlot(2).getStack().getCount(), "Second shapeless input should use the next stable grid slot");
		context.assertEquals(Items.FLINT_AND_STEEL, fixture.handler.getSlot(0).getStack().getItem(), "Vanilla should compute the shapeless output");
		context.assertEquals(1, fixture.handler.getSlot(0).getStack().getCount(), "Vanilla output count should remain one");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 240)
	public void expiredConfirmationMovesNothing(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.runAtTick(201, () -> {
			WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
			context.assertEquals(WorkshopCraftExecutionResultCode.EXPIRED, result.resultId(), "Preview should expire after 200 ticks");
			context.assertEquals(4, fixture.container.inventory().count(Items.OAK_PLANKS), "Expired confirmation must not extract storage");
			context.assertEquals(0, gridCount(fixture.handler), "Expired confirmation must not fill the grid");
			finish(fixture, context);
		});
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void lockedContainerCannotSupplyCraftingMaterials(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		ChestBlockEntity chest = (ChestBlockEntity)context.getWorld().getBlockEntity(fixture.container.representativePosition());
		ItemStack lockedChest = new ItemStack(Items.CHEST);
		lockedChest.set(DataComponentTypes.LOCK, new ContainerLock("workshop-secret"));
		chest.readComponents(lockedChest);
		fixture.container.inventory().setStack(0, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.NO_ACCESSIBLE_CONTAINERS, preview.resultId(), "Locked chest must not become a material source");
		context.assertEquals(4, fixture.container.inventory().count(Items.OAK_PLANKS), "Locked storage must remain untouched");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void craftAccessCallbackCanDenyExtractionWithoutMutation(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		WorkshopCraftPlanBuilder deniedBuilder = new WorkshopCraftPlanBuilder(
			new io.github.ikunkk02afk.workshopzone.search.WorkshopContainerAccessService(),
			(player, world, position, recipe, variant, amount) -> false
		);
		WorkshopCraftService deniedService = new WorkshopCraftService(fixture.manager, deniedBuilder);
		RecipeEntry<?> entry = fixture.player.getServer().getRecipeManager().get(CRAFTING_TABLE_RECIPE).orElseThrow();
		fixture.player.unlockRecipes(List.of(entry));
		WorkshopCraftPreviewPayload preview = deniedService.preview(fixture.player, fixture.handler.syncId, CRAFTING_TABLE_RECIPE, false);
		context.assertEquals(WorkshopCraftPreviewResultCode.DENIED, preview.resultId(), "Craft extraction callback should be authoritative");
		context.assertEquals(4, fixture.container.inventory().count(Items.OAK_PLANKS), "Denied source must not be mutated");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void twoPlayersCompetingForSameBatchOnlyOneSucceeds(TestContext context) {
		Fixture first = fixture(context, new ItemStack(Items.OAK_PLANKS, 64));
		first.container.inventory().setStack(1, new ItemStack(Items.OAK_PLANKS, 64));
		Fixture second = fixture(context, first.container);
		WorkshopCraftService sharedService = new WorkshopCraftService(first.manager);
		WorkshopCraftPreviewPayload firstPreview = previewBatch(sharedService, first, CRAFTING_TABLE_RECIPE);
		WorkshopCraftPreviewPayload secondPreview = previewBatch(sharedService, second, CRAFTING_TABLE_RECIPE);
		WorkshopCraftExecutionResultPayload firstResult = sharedService.confirm(first.player, new ConfirmWorkshopCraftPayload(firstPreview.previewId(), true));
		WorkshopCraftExecutionResultPayload secondResult = sharedService.confirm(second.player, new ConfirmWorkshopCraftPayload(secondPreview.previewId(), true));
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, firstResult.resultId(), "First atomic confirmation should win");
		context.assertEquals(WorkshopCraftExecutionResultCode.BATCH_CHANGED, secondResult.resultId(), "Second confirmation should reject the depleted batch quantity");
		context.assertEquals(128, gridCount(first.handler), "Winner should receive exactly one prepared batch in its grid");
		context.assertEquals(0, gridCount(second.handler), "Losing player must receive no partial fill");
		context.assertEquals(0, first.container.inventory().count(Items.OAK_PLANKS), "Materials must be extracted exactly once");
		finish(first, context);
		finish(second, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void cakeRemainderBucketsAreHandledOnlyByVanillaOutputSlot(TestContext context) {
		LogicalContainer container = place(context, STORAGE, Blocks.CHEST.getDefaultState());
		container.inventory().setStack(0, new ItemStack(Items.MILK_BUCKET));
		container.inventory().setStack(1, new ItemStack(Items.MILK_BUCKET));
		container.inventory().setStack(2, new ItemStack(Items.MILK_BUCKET));
		container.inventory().setStack(3, new ItemStack(Items.SUGAR, 2));
		container.inventory().setStack(4, new ItemStack(Items.EGG));
		container.inventory().setStack(5, new ItemStack(Items.WHEAT, 3));
		Fixture fixture = fixture(context, container);
		WorkshopCraftPreviewPayload preview = previewBatch(fixture, Identifier.ofVanilla("cake"));
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Cake should be a supported shaped recipe");
		context.assertEquals(WorkshopCraftMode.SINGLE, preview.craftMode(), "Non-stackable milk buckets should degrade the batch to one craft");
		context.assertEquals(1, preview.combinedMaxIterations(), "Milk bucket input slots must cap the batch at one");
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Cake grid refill should succeed");
		fixture.handler.onSlotClick(0, 0, SlotActionType.PICKUP, fixture.player);
		context.assertEquals(Items.CAKE, fixture.handler.getCursorStack().getItem(), "Vanilla output slot should provide the cake");
		int bucketCount = fixture.player.getInventory().count(Items.BUCKET);
		for (int slot = 1; slot <= 9; slot++) {
			bucketCount += fixture.handler.getSlot(slot).getStack().isOf(Items.BUCKET) ? fixture.handler.getSlot(slot).getStack().getCount() : 0;
		}
		context.assertEquals(3, bucketCount, "Vanilla should return exactly three buckets without duplication or loss");
		finish(fixture, context);
	}

	private static Fixture fixture(TestContext context, ItemStack storageStack) {
		LogicalContainer container = place(context, STORAGE, Blocks.CHEST.getDefaultState());
		container.inventory().setStack(0, storageStack.copy());
		return fixture(context, container);
	}

	private static Fixture fixture(TestContext context, LogicalContainer container) {
		context.setBlockState(TABLE, Blocks.CRAFTING_TABLE.getDefaultState());
		BlockPos tableAbsolute = context.getAbsolutePos(TABLE);
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		player.setPosition(tableAbsolute.getX() + 0.5, tableAbsolute.getY() + 1, tableAbsolute.getZ() + 0.5);
		var factory = context.getWorld().getBlockState(tableAbsolute).createScreenHandlerFactory(context.getWorld(), tableAbsolute);
		context.assertTrue(factory != null && player.openHandledScreen(factory).isPresent(), "Crafting table should open a handler");
		context.assertTrue(player.currentScreenHandler instanceof CraftingScreenHandler, "Opened handler should be the 3x3 crafting handler");
		WorkshopSessionManager manager = WorkshopSessionManager.getInstance();
		manager.open(player, tableAbsolute, WorkshopBlockType.CRAFTING_TABLE);
		WorkshopSession session = manager.get(player.getUuid()).orElseThrow();
		WorkshopCraftService service = new WorkshopCraftService(manager);
		return new Fixture(player, (CraftingScreenHandler)player.currentScreenHandler, manager, session, service, container);
	}

	private static WorkshopCraftPreviewPayload preview(Fixture fixture, Identifier recipeId) {
		return preview(fixture.service, fixture, recipeId);
	}

	private static WorkshopCraftPreviewPayload previewBatch(Fixture fixture, Identifier recipeId) {
		return previewBatch(fixture.service, fixture, recipeId);
	}

	private static WorkshopCraftPreviewPayload previewBatch(
		WorkshopCraftService service,
		Fixture fixture,
		Identifier recipeId
	) {
		RecipeEntry<?> entry = fixture.player.getServer().getRecipeManager().get(recipeId).orElseThrow();
		fixture.player.unlockRecipes(List.of(entry));
		return service.preview(fixture.player, fixture.handler.syncId, recipeId, true);
	}

	private static WorkshopCraftPreviewPayload preview(
		WorkshopCraftService service,
		Fixture fixture,
		Identifier recipeId
	) {
		RecipeEntry<?> entry = fixture.player.getServer().getRecipeManager().get(recipeId).orElseThrow();
		fixture.player.unlockRecipes(List.of(entry));
		return service.preview(fixture.player, fixture.handler.syncId, recipeId, false);
	}

	private static WorkshopCraftExecutionResultPayload confirm(Fixture fixture, WorkshopCraftPreviewPayload preview) {
		return fixture.service.confirm(fixture.player, new ConfirmWorkshopCraftPayload(preview.previewId(), true));
	}

	private static int gridCount(CraftingScreenHandler handler) {
		int count = 0;
		for (int slot = 1; slot <= 9; slot++) {
			count += handler.getSlot(slot).getStack().getCount();
		}
		return count;
	}

	private static LogicalContainer place(TestContext context, BlockPos relativePosition, BlockState state) {
		context.setBlockState(relativePosition, state);
		WorkshopContainerResolver.Result result = WorkshopContainerResolver.resolve(context.getWorld(), context.getAbsolutePos(relativePosition));
		context.assertTrue(result.successful(), "Placed container should resolve");
		return result.container();
	}

	private static LogicalContainer doubleChest(TestContext context, BlockPos relativePosition) {
		BlockState left = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH).with(ChestBlock.CHEST_TYPE, ChestType.LEFT);
		BlockState right = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH).with(ChestBlock.CHEST_TYPE, ChestType.RIGHT);
		context.setBlockState(relativePosition, left);
		context.setBlockState(relativePosition.add(1, 0, 0), right);
		WorkshopContainerResolver.Result result = WorkshopContainerResolver.resolve(context.getWorld(), context.getAbsolutePos(relativePosition));
		context.assertTrue(result.successful(), "Double chest should resolve");
		return result.container();
	}

	private static void finish(Fixture fixture, TestContext context) {
		fixture.manager.clear(fixture.player, false);
		fixture.player.closeHandledScreen();
		context.complete();
	}

	private record Fixture(
		ServerPlayerEntity player,
		CraftingScreenHandler handler,
		WorkshopSessionManager manager,
		WorkshopSession session,
		WorkshopCraftService service,
		LogicalContainer container
	) {
	}
}
