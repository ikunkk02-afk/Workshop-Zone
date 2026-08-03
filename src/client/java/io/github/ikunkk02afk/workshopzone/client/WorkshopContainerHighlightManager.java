package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WorkshopContainerHighlightManager {
	public static final int MAX_LOGICAL_CONTAINERS = 64;
	public static final int MAX_BLOCK_POSITIONS = 128;
	public static final long DEFAULT_DURATION_MILLIS = 5_000L;
	private final List<WorkshopContainerHighlight> highlights = new ArrayList<>();
	private Identifier dimensionId;

	public void highlightOne(
		WorkshopItemSearchContainerResult result,
		Identifier targetItemId,
		Identifier dimensionId,
		long nowMillis
	) {
		Objects.requireNonNull(result, "result");
		this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
		highlights.clear();
		highlights.add(toHighlight(result, targetItemId, nowMillis, true));
		WorkshopZone.LOGGER.debug("Highlighted one workshop container using {} block positions", result.highlightPositions().size());
	}

	public void highlightAll(
		List<WorkshopItemSearchContainerResult> results,
		Identifier targetItemId,
		Identifier dimensionId,
		long nowMillis
	) {
		Objects.requireNonNull(results, "results");
		this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
		highlights.clear();
		int positions = 0;
		for (WorkshopItemSearchContainerResult result : results) {
			if (highlights.size() >= MAX_LOGICAL_CONTAINERS
				|| positions + result.highlightPositions().size() > MAX_BLOCK_POSITIONS) {
				break;
			}
			highlights.add(toHighlight(result, targetItemId, nowMillis, false));
			positions += result.highlightPositions().size();
		}
		WorkshopZone.LOGGER.debug("Highlighted {} workshop containers using {} block positions", highlights.size(), positions);
	}

	public List<WorkshopContainerHighlight> active(long nowMillis, Identifier currentDimensionId) {
		if (dimensionId == null || !dimensionId.equals(currentDimensionId)) {
			clear();
			return List.of();
		}
		highlights.removeIf(highlight -> !highlight.active(nowMillis));
		if (highlights.isEmpty()) {
			dimensionId = null;
		}
		return List.copyOf(highlights);
	}

	public void clear() {
		highlights.clear();
		dimensionId = null;
	}

	private static WorkshopContainerHighlight toHighlight(
		WorkshopItemSearchContainerResult result,
		Identifier targetItemId,
		long nowMillis,
		boolean selected
	) {
		return new WorkshopContainerHighlight(
			result.representativePosition(), result.highlightPositions(), targetItemId,
			result.containerItemCount(), nowMillis, nowMillis + DEFAULT_DURATION_MILLIS, selected
		);
	}
}
