package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;

public record ClientWorkshopSearchResult(
	Identifier targetItemId,
	long totalItemCount,
	int totalMatchingContainers,
	boolean truncated,
	List<ClientWorkshopContainerSearchResult> containers
) {
	public ClientWorkshopSearchResult {
		Objects.requireNonNull(targetItemId, "targetItemId");
		containers = List.copyOf(containers);
		if (totalItemCount < 0 || totalMatchingContainers < containers.size()) {
			throw new IllegalArgumentException("Invalid client workshop search result");
		}
	}
}
