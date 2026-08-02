package io.github.ikunkk02afk.workshopzone.session;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockCatalog;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

final class WorkshopTargetValidator {
	private WorkshopTargetValidator() {
	}

	static Result validate(Access access, WorkshopBlockEntry expected) {
		Objects.requireNonNull(access, "access");
		Objects.requireNonNull(expected, "expected");
		BlockPos position = expected.position();
		if (!access.isChunkLoaded(position)) {
			return new Result(WorkshopOpenResult.CHUNK_UNLOADED, null, null);
		}

		BlockState state = access.getBlockState(position);
		WorkshopBlockType actualType = state.isAir()
			? null
			: WorkshopBlockCatalog.vanilla().find(state.getBlock()).orElse(null);
		if (actualType != expected.type()
			|| !Registries.BLOCK.getId(state.getBlock()).equals(expected.blockId())) {
			return new Result(WorkshopOpenResult.BLOCK_CHANGED, state, actualType);
		}
		return new Result(WorkshopOpenResult.SUCCESS, state, actualType);
	}

	interface Access {
		boolean isChunkLoaded(BlockPos position);

		BlockState getBlockState(BlockPos position);
	}

	record Result(WorkshopOpenResult result, BlockState state, WorkshopBlockType type) {
	}
}
