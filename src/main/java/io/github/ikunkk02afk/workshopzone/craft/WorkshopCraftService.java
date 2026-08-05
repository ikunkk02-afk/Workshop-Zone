package io.github.ikunkk02afk.workshopzone.craft;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.network.ConfirmWorkshopCraftPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftExecutionResultPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftPreviewPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionValidation;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.registry.Registries;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class WorkshopCraftService {
	public static final int PREVIEW_COOLDOWN_TICKS = 5;
	public static final int CONFIRM_COOLDOWN_TICKS = 5;
	public static final int PREVIEW_LIFETIME_TICKS = 200;

	private final WorkshopSessionManager sessions;
	private final WorkshopCraftPlanBuilder planBuilder;
	private final WorkshopCraftPendingStore pending = new WorkshopCraftPendingStore();
	private final AtomicLong nextPreviewId = new AtomicLong();
	private final Map<UUID, Long> lastPreviewTicks = new HashMap<>();
	private final Map<UUID, Long> lastConfirmTicks = new HashMap<>();

	public WorkshopCraftService(WorkshopSessionManager sessions) {
		this(sessions, new WorkshopCraftPlanBuilder());
	}

	public WorkshopCraftService(WorkshopSessionManager sessions, WorkshopCraftPlanBuilder planBuilder) {
		this.sessions = sessions;
		this.planBuilder = planBuilder;
	}

	public WorkshopCraftPreviewPayload preview(
		ServerPlayerEntity player,
		int syncId,
		Identifier recipeId,
		boolean craftAll
	) {
		WorkshopSession session = sessions.get(player.getUuid()).orElse(null);
		pending.clear(player.getUuid());
		WorkshopCraftMode requestedMode = craftAll ? WorkshopCraftMode.BATCH : WorkshopCraftMode.SINGLE;
		RecipeEntry<?> entry = player.getServer().getRecipeManager().get(recipeId).orElse(null);
		if (entry == null || !player.getRecipeBook().contains(entry)) {
			return failure(session, syncId, recipeId, WorkshopCraftPreviewResultCode.UNSUPPORTED_RECIPE, ItemStack.EMPTY);
		}
		if (requestedMode == WorkshopCraftMode.SINGLE) {
			try {
				RecipeMatcher vanillaPlayerMatcher = new RecipeMatcher();
				player.getInventory().populateRecipeFinder(vanillaPlayerMatcher);
				if (vanillaPlayerMatcher.match(entry.value(), null)) {
					return failure(
						session, syncId, recipeId, WorkshopCraftPreviewResultCode.NOT_NEEDED,
						WorkshopCraftMode.SINGLE, ItemStack.EMPTY
					);
				}
			} catch (RuntimeException exception) {
				WorkshopZone.LOGGER.debug("Could not safely inspect vanilla player recipe availability for {}", recipeId, exception);
			}
		}
		if (player.isSpectator()
			|| !(player.currentScreenHandler instanceof CraftingScreenHandler handler)
			|| handler.syncId != syncId || !handler.canUse(player)) {
			return failure(session, syncId, recipeId, WorkshopCraftPreviewResultCode.INVALID_SESSION, ItemStack.EMPTY);
		}
		if (!isGridEmpty(handler)) {
			return failure(session, syncId, recipeId, WorkshopCraftPreviewResultCode.GRID_NOT_EMPTY, ItemStack.EMPTY);
		}
		WorkshopCraftParsedRecipe parsed = WorkshopCraftRecipeParser.parse(entry, player.getServerWorld().getRegistryManager()).orElse(null);
		if (parsed == null) {
			return failure(session, syncId, recipeId, WorkshopCraftPreviewResultCode.UNSUPPORTED_RECIPE, ItemStack.EMPTY);
		}
		if (session == null || session.sessionId() < 0 || session.revision() < 0
			|| session.syncId() != syncId || session.openedBlockType() != WorkshopBlockType.CRAFTING_TABLE
			|| sessions.validate(player, session) != WorkshopSessionValidation.VALID) {
			return failure(session, syncId, recipeId, WorkshopCraftPreviewResultCode.INVALID_SESSION, parsed.output());
		}
		long now = player.getServerWorld().getTime();
		long previous = lastPreviewTicks.getOrDefault(player.getUuid(), now - PREVIEW_COOLDOWN_TICKS);
		if (now - previous < PREVIEW_COOLDOWN_TICKS) {
			return failure(session, syncId, recipeId, WorkshopCraftPreviewResultCode.COOLDOWN, parsed.output());
		}
		lastPreviewTicks.put(player.getUuid(), now);
		WorkshopCraftPlanBuildResult built = planBuilder.build(player, session, parsed, handler, requestedMode, 0);
		if (built.status() != WorkshopCraftPlanStatus.AVAILABLE) {
			return failure(session, syncId, recipeId, previewResult(built.status()), parsed.output());
		}
		WorkshopCraftPlan plan = built.plan();
		if (plan.storageItemCount() == 0
			|| requestedMode == WorkshopCraftMode.BATCH
				&& plan.combinedMaxIterations() <= plan.playerOnlyMaxIterations()) {
			return failure(
				session, syncId, recipeId, WorkshopCraftPreviewResultCode.NOT_NEEDED, requestedMode, parsed.output()
			);
		}
		long previewId = nextPreviewId.incrementAndGet();
		long expiresAt = now + PREVIEW_LIFETIME_TICKS;
		WorkshopCraftPendingConfirmation confirmation = new WorkshopCraftPendingConfirmation(
			previewId, player.getUuid(), session.sessionId(), session.revision(), syncId, recipeId,
			plan.craftMode(), plan.plannedIterations(), now, expiresAt,
			Registries.ITEM.getId(plan.recipe().output().getItem()), plan.recipe().output().getCount(),
			plan.materialSummaries()
		);
		pending.put(confirmation);
		return new WorkshopCraftPreviewPayload(
			previewId, session.sessionId(), session.revision(), syncId, recipeId,
			WorkshopCraftPreviewResultCode.AVAILABLE, plan.craftMode(), plan.recipe().output(), plan.materialSummaries(),
			plan.plannedIterations(), plan.recipe().output().getCount(),
			(long)plan.recipe().output().getCount() * plan.plannedIterations(),
			plan.playerOnlyMaxIterations(), plan.combinedMaxIterations(),
			plan.storageItemCount(), plan.usedContainerCount(), PREVIEW_LIFETIME_TICKS
		);
	}

	public WorkshopCraftExecutionResultPayload confirm(
		ServerPlayerEntity player,
		ConfirmWorkshopCraftPayload request
	) {
		WorkshopCraftPendingConfirmation confirmation = pending.consume(player.getUuid(), request.previewId()).orElse(null);
		if (confirmation == null) {
			return executionFailure(
				request.previewId(), 0, player.currentScreenHandler.syncId, Identifier.ofVanilla("air"),
				WorkshopCraftExecutionResultCode.INVALID_CONFIRMATION
			);
		}
		if (!request.accept()) {
			return executionFailure(confirmation, WorkshopCraftExecutionResultCode.CANCELLED);
		}
		long now = player.getServerWorld().getTime();
		long previous = lastConfirmTicks.getOrDefault(player.getUuid(), now - CONFIRM_COOLDOWN_TICKS);
		lastConfirmTicks.put(player.getUuid(), now);
		if (now - previous < CONFIRM_COOLDOWN_TICKS) {
			return executionFailure(confirmation, WorkshopCraftExecutionResultCode.INVALID_CONFIRMATION);
		}
		WorkshopSession session = sessions.get(player.getUuid()).orElse(null);
		WorkshopCraftPendingValidation validation = WorkshopCraftPendingChecks.validate(
			confirmation, request.previewId(), player.getUuid(),
			session == null ? -1 : session.sessionId(), session == null ? -1 : session.revision(),
			player.currentScreenHandler.syncId, now
		);
		if (validation != WorkshopCraftPendingValidation.VALID) {
			WorkshopCraftExecutionResultCode result = switch (validation) {
				case EXPIRED -> WorkshopCraftExecutionResultCode.EXPIRED;
				case STALE_SESSION -> WorkshopCraftExecutionResultCode.STALE_SESSION;
				case INVALID_CONFIRMATION -> WorkshopCraftExecutionResultCode.INVALID_CONFIRMATION;
				case VALID -> throw new IllegalStateException("Unexpected valid confirmation branch");
			};
			return executionFailure(confirmation, result);
		}
		if (player.isSpectator()
			|| session.openedBlockType() != WorkshopBlockType.CRAFTING_TABLE
			|| sessions.validate(player, session) != WorkshopSessionValidation.VALID
			|| !(player.currentScreenHandler instanceof CraftingScreenHandler handler)
			|| !handler.canUse(player)) {
			return executionFailure(confirmation, WorkshopCraftExecutionResultCode.STALE_SESSION);
		}
		if (!isGridEmpty(handler)) {
			return executionFailure(confirmation, WorkshopCraftExecutionResultCode.GRID_CHANGED);
		}
		RecipeEntry<?> currentEntry = player.getServer().getRecipeManager().get(confirmation.recipeId()).orElse(null);
		if (currentEntry == null || !player.getRecipeBook().contains(currentEntry)) {
			return executionFailure(confirmation, WorkshopCraftExecutionResultCode.RECIPE_CHANGED);
		}
		WorkshopCraftParsedRecipe parsed = WorkshopCraftRecipeParser.parse(
			currentEntry, player.getServerWorld().getRegistryManager()
		).orElse(null);
		if (parsed == null
			|| !Registries.ITEM.getId(parsed.output().getItem()).equals(confirmation.previewOutputItemId())
			|| parsed.output().getCount() != confirmation.previewOutputCount()) {
			return executionFailure(confirmation, WorkshopCraftExecutionResultCode.RECIPE_CHANGED);
		}
		WorkshopCraftPlanBuildResult rebuilt = planBuilder.build(
			player, session, parsed, handler, confirmation.craftMode(), confirmation.plannedIterations()
		);
		if (rebuilt.status() != WorkshopCraftPlanStatus.AVAILABLE) {
			WorkshopCraftExecutionResultCode result = confirmation.craftMode() == WorkshopCraftMode.BATCH
				&& rebuilt.status() != WorkshopCraftPlanStatus.DENIED
				&& rebuilt.status() != WorkshopCraftPlanStatus.INTERNAL_ERROR
				? WorkshopCraftExecutionResultCode.BATCH_CHANGED
				: rebuilt.status() == WorkshopCraftPlanStatus.DENIED
				? WorkshopCraftExecutionResultCode.ACCESS_DENIED
				: rebuilt.status() == WorkshopCraftPlanStatus.INTERNAL_ERROR
					? WorkshopCraftExecutionResultCode.INTERNAL_ERROR
					: WorkshopCraftExecutionResultCode.MATERIALS_CHANGED;
			return executionFailure(confirmation, result);
		}
		WorkshopCraftPlan plan = rebuilt.plan();
		if (plan.plannedIterations() != confirmation.plannedIterations()) {
			return executionFailure(
				confirmation,
				confirmation.craftMode() == WorkshopCraftMode.BATCH
					? WorkshopCraftExecutionResultCode.BATCH_CHANGED
					: WorkshopCraftExecutionResultCode.MATERIALS_CHANGED
			);
		}
		try {
			if (!WorkshopCraftTransactionExecutor.execute(plan, handler, player)) {
				return executionFailure(confirmation, WorkshopCraftExecutionResultCode.TRANSACTION_FAILED);
			}
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.error("Workshop crafting transaction failed safely", exception);
			return executionFailure(confirmation, WorkshopCraftExecutionResultCode.TRANSACTION_FAILED);
		}
		return new WorkshopCraftExecutionResultPayload(
			confirmation.previewId(), confirmation.sessionId(), confirmation.syncId(),
			WorkshopCraftExecutionResultCode.SUCCESS, confirmation.recipeId(), plan.craftMode(), plan.plannedIterations(),
			plan.assignments().size() * plan.plannedIterations(),
			plan.playerItemCount(), plan.storageItemCount(), plan.usedContainerCount()
		);
	}

	public void clear(ServerPlayerEntity player) {
		clear(player.getUuid());
	}

	public void clear(UUID playerId) {
		pending.clear(playerId);
		lastPreviewTicks.remove(playerId);
		lastConfirmTicks.remove(playerId);
	}

	public void clearAll() {
		pending.clearAll();
		lastPreviewTicks.clear();
		lastConfirmTicks.clear();
	}

	public void tick(long currentTick) {
		pending.clearExpired(currentTick);
	}

	private static boolean isGridEmpty(CraftingScreenHandler handler) {
		for (int slot = 1; slot <= 9; slot++) {
			if (!handler.getSlot(slot).getStack().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static WorkshopCraftPreviewResultCode previewResult(WorkshopCraftPlanStatus status) {
		return switch (status) {
			case INSUFFICIENT -> WorkshopCraftPreviewResultCode.INSUFFICIENT;
			case NO_ACCESSIBLE_CONTAINERS -> WorkshopCraftPreviewResultCode.NO_ACCESSIBLE_CONTAINERS;
			case DENIED -> WorkshopCraftPreviewResultCode.DENIED;
			case INTERNAL_ERROR -> WorkshopCraftPreviewResultCode.INTERNAL_ERROR;
			case AVAILABLE -> WorkshopCraftPreviewResultCode.AVAILABLE;
		};
	}

	private static WorkshopCraftPreviewPayload failure(
		WorkshopSession session,
		int syncId,
		Identifier recipeId,
		WorkshopCraftPreviewResultCode result,
		ItemStack output
	) {
		return failure(session, syncId, recipeId, result, WorkshopCraftMode.SINGLE, output);
	}

	private static WorkshopCraftPreviewPayload failure(
		WorkshopSession session,
		int syncId,
		Identifier recipeId,
		WorkshopCraftPreviewResultCode result,
		WorkshopCraftMode craftMode,
		ItemStack output
	) {
		return new WorkshopCraftPreviewPayload(
			0, session == null ? 0 : session.sessionId(), session == null ? 0 : session.revision(),
			Math.max(0, syncId), recipeId, result, craftMode, output, List.of(),
			0, output.isEmpty() ? 0 : output.getCount(), 0, 0, 0, 0, 0, 0
		);
	}

	private static WorkshopCraftExecutionResultPayload executionFailure(
		WorkshopCraftPendingConfirmation confirmation,
		WorkshopCraftExecutionResultCode result
	) {
		return new WorkshopCraftExecutionResultPayload(
			confirmation.previewId(), confirmation.sessionId(), confirmation.syncId(), result,
			confirmation.recipeId(), confirmation.craftMode(), confirmation.plannedIterations(), 0, 0, 0, 0
		);
	}

	private static WorkshopCraftExecutionResultPayload executionFailure(
		long previewId,
		long sessionId,
		int syncId,
		Identifier recipeId,
		WorkshopCraftExecutionResultCode result
	) {
		return new WorkshopCraftExecutionResultPayload(
			previewId, Math.max(0, sessionId), Math.max(0, syncId), result, recipeId,
			WorkshopCraftMode.SINGLE, 1, 0, 0, 0, 0
		);
	}
}
