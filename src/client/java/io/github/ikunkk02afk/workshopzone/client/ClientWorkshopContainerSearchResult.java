package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;

import java.util.Objects;

public record ClientWorkshopContainerSearchResult(
	WorkshopItemSearchContainerResult serverResult,
	ClientWorkshopEntry workshopEntry
) {
	public ClientWorkshopContainerSearchResult {
		Objects.requireNonNull(serverResult, "serverResult");
		Objects.requireNonNull(workshopEntry, "workshopEntry");
		if (!serverResult.representativePosition().equals(workshopEntry.position())) {
			throw new IllegalArgumentException("Workshop search result must match its snapshot entry");
		}
	}
}
