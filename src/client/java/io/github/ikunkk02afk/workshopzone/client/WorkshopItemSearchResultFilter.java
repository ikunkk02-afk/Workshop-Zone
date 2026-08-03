package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class WorkshopItemSearchResultFilter {
	private WorkshopItemSearchResultFilter() {
	}

	public static boolean matches(
		WorkshopItemSearchResultPayload payload,
		long pendingRequestId,
		long sessionId,
		long revision,
		int syncId
	) {
		return payload != null
			&& payload.requestId() == pendingRequestId
			&& payload.sessionId() == sessionId
			&& payload.revision() == revision
			&& payload.syncId() == syncId;
	}

	public static List<WorkshopItemSearchContainerResult> filterExisting(
		List<WorkshopItemSearchContainerResult> results,
		Set<BlockPos> snapshotPositions
	) {
		Objects.requireNonNull(results, "results");
		Objects.requireNonNull(snapshotPositions, "snapshotPositions");
		return results.stream().filter(result -> snapshotPositions.contains(result.representativePosition())).toList();
	}
}
