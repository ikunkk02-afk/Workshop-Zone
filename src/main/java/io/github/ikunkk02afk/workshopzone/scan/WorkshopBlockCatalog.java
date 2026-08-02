package io.github.ikunkk02afk.workshopzone.scan;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorkshopBlockCatalog {
	private static final WorkshopBlockCatalog VANILLA = builder()
		.register(Blocks.CHEST, WorkshopBlockType.CHEST)
		.register(Blocks.TRAPPED_CHEST, WorkshopBlockType.TRAPPED_CHEST)
		.register(Blocks.BARREL, WorkshopBlockType.BARREL)
		.register(Blocks.CRAFTING_TABLE, WorkshopBlockType.CRAFTING_TABLE)
		.register(Blocks.FURNACE, WorkshopBlockType.FURNACE)
		.register(Blocks.BLAST_FURNACE, WorkshopBlockType.BLAST_FURNACE)
		.register(Blocks.SMOKER, WorkshopBlockType.SMOKER)
		.register(Blocks.SMITHING_TABLE, WorkshopBlockType.SMITHING_TABLE)
		.register(Blocks.ANVIL, WorkshopBlockType.ANVIL)
		.register(Blocks.CHIPPED_ANVIL, WorkshopBlockType.ANVIL)
		.register(Blocks.DAMAGED_ANVIL, WorkshopBlockType.ANVIL)
		.register(Blocks.STONECUTTER, WorkshopBlockType.STONECUTTER)
		.register(Blocks.GRINDSTONE, WorkshopBlockType.GRINDSTONE)
		.register(Blocks.LOOM, WorkshopBlockType.LOOM)
		.register(Blocks.CARTOGRAPHY_TABLE, WorkshopBlockType.CARTOGRAPHY_TABLE)
		.register(Blocks.BREWING_STAND, WorkshopBlockType.BREWING_STAND)
		.register(Blocks.ENCHANTING_TABLE, WorkshopBlockType.ENCHANTING_TABLE)
		.build();

	private final Map<Block, WorkshopBlockType> types;

	private WorkshopBlockCatalog(Map<Block, WorkshopBlockType> types) {
		this.types = Map.copyOf(types);
	}

	public static WorkshopBlockCatalog vanilla() {
		return VANILLA;
	}

	public static Builder builder() {
		return new Builder();
	}

	public Optional<WorkshopBlockType> find(Block block) {
		return Optional.ofNullable(types.get(block));
	}

	public static final class Builder {
		private final Map<Block, WorkshopBlockType> types = new LinkedHashMap<>();

		public Builder register(Block block, WorkshopBlockType type) {
			Objects.requireNonNull(block, "block");
			Objects.requireNonNull(type, "type");
			if (types.putIfAbsent(block, type) != null) {
				throw new IllegalArgumentException("Block is already registered: " + block);
			}
			return this;
		}

		public WorkshopBlockCatalog build() {
			return new WorkshopBlockCatalog(types);
		}
	}
}
