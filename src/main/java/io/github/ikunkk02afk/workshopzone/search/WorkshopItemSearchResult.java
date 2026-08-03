package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.network.SearchWorkshopItemPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;

public record WorkshopItemSearchResult(
	WorkshopItemSearchResultCode resultCode,
	Identifier targetItemId,
	long totalItemCount,
	int totalMatchingContainers,
	boolean truncated,
	List<WorkshopItemSearchContainerResult> containers,
	int candidateContainerCount,
	int accessibleContainerCount
) {
	public WorkshopItemSearchResult {
		Objects.requireNonNull(resultCode, "resultCode");
		Objects.requireNonNull(targetItemId, "targetItemId");
		containers = List.copyOf(containers);
		if (totalItemCount < 0 || totalMatchingContainers < 0 || candidateContainerCount < 0
			|| accessibleContainerCount < 0 || accessibleContainerCount > candidateContainerCount
			|| containers.size() > WorkshopItemSearchResultPayload.MAX_RESULTS
			|| totalMatchingContainers < containers.size()
			|| truncated != (totalMatchingContainers > containers.size())) {
			throw new IllegalArgumentException("Invalid workshop item search result");
		}
	}

	public WorkshopItemSearchResultPayload toPayload(SearchWorkshopItemPayload request) {
		return new WorkshopItemSearchResultPayload(
			request.requestId(), request.sessionId(), request.revision(), request.syncId(),
			resultCode, targetItemId, totalItemCount, totalMatchingContainers, truncated, containers
		);
	}

	public static WorkshopItemSearchResult empty(
		WorkshopItemSearchResultCode resultCode,
		Identifier targetItemId,
		int candidateContainerCount,
		int accessibleContainerCount
	) {
		return new WorkshopItemSearchResult(
			resultCode, targetItemId, 0, 0, false, List.of(), candidateContainerCount, accessibleContainerCount
		);
	}
}
