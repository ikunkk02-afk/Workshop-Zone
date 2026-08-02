package io.github.ikunkk02afk.workshopzone.scan;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

public final class WorkshopAreaScannerGameTests implements FabricGameTest {
	private static final WorkshopAreaScanner SCANNER = new WorkshopAreaScanner();
	private static final BlockPos CENTER = new BlockPos(4, 4, 4);

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void emptyAreaReturnsNoResults(TestContext context) {
		WorkshopScanResult result = scan(context, 3, 2);
		context.assertEquals(0, result.size(), "Empty area should not contain workshop blocks");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void recognizesSingleAndTrappedChests(TestContext context) {
		context.setBlockState(CENTER.add(1, 0, 0), Blocks.CHEST);
		context.setBlockState(CENTER.add(-1, 0, 0), Blocks.TRAPPED_CHEST);

		WorkshopScanResult result = scan(context, 3, 1);
		context.assertEquals(2, result.size(), "Both supported chest variants should be found");
		context.assertEquals(2, result.containerCount(), "Both chest variants should count as containers");
		assertTypes(context, result, WorkshopBlockType.CHEST, WorkshopBlockType.TRAPPED_CHEST);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void doubleChestUsesOneStableRepresentative(TestContext context) {
		BlockPos left = CENTER.add(-1, 0, 0);
		BlockPos right = CENTER;
		BlockState leftState = Blocks.CHEST.getDefaultState()
			.with(ChestBlock.FACING, Direction.NORTH)
			.with(ChestBlock.CHEST_TYPE, ChestType.LEFT);
		BlockState rightState = Blocks.CHEST.getDefaultState()
			.with(ChestBlock.FACING, Direction.NORTH)
			.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT);
		context.setBlockState(left, leftState);
		context.setBlockState(right, rightState);

		WorkshopScanResult result = scan(context, 3, 1);
		context.assertEquals(1, result.size(), "A double chest should be counted once");
		context.assertEquals(
			context.getAbsolutePos(left),
			result.entries().getFirst().position(),
			"The lower X position should be the stable representative"
		);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void recognizesBarrelAndCraftingTable(TestContext context) {
		context.setBlockState(CENTER.add(1, 0, 0), Blocks.BARREL);
		context.setBlockState(CENTER.add(2, 0, 0), Blocks.CRAFTING_TABLE);

		WorkshopScanResult result = scan(context, 3, 1);
		context.assertEquals(2, result.size(), "Barrel and crafting table should be found");
		context.assertEquals(1, result.containerCount(), "Only the barrel should count as a container");
		context.assertEquals(1, result.processingDeviceCount(), "Crafting table should count as a work device");
		assertTypes(context, result, WorkshopBlockType.BARREL, WorkshopBlockType.CRAFTING_TABLE);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void recognizesAllSupportedFurnaces(TestContext context) {
		context.setBlockState(CENTER.add(1, 0, 0), Blocks.FURNACE);
		context.setBlockState(CENTER.add(2, 0, 0), Blocks.BLAST_FURNACE);
		context.setBlockState(CENTER.add(3, 0, 0), Blocks.SMOKER);

		WorkshopScanResult result = scan(context, 4, 1);
		context.assertEquals(3, result.size(), "All three supported furnace variants should be found");
		context.assertEquals(3, result.processingDeviceCount(), "All furnace variants should count as work devices");
		assertTypes(
			context,
			result,
			WorkshopBlockType.FURNACE,
			WorkshopBlockType.BLAST_FURNACE,
			WorkshopBlockType.SMOKER
		);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void recognizesAdditionalVanillaWorkstations(TestContext context) {
		context.setBlockState(CENTER.add(1, 0, 0), Blocks.SMITHING_TABLE);
		context.setBlockState(CENTER.add(2, 0, 0), Blocks.STONECUTTER);
		context.setBlockState(CENTER.add(3, 0, 0), Blocks.GRINDSTONE);
		context.setBlockState(CENTER.add(4, 0, 0), Blocks.LOOM);
		context.setBlockState(CENTER.add(1, 1, 0), Blocks.CARTOGRAPHY_TABLE);
		context.setBlockState(CENTER.add(2, 1, 0), Blocks.BREWING_STAND);
		context.setBlockState(CENTER.add(3, 1, 0), Blocks.ENCHANTING_TABLE);

		WorkshopScanResult result = scan(context, 5, 2);
		context.assertEquals(7, result.size(), "All additional workstation families should be found");
		context.assertEquals(7, result.processingDeviceCount(), "Every additional workstation should be categorized as a workstation");
		assertTypes(
			context, result,
			WorkshopBlockType.SMITHING_TABLE,
			WorkshopBlockType.STONECUTTER,
			WorkshopBlockType.GRINDSTONE,
			WorkshopBlockType.LOOM,
			WorkshopBlockType.CARTOGRAPHY_TABLE,
			WorkshopBlockType.BREWING_STAND,
			WorkshopBlockType.ENCHANTING_TABLE
		);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void allAnvilStatesShareTypeButKeepActualBlockIds(TestContext context) {
		BlockPos anvil = CENTER.add(1, 0, 0);
		BlockPos chipped = CENTER.add(2, 0, 0);
		BlockPos damaged = CENTER.add(3, 0, 0);
		context.setBlockState(anvil, Blocks.ANVIL);
		context.setBlockState(chipped, Blocks.CHIPPED_ANVIL);
		context.setBlockState(damaged, Blocks.DAMAGED_ANVIL);

		WorkshopScanResult result = scan(context, 4, 1);
		context.assertEquals(3, result.size(), "All three anvil blocks should be found");
		for (WorkshopBlockEntry entry : result.entries()) {
			context.assertEquals(WorkshopBlockType.ANVIL, entry.type(), "All anvil variants should share ANVIL type");
		}
		List<net.minecraft.util.Identifier> ids = result.entries().stream().map(WorkshopBlockEntry::blockId).toList();
		context.assertTrue(ids.contains(Registries.BLOCK.getId(Blocks.ANVIL)), "Anvil id should be preserved");
		context.assertTrue(ids.contains(Registries.BLOCK.getId(Blocks.CHIPPED_ANVIL)), "Chipped anvil id should be preserved");
		context.assertTrue(ids.contains(Registries.BLOCK.getId(Blocks.DAMAGED_ANVIL)), "Damaged anvil id should be preserved");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void excludesUnsupportedContainersAndMachines(TestContext context) {
		context.setBlockState(CENTER.add(1, 0, 0), Blocks.HOPPER);
		context.setBlockState(CENTER.add(2, 0, 0), Blocks.WHITE_SHULKER_BOX);
		context.setBlockState(CENTER.add(3, 0, 0), Blocks.DISPENSER);
		context.setBlockState(CENTER.add(-1, 0, 0), Blocks.DROPPER);
		context.setBlockState(CENTER.add(-2, 0, 0), Blocks.FLETCHING_TABLE);

		WorkshopScanResult result = scan(context, 4, 1);
		context.assertEquals(0, result.size(), "Unsupported containers and machines must be excluded");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void sortsResultsByDistance(TestContext context) {
		context.setBlockState(CENTER.add(3, 0, 0), Blocks.FURNACE);
		context.setBlockState(CENTER.add(1, 0, 0), Blocks.BARREL);
		context.setBlockState(CENTER.add(2, 0, 0), Blocks.CRAFTING_TABLE);

		WorkshopScanResult result = scan(context, 4, 1);
		List<WorkshopBlockType> types = result.entries().stream().map(WorkshopBlockEntry::type).toList();
		context.assertEquals(
			List.of(WorkshopBlockType.BARREL, WorkshopBlockType.CRAFTING_TABLE, WorkshopBlockType.FURNACE),
			types,
			"Results should be sorted from nearest to farthest"
		);
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void excludesBlocksOutsideHorizontalAndVerticalRange(TestContext context) {
		context.setBlockState(CENTER.add(1, 0, 0), Blocks.BARREL);
		context.setBlockState(CENTER.add(2, 0, 0), Blocks.CHEST);
		context.setBlockState(CENTER.add(0, 2, 0), Blocks.FURNACE);

		WorkshopScanResult result = scan(context, 1, 1);
		context.assertEquals(1, result.size(), "Only the block inside both radii should be found");
		context.assertEquals(WorkshopBlockType.BARREL, result.entries().getFirst().type(), "Barrel should remain");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void unloadedChunksAreNeverRead(TestContext context) {
		CountingUnloadedAccess access = new CountingUnloadedAccess();
		WorkshopScanResult result = SCANNER.scan(access, BlockPos.ORIGIN, 8, 4);

		context.assertEquals(0, result.size(), "Unloaded chunks should produce no results");
		context.assertEquals(0, access.blockReads, "Block states in unloaded chunks must never be read");
		context.complete();
	}

	private static WorkshopScanResult scan(TestContext context, int horizontalRadius, int verticalRadius) {
		return SCANNER.scan(
			context.getWorld(),
			context.getAbsolutePos(CENTER),
			horizontalRadius,
			verticalRadius
		);
	}

	private static void assertTypes(
		TestContext context,
		WorkshopScanResult result,
		WorkshopBlockType... expectedTypes
	) {
		List<WorkshopBlockType> actualTypes = result.entries().stream()
			.map(WorkshopBlockEntry::type)
			.toList();
		for (WorkshopBlockType expectedType : expectedTypes) {
			context.assertTrue(actualTypes.contains(expectedType), "Missing expected type: " + expectedType);
		}
	}

	private static final class CountingUnloadedAccess implements WorkshopAreaScanner.ScanWorldAccess {
		private int blockReads;

		@Override
		public boolean isChunkLoaded(int chunkX, int chunkZ) {
			return false;
		}

		@Override
		public boolean isInBuildLimit(BlockPos position) {
			return true;
		}

		@Override
		public BlockState getBlockState(BlockPos position) {
			blockReads++;
			return Blocks.CHEST.getDefaultState();
		}
	}
}
