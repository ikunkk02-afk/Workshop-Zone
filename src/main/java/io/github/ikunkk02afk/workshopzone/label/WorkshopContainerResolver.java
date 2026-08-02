package io.github.ikunkk02afk.workshopzone.label;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopAreaScanner;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockCatalog;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;

import java.util.List;

public final class WorkshopContainerResolver {
	private WorkshopContainerResolver() {
	}

	public static Result resolve(ServerWorld world, BlockPos requestedPosition) {
		if (!isLoaded(world, requestedPosition)) {
			return Result.failure(Status.CHUNK_UNLOADED);
		}
		BlockState state = world.getBlockState(requestedPosition);
		WorkshopBlockType type = WorkshopBlockCatalog.vanilla().find(state.getBlock()).orElse(null);
		if (type == null || !type.isContainer()) {
			return Result.failure(Status.NOT_CONTAINER);
		}
		if (state.getBlock() instanceof BarrelBlock) {
			BlockEntity blockEntity = world.getBlockEntity(requestedPosition);
			if (!(blockEntity instanceof BarrelBlockEntity) || !(blockEntity instanceof ContainerLabelHolder)) {
				return Result.failure(Status.BLOCK_CHANGED);
			}
			return Result.success(new LogicalContainer(
				requestedPosition, List.of(requestedPosition), (Inventory)blockEntity, List.of(blockEntity), type, false
			));
		}
		if (!(state.getBlock() instanceof ChestBlock chestBlock) || !state.contains(ChestBlock.CHEST_TYPE)) {
			return Result.failure(Status.BLOCK_CHANGED);
		}
		BlockPos representative = WorkshopAreaScanner.representativePosition(requestedPosition, state);
		if (!representative.equals(requestedPosition)) {
			if (!isLoaded(world, representative)) {
				return Result.failure(Status.CHUNK_UNLOADED);
			}
			state = world.getBlockState(representative);
			if (state.getBlock() != chestBlock) {
				return Result.failure(Status.BLOCK_CHANGED);
			}
		}
		BlockEntity first = world.getBlockEntity(representative);
		if (!(first instanceof ChestBlockEntity) || !(first instanceof ContainerLabelHolder)) {
			return Result.failure(Status.BLOCK_CHANGED);
		}
		if (state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
			return Result.success(new LogicalContainer(
				representative, List.of(representative), (Inventory)first, List.of(first), type, false
			));
		}
		BlockPos otherPosition = representative.offset(ChestBlock.getFacing(state));
		if (!isLoaded(world, otherPosition)) {
			return Result.failure(Status.CHUNK_UNLOADED);
		}
		BlockState otherState = world.getBlockState(otherPosition);
		if (otherState.getBlock() != chestBlock
			|| !otherState.contains(ChestBlock.CHEST_TYPE)
			|| otherState.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
			|| !otherPosition.offset(ChestBlock.getFacing(otherState)).equals(representative)
			|| !WorkshopAreaScanner.representativePosition(otherPosition, otherState).equals(representative)) {
			return Result.failure(Status.BLOCK_CHANGED);
		}
		BlockEntity second = world.getBlockEntity(otherPosition);
		if (!(second instanceof ChestBlockEntity) || !(second instanceof ContainerLabelHolder)) {
			return Result.failure(Status.BLOCK_CHANGED);
		}
		Inventory inventory = ChestBlock.getInventory(chestBlock, state, world, representative, true);
		if (inventory == null || inventory.size() != 54) {
			return Result.failure(Status.BLOCK_CHANGED);
		}
		return Result.success(new LogicalContainer(
			representative, List.of(representative, otherPosition), inventory, List.of(first, second), type, true
		));
	}

	private static boolean isLoaded(ServerWorld world, BlockPos position) {
		return world.getChunkManager().isChunkLoaded(
			ChunkSectionPos.getSectionCoord(position.getX()), ChunkSectionPos.getSectionCoord(position.getZ())
		);
	}

	public enum Status {
		SUCCESS,
		NOT_CONTAINER,
		CHUNK_UNLOADED,
		BLOCK_CHANGED
	}

	public record Result(Status status, LogicalContainer container) {
		static Result success(LogicalContainer container) {
			return new Result(Status.SUCCESS, container);
		}

		static Result failure(Status status) {
			return new Result(status, null);
		}

		public boolean successful() {
			return status == Status.SUCCESS;
		}
	}
}
