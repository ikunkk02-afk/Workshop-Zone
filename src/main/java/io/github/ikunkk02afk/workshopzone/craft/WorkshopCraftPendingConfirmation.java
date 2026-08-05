package io.github.ikunkk02afk.workshopzone.craft;

import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record WorkshopCraftPendingConfirmation(
	long previewId,
	UUID playerId,
	long sessionId,
	long revision,
	int syncId,
	Identifier recipeId,
	WorkshopCraftMode craftMode,
	int plannedIterations,
	long createdAtTick,
	long expiresAtTick,
	Identifier previewOutputItemId,
	int previewOutputCount,
	List<WorkshopCraftMaterialSummary> previewMaterials
) {
	public WorkshopCraftPendingConfirmation {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(recipeId, "recipeId");
		Objects.requireNonNull(craftMode, "craftMode");
		Objects.requireNonNull(previewOutputItemId, "previewOutputItemId");
		previewMaterials = List.copyOf(previewMaterials);
		if (previewId <= 0 || sessionId < 0 || revision < 0 || syncId < 0
			|| plannedIterations <= 0 || plannedIterations > 64 || createdAtTick < 0
			|| craftMode == WorkshopCraftMode.SINGLE && plannedIterations != 1
			|| craftMode == WorkshopCraftMode.BATCH && plannedIterations <= 1
			|| expiresAtTick <= createdAtTick || previewOutputCount <= 0 || previewMaterials.size() > 9) {
			throw new IllegalArgumentException("Invalid pending workshop crafting confirmation");
		}
	}

	public WorkshopCraftPendingConfirmation(
		long previewId,
		UUID playerId,
		long sessionId,
		long revision,
		int syncId,
		Identifier recipeId,
		long createdAtTick,
		long expiresAtTick,
		Identifier previewOutputItemId,
		int previewOutputCount,
		List<WorkshopCraftMaterialSummary> previewMaterials
	) {
		this(
			previewId, playerId, sessionId, revision, syncId, recipeId, WorkshopCraftMode.SINGLE, 1,
			createdAtTick, expiresAtTick, previewOutputItemId, previewOutputCount, previewMaterials
		);
	}
}
