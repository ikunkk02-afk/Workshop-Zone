package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResult;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelOperation;
import io.github.ikunkk02afk.workshopzone.network.UpdateContainerLabelPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Optional;

public final class ContainerLabelGameTests implements FabricGameTest {
	private static final BlockPos BASE = new BlockPos(4, 4, 4);

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void singleChestAcceptsExactLabel(TestContext context) {
		LogicalContainer container = placeAndResolve(context, Blocks.CHEST.getDefaultState());
		context.assertTrue(ContainerLabelService.applyAtomically(container, ironRule()), "Single chest label should apply");
		context.assertEquals(ironRule(), container.holders().getFirst().workshopZone$getLabelRule(), "Single chest should store the rule");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void barrelAcceptsExactLabel(TestContext context) {
		LogicalContainer container = placeAndResolve(context, Blocks.BARREL.getDefaultState());
		ContainerLabelService.applyAtomically(container, ironRule());
		context.assertEquals(ironRule(), container.holders().getFirst().workshopZone$getLabelRule(), "Barrel should store the rule");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void trappedChestAcceptsExactLabel(TestContext context) {
		LogicalContainer container = placeAndResolve(context, Blocks.TRAPPED_CHEST.getDefaultState());
		ContainerLabelService.applyAtomically(container, ironRule());
		context.assertEquals(ironRule(), container.holders().getFirst().workshopZone$getLabelRule(), "Trapped chest should store the rule");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void labelPersistsThroughBlockEntityNbt(TestContext context) {
		LogicalContainer container = placeAndResolve(context, Blocks.CHEST.getDefaultState());
		ContainerLabelService.applyAtomically(container, ironRule());
		BlockEntity original = container.members().getFirst();
		NbtCompound nbt = original.createNbt(context.getWorld().getRegistryManager());
		ChestBlockEntity reloaded = new ChestBlockEntity(original.getPos(), original.getCachedState());
		reloaded.read(nbt, context.getWorld().getRegistryManager());
		context.assertEquals(ironRule(), ((ContainerLabelHolder)reloaded).workshopZone$getLabelRule(), "Reloaded NBT should retain the label");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void matchingItemCanInsert(TestContext context) {
		LogicalContainer container = labeledChest(context);
		context.assertTrue(container.inventory().isValid(0, new ItemStack(Items.IRON_INGOT)), "Iron should be valid");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void differentItemCannotInsert(TestContext context) {
		LogicalContainer container = labeledChest(context);
		context.assertTrue(!container.inventory().isValid(0, new ItemStack(Items.GOLD_INGOT)), "Gold should be rejected");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void existingWrongItemCanStillBeExtracted(TestContext context) {
		LogicalContainer container = placeAndResolve(context, Blocks.CHEST.getDefaultState());
		container.inventory().setStack(0, new ItemStack(Items.GOLD_INGOT));
		container.holders().getFirst().workshopZone$setLabelRule(ironRule());
		ItemStack extracted = container.inventory().removeStack(0);
		context.assertEquals(Items.GOLD_INGOT, extracted.getItem(), "Extraction must remain unrestricted");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftClickWrongItemDoesNotEnter(TestContext context) {
		LogicalContainer container = labeledChest(context);
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		player.setPosition(container.representativePosition().getX() + 0.5, container.representativePosition().getY() + 1, container.representativePosition().getZ() + 0.5);
		var factory = context.getWorld().getBlockState(container.representativePosition()).createScreenHandlerFactory(context.getWorld(), container.representativePosition());
		context.assertTrue(factory != null && player.openHandledScreen(factory).isPresent(), "Chest should open");
		player.getInventory().setStack(0, new ItemStack(Items.GOLD_INGOT, 8));
		((GenericContainerScreenHandler)player.currentScreenHandler).quickMove(player, 54);
		context.assertEquals(8, player.getInventory().getStack(0).getCount(), "Rejected shift-click stack should remain in its source slot");
		context.assertTrue(container.inventory().isEmpty(), "Rejected shift-click must not partially insert");
		player.closeHandledScreen();
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void hopperCanInsertMatchingItem(TestContext context) {
		LogicalContainer container = labeledChest(context);
		ItemStack remainder = HopperBlockEntity.transfer(null, container.inventory(), new ItemStack(Items.IRON_INGOT), null);
		context.assertTrue(remainder.isEmpty(), "Hopper path should insert matching iron");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void hopperCannotInsertWrongItem(TestContext context) {
		LogicalContainer container = labeledChest(context);
		ItemStack remainder = HopperBlockEntity.transfer(null, container.inventory(), new ItemStack(Items.GOLD_INGOT), null);
		context.assertEquals(1, remainder.getCount(), "Wrong hopper item should remain unchanged");
		context.assertTrue(container.inventory().isEmpty(), "Wrong hopper item must not enter");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void clearingLabelAllowsWrongItem(TestContext context) {
		LogicalContainer container = labeledChest(context);
		ContainerLabelService.applyAtomically(container, ContainerLabelRule.NONE);
		context.assertTrue(container.inventory().isValid(0, new ItemStack(Items.GOLD_INGOT)), "Clear should restore vanilla insertion");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void incompatibleContentsRejectNewRule(TestContext context) {
		LogicalContainer container = placeAndResolve(context, Blocks.CHEST.getDefaultState());
		container.inventory().setStack(9, new ItemStack(Items.GOLD_INGOT));
		ContainerLabelService.ContentValidation result = ContainerLabelService.validateContents(container.inventory(), ironRule());
		context.assertTrue(!result.compatible(), "Gold contents should reject an iron rule");
		context.assertEquals(1, result.mismatchSlotCount(), "Every slot should be checked and counted");
		context.assertEquals(ContainerLabelRule.NONE, container.holders().getFirst().workshopZone$getLabelRule(), "Validation must not mutate the label");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void emptyChestAcceptsAnyValidExactRule(TestContext context) {
		LogicalContainer container = placeAndResolve(context, Blocks.CHEST.getDefaultState());
		ContainerLabelRule diamonds = ContainerLabelRule.exact(Items.DIAMOND);
		context.assertTrue(ContainerLabelService.validateContents(container.inventory(), diamonds).compatible(), "Empty chest should validate");
		context.assertTrue(ContainerLabelService.applyAtomically(container, diamonds), "Empty chest should accept the rule");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void doubleChestStoresRuleOnBothHalves(TestContext context) {
		LogicalContainer container = doubleChest(context);
		ContainerLabelService.applyAtomically(container, ironRule());
		context.assertEquals(2, container.holders().size(), "Double chest should expose both members");
		context.assertTrue(container.holders().stream().allMatch(holder -> holder.workshopZone$getLabelRule().equals(ironRule())), "Both halves should have identical rules");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void compatibleOneSidedDoubleChestLabelSynchronizes(TestContext context) {
		LogicalContainer container = doubleChest(context);
		container.inventory().setStack(30, new ItemStack(Items.IRON_INGOT));
		container.holders().getFirst().workshopZone$setLabelRule(ironRule());
		context.assertEquals(ironRule(), container.holders().getFirst().workshopZone$getLabelRule(), "First half starts labeled");
		context.assertEquals(ContainerLabelRule.NONE, container.holders().get(1).workshopZone$getLabelRule(), "Second half starts unlabeled");
		context.assertTrue(ContainerLabelService.reconcile(container).hasLabel(), "Compatible contents should reconcile");
		context.assertTrue(container.holders().stream().allMatch(holder -> holder.workshopZone$getLabelRule().equals(ironRule())), "Reconcile should synchronize both halves");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void incompatibleOneSidedDoubleChestLabelBecomesConflict(TestContext context) {
		LogicalContainer container = doubleChest(context);
		container.inventory().setStack(30, new ItemStack(Items.GOLD_INGOT));
		container.holders().getFirst().workshopZone$setLabelRule(ironRule());
		context.assertTrue(ContainerLabelService.reconcile(container).conflict(), "Incompatible contents should conflict");
		context.assertEquals(ContainerLabelRule.NONE, container.holders().get(1).workshopZone$getLabelRule(), "Conflict must not silently overwrite the other half");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void splitDoubleChestKeepsRemainingRule(TestContext context) {
		LogicalContainer container = doubleChest(context);
		ContainerLabelService.applyAtomically(container, ironRule());
		BlockPos removed = container.memberPositions().get(1);
		context.getWorld().breakBlock(removed, false);
		BlockEntity remaining = context.getWorld().getBlockEntity(container.representativePosition());
		context.assertTrue(remaining instanceof ContainerLabelHolder, "Remaining chest should still exist");
		context.assertEquals(ironRule(), ((ContainerLabelHolder)remaining).workshopZone$getLabelRule(), "Remaining half should retain its label");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void conflictingDoubleChestBlocksAllInput(TestContext context) {
		LogicalContainer container = doubleChest(context);
		container.holders().get(0).workshopZone$setLabelRule(ironRule());
		container.holders().get(1).workshopZone$setLabelRule(ContainerLabelRule.exact(Items.GOLD_INGOT));
		context.assertTrue(ContainerLabelService.reconcile(container).conflict(), "Different labels should produce conflict");
		context.assertTrue(!container.inventory().isValid(0, new ItemStack(Items.IRON_INGOT)), "Conflict should reject input to first half");
		context.assertTrue(!container.inventory().isValid(27, new ItemStack(Items.GOLD_INGOT)), "Conflict should reject input to second half");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void conflictingDoubleChestCanBeCleared(TestContext context) {
		LogicalContainer container = doubleChest(context);
		container.holders().get(0).workshopZone$setLabelRule(ironRule());
		container.holders().get(1).workshopZone$setLabelRule(ContainerLabelRule.exact(Items.GOLD_INGOT));
		context.assertTrue(ContainerLabelService.applyAtomically(container, ContainerLabelRule.NONE), "Conflict clear should update both halves");
		context.assertTrue(container.holders().stream().allMatch(holder -> holder.workshopZone$getLabelRule().equals(ContainerLabelRule.NONE)), "Both halves should clear");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void unloadedContainerChunkIsNotForced(TestContext context) {
		BlockPos far = context.getAbsolutePos(BASE).add(16000, 0, 0);
		int chunkX = far.getX() >> 4;
		int chunkZ = far.getZ() >> 4;
		context.assertTrue(!context.getWorld().getChunkManager().isChunkLoaded(chunkX, chunkZ), "Far chunk should begin unloaded");
		WorkshopContainerResolver.Result result = WorkshopContainerResolver.resolve(context.getWorld(), far);
		context.assertEquals(WorkshopContainerResolver.Status.CHUNK_UNLOADED, result.status(), "Resolver should reject an unloaded chunk");
		context.assertTrue(!context.getWorld().getChunkManager().isChunkLoaded(chunkX, chunkZ), "Resolver must not force-load it");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void nonContainerCannotResolve(TestContext context) {
		context.setBlockState(BASE, Blocks.CRAFTING_TABLE);
		WorkshopContainerResolver.Result result = WorkshopContainerResolver.resolve(context.getWorld(), context.getAbsolutePos(BASE));
		context.assertEquals(WorkshopContainerResolver.Status.NOT_CONTAINER, result.status(), "Worktable must not be labelable");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void forgedOtherCoordinateIsRejected(TestContext context) {
		context.setBlockState(BASE, Blocks.CHEST);
		context.setBlockState(BASE.add(2, 0, 0), Blocks.CHEST);
		BlockPos opened = context.getAbsolutePos(BASE);
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		player.setPosition(opened.getX() + 0.5, opened.getY() + 1, opened.getZ() + 0.5);
		var factory = context.getWorld().getBlockState(opened).createScreenHandlerFactory(context.getWorld(), opened);
		context.assertTrue(factory != null && player.openHandledScreen(factory).isPresent(), "Chest should open");
		WorkshopSessionManager manager = WorkshopSessionManager.getInstance();
		manager.open(player, opened, WorkshopBlockType.CHEST);
		WorkshopSession session = manager.get(player.getUuid()).orElseThrow();
		ContainerLabelEditResult result = manager.updateContainerLabel(player, new UpdateContainerLabelPayload(
			session.sessionId(), session.revision(), session.syncId(), context.getAbsolutePos(BASE.add(2, 0, 0)),
			ContainerLabelOperation.SET_EXACT_ITEM, Optional.of(net.minecraft.registry.Registries.ITEM.getId(Items.IRON_INGOT))
		));
		context.assertEquals(ContainerLabelEditResult.INVALID_SESSION, result, "Payload cannot redirect editing to another chest");
		manager.clear(player, false);
		player.closeHandledScreen();
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void fabricTransferInventoryStorageHonorsLabel(TestContext context) {
		LogicalContainer container = labeledChest(context);
		InventoryStorage storage = InventoryStorage.of(container.inventory(), null);
		try (Transaction simulation = Transaction.openOuter()) {
			context.assertEquals(1L, storage.insert(ItemVariant.of(Items.IRON_INGOT), 1, simulation), "Simulation should report insertable iron");
		}
		context.assertTrue(container.inventory().isEmpty(), "Uncommitted transaction must not change the inventory");
		try (Transaction transaction = Transaction.openOuter()) {
			long rejected = storage.insert(ItemVariant.of(Items.GOLD_INGOT), 1, transaction);
			context.assertEquals(0L, rejected, "Transfer API should reject gold");
			long inserted = storage.insert(ItemVariant.of(Items.IRON_INGOT), 1, transaction);
			context.assertEquals(1L, inserted, "Transfer API should accept iron");
			transaction.commit();
		}
		context.assertEquals(1, container.inventory().count(Items.IRON_INGOT), "Committed transaction should contain iron");
		context.assertEquals(0, container.inventory().count(Items.GOLD_INGOT), "Rejected transaction must not contain gold");
		context.complete();
	}

	private static LogicalContainer labeledChest(TestContext context) {
		LogicalContainer container = placeAndResolve(context, Blocks.CHEST.getDefaultState());
		ContainerLabelService.applyAtomically(container, ironRule());
		return container;
	}

	private static LogicalContainer placeAndResolve(TestContext context, BlockState state) {
		context.setBlockState(BASE, state);
		WorkshopContainerResolver.Result result = WorkshopContainerResolver.resolve(context.getWorld(), context.getAbsolutePos(BASE));
		context.assertTrue(result.successful(), "Placed container should resolve");
		return result.container();
	}

	private static LogicalContainer doubleChest(TestContext context) {
		BlockState left = Blocks.CHEST.getDefaultState()
			.with(ChestBlock.FACING, Direction.NORTH)
			.with(ChestBlock.CHEST_TYPE, ChestType.LEFT);
		BlockState right = Blocks.CHEST.getDefaultState()
			.with(ChestBlock.FACING, Direction.NORTH)
			.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT);
		context.setBlockState(BASE, left);
		context.setBlockState(BASE.add(1, 0, 0), right);
		WorkshopContainerResolver.Result result = WorkshopContainerResolver.resolve(context.getWorld(), context.getAbsolutePos(BASE));
		context.assertTrue(result.successful(), "Double chest should resolve");
		return result.container();
	}

	private static ContainerLabelRule ironRule() {
		return ContainerLabelRule.exact(Items.IRON_INGOT);
	}
}
