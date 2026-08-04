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
		doubleChest.inventory().setStack(0, new ItemStack(Items.OAK_PLANKS, 2));
		doubleChest.inventory().setStack(40, new ItemStack(Items.BIRCH_PLANKS, 2));
		Fixture fixture = fixture(context, doubleChest);
		WorkshopCraftPreviewPayload preview = preview(fixture, CRAFTING_TABLE_RECIPE);
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Both chest halves should contribute");
		context.assertEquals(1, preview.usedContainerCount(), "Double chest must count as one logical container");
		WorkshopCraftExecutionResultPayload result = confirm(fixture, preview);
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, result.resultId(), "Double-chest extraction should succeed");
		context.assertEquals(1, result.usedContainerCount(), "Execution should still count one logical container");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void craftAllRequestNeverUsesWorkshopStorage(TestContext context) {
		Fixture fixture = fixture(context, new ItemStack(Items.OAK_PLANKS, 16));
		WorkshopCraftPreviewPayload preview = fixture.service.preview(
			fixture.player, fixture.handler.syncId, CRAFTING_TABLE_RECIPE, true
		);
		context.assertEquals(WorkshopCraftPreviewResultCode.NOT_NEEDED, preview.resultId(), "Shift/craft-all is outside this phase");
		context.assertEquals(16, fixture.container.inventory().count(Items.OAK_PLANKS), "Craft-all must not read or extract storage");
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
	public void shapelessRecipeUsesStableFirstInputSlotAndKeepsOutputCount(TestContext context) {
		Identifier recipe = Identifier.ofVanilla("flint_and_steel");
		LogicalContainer container = place(context, STORAGE, Blocks.CHEST.getDefaultState());
		container.inventory().setStack(0, new ItemStack(Items.FLINT));
		container.inventory().setStack(1, new ItemStack(Items.IRON_INGOT));
		Fixture fixture = fixture(context, container);
		WorkshopCraftPreviewPayload preview = preview(fixture, recipe);
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Vanilla shapeless recipe should be supported");
		context.assertEquals(1, preview.output().getCount(), "Static recipe output count should be preserved");
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, confirm(fixture, preview).resultId(), "Shapeless refill should succeed");
		context.assertTrue(fixture.handler.getSlot(1).hasStack(), "Shapeless input should begin at the first stable grid slot");
		context.assertTrue(fixture.handler.getSlot(2).hasStack(), "Second shapeless input should use the next stable grid slot");
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
	public void twoPlayersCompetingForSameMaterialsOnlyOneSucceeds(TestContext context) {
		Fixture first = fixture(context, new ItemStack(Items.OAK_PLANKS, 4));
		Fixture second = fixture(context, first.container);
		WorkshopCraftService sharedService = new WorkshopCraftService(first.manager);
		WorkshopCraftPreviewPayload firstPreview = preview(sharedService, first, CRAFTING_TABLE_RECIPE);
		WorkshopCraftPreviewPayload secondPreview = preview(sharedService, second, CRAFTING_TABLE_RECIPE);
		WorkshopCraftExecutionResultPayload firstResult = sharedService.confirm(first.player, new ConfirmWorkshopCraftPayload(firstPreview.previewId(), true));
		WorkshopCraftExecutionResultPayload secondResult = sharedService.confirm(second.player, new ConfirmWorkshopCraftPayload(secondPreview.previewId(), true));
		context.assertEquals(WorkshopCraftExecutionResultCode.SUCCESS, firstResult.resultId(), "First atomic confirmation should win");
		context.assertEquals(WorkshopCraftExecutionResultCode.MATERIALS_CHANGED, secondResult.resultId(), "Second confirmation should observe depleted storage");
		context.assertEquals(4, gridCount(first.handler), "Winner should receive exactly one craft in its grid");
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
		WorkshopCraftPreviewPayload preview = preview(fixture, Identifier.ofVanilla("cake"));
		context.assertEquals(WorkshopCraftPreviewResultCode.AVAILABLE, preview.resultId(), "Cake should be a supported shaped recipe");
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
