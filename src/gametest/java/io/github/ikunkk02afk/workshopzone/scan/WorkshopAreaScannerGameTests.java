package io.github.ikunkk02afk.workshopzone.scan;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
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
	public void excludesUnsupportedContainersAndMachines(TestContext context) {
		context.setBlockState(CENTER.add(1, 0, 0), Blocks.HOPPER);
		context.setBlockState(CENTER.add(2, 0, 0), Blocks.WHITE_SHULKER_BOX);
		context.setBlockState(CENTER.add(3, 0, 0), Blocks.DISPENSER);
		context.setBlockState(CENTER.add(-1, 0, 0), Blocks.DROPPER);

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
