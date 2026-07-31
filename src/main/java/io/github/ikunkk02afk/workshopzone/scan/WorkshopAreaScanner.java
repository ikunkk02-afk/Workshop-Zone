package io.github.ikunkk02afk.workshopzone.scan;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WorkshopAreaScanner {
	public static final int DEFAULT_HORIZONTAL_RADIUS = 8;
	public static final int DEFAULT_VERTICAL_RADIUS = 4;

	private static final Comparator<BlockPos> POSITION_ORDER = Comparator
		.comparingInt(BlockPos::getX)
		.thenComparingInt(BlockPos::getY)
		.thenComparingInt(BlockPos::getZ);
	private static final Comparator<WorkshopBlockEntry> ENTRY_ORDER = Comparator
		.comparingDouble(WorkshopBlockEntry::distanceSquared)
		.thenComparing(WorkshopBlockEntry::position, POSITION_ORDER)
		.thenComparing(entry -> entry.blockId().toString());

	private final WorkshopBlockCatalog catalog;

	public WorkshopAreaScanner() {
		this(WorkshopBlockCatalog.vanilla());
	}

	public WorkshopAreaScanner(WorkshopBlockCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
	}

	public WorkshopScanResult scan(
		ServerWorld world,
		BlockPos center,
		int horizontalRadius,
		int verticalRadius
	) {
		Objects.requireNonNull(world, "world");
		return scan(new ServerWorldAccess(world), center, horizontalRadius, verticalRadius);
	}

	WorkshopScanResult scan(
		ScanWorldAccess world,
		BlockPos center,
		int horizontalRadius,
		int verticalRadius
	) {
		Objects.requireNonNull(world, "world");
		Objects.requireNonNull(center, "center");
		if (horizontalRadius < 0 || verticalRadius < 0) {
			throw new IllegalArgumentException("Scan radii must not be negative");
		}

		BlockPos immutableCenter = center.toImmutable();
		Map<BlockPos, WorkshopBlockEntry> uniqueEntries = new LinkedHashMap<>();
		int minY = center.getY() - verticalRadius;
		int maxY = center.getY() + verticalRadius;

		for (int x = center.getX() - horizontalRadius; x <= center.getX() + horizontalRadius; x++) {
			int chunkX = ChunkSectionPos.getSectionCoord(x);
			for (int z = center.getZ() - horizontalRadius; z <= center.getZ() + horizontalRadius; z++) {
				int chunkZ = ChunkSectionPos.getSectionCoord(z);
				if (!world.isChunkLoaded(chunkX, chunkZ)) {
					continue;
				}

				for (int y = minY; y <= maxY; y++) {
					BlockPos position = new BlockPos(x, y, z);
					if (!world.isInBuildLimit(position)) {
						continue;
					}

					BlockState state = world.getBlockState(position);
					WorkshopBlockType type = catalog.find(state.getBlock()).orElse(null);
					if (type == null) {
						continue;
					}

					BlockPos representative = getRepresentativePosition(position, state);
					uniqueEntries.computeIfAbsent(representative, key -> WorkshopBlockEntry.create(
						type,
						key,
						Registries.BLOCK.getId(state.getBlock()),
						key.getSquaredDistance(immutableCenter)
					));
				}
			}
		}

		List<WorkshopBlockEntry> sortedEntries = new ArrayList<>(uniqueEntries.values());
		sortedEntries.sort(ENTRY_ORDER);
		return WorkshopScanResult.create(
			immutableCenter,
			horizontalRadius,
			verticalRadius,
			sortedEntries
		);
	}

	static BlockPos getRepresentativePosition(BlockPos position, BlockState state) {
		if (!(state.getBlock() instanceof ChestBlock)
			|| !state.contains(ChestBlock.CHEST_TYPE)
			|| state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
			return position.toImmutable();
		}

		BlockPos otherHalf = position.offset(ChestBlock.getFacing(state));
		return POSITION_ORDER.compare(position, otherHalf) <= 0
			? position.toImmutable()
			: otherHalf.toImmutable();
	}

	interface ScanWorldAccess {
		boolean isChunkLoaded(int chunkX, int chunkZ);

		boolean isInBuildLimit(BlockPos position);

		BlockState getBlockState(BlockPos position);
	}

	private record ServerWorldAccess(ServerWorld world) implements ScanWorldAccess {
		@Override
		public boolean isChunkLoaded(int chunkX, int chunkZ) {
			return world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
		}

		@Override
		public boolean isInBuildLimit(BlockPos position) {
			return world.isInBuildLimit(position);
		}

		@Override
		public BlockState getBlockState(BlockPos position) {
			return world.getBlockState(position);
		}
	}
}
