package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WorkshopItemCatalogService {
	private final WorkshopSessionManager sessions;
	private final WorkshopContainerAccessService accessService;
	private final WorkshopSearchContainerCollector containerCollector;
	private final Map<UUID, Long> lastCatalogTicks = new HashMap<>();

	public WorkshopItemCatalogService(WorkshopSessionManager sessions) {
		this(sessions, new WorkshopContainerAccessService());
	}

	public WorkshopItemCatalogService(
		WorkshopSessionManager sessions,
		WorkshopContainerAccessService accessService
	) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.accessService = Objects.requireNonNull(accessService, "accessService");
		this.containerCollector = new WorkshopSearchContainerCollector(accessService);
	}

	public WorkshopItemCatalogPayload catalog(
		ServerPlayerEntity player,
		RequestWorkshopItemCatalogPayload request
	) {
		WorkshopZone.LOGGER.debug(
			"Player {} requested workshop item catalog requestId {} session {} revision {} syncId {}",
			player.getGameProfile().getName(), request.requestId(), request.sessionId(), request.revision(), request.syncId()
		);
		WorkshopSession session = sessions.get(player.getUuid()).orElse(null);
		WorkshopItemCatalogResultCode identity = WorkshopItemCatalogChecks.validateIdentity(
			session,
			request,
			player.currentScreenHandler.syncId,
			session != null && session.dimension().equals(player.getServerWorld().getRegistryKey()),
			session != null && WorkshopSessionManager.matchesHandler(session.openedBlockType(), player.currentScreenHandler),
			session == null ? null : sessions.validate(player, session)
		);
		if (identity != WorkshopItemCatalogResultCode.SUCCESS) {
			return reject(request, identity);
		}

		long now = player.getServerWorld().getTime();
		long previous = lastCatalogTicks.getOrDefault(player.getUuid(), now - WorkshopItemCatalogChecks.COOLDOWN_TICKS);
		if (!WorkshopItemCatalogChecks.cooldownElapsed(now, previous)) {
			return reject(request, WorkshopItemCatalogResultCode.COOLDOWN);
		}
		lastCatalogTicks.put(player.getUuid(), now);

		try {
			return buildCatalog(player, session, request);
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.error(
				"Workshop item catalog failed for player {} requestId {}",
				player.getGameProfile().getName(), request.requestId(), exception
			);
			return reject(request, WorkshopItemCatalogResultCode.INTERNAL_ERROR);
		}
	}

	public void clear(ServerPlayerEntity player) {
		lastCatalogTicks.remove(player.getUuid());
	}

	private WorkshopItemCatalogPayload buildCatalog(
		ServerPlayerEntity player,
		WorkshopSession session,
		RequestWorkshopItemCatalogPayload request
	) {
		ServerWorld world = player.getServerWorld();
		WorkshopSearchContainerCollector.Result collected = containerCollector.collect(player, session);
		if (collected.containers().isEmpty()) {
			return reject(request, WorkshopItemCatalogResultCode.EMPTY);
		}

		WorkshopItemCatalogBuilder builder = new WorkshopItemCatalogBuilder();
		for (WorkshopAccessibleContainer accessible : collected.containers()) {
			builder.addContainer(accessible.container().inventory(), item ->
				accessService.canSearchItem(
					player, world, accessible.container(), item
				) == WorkshopContainerAccessService.AccessResult.ALLOW
			);
		}
		WorkshopItemCatalog catalog = builder.build(WorkshopItemCatalog.MAX_CATALOG_ENTRIES);
		if (catalog.entries().isEmpty()) {
			return reject(request, WorkshopItemCatalogResultCode.EMPTY);
		}
		WorkshopZone.LOGGER.debug(
			"Workshop item catalog requestId {} session {} considered {} containers, accepted {}, returned {} of {} item types",
			request.requestId(), session.sessionId(), collected.candidateContainerCount(), collected.containers().size(),
			catalog.entries().size(), catalog.totalDistinctItems()
		);
		return new WorkshopItemCatalogPayload(
			request.requestId(), request.sessionId(), request.revision(), request.syncId(),
			WorkshopItemCatalogResultCode.SUCCESS, catalog.totalDistinctItems(), catalog.truncated(), catalog.entries()
		);
	}

	private WorkshopItemCatalogPayload reject(
		RequestWorkshopItemCatalogPayload request,
		WorkshopItemCatalogResultCode result
	) {
		WorkshopZone.LOGGER.debug("Rejected workshop item catalog requestId {}: {}", request.requestId(), result);
		return new WorkshopItemCatalogPayload(
			request.requestId(), request.sessionId(), request.revision(), request.syncId(),
			result, 0, false, java.util.List.of()
		);
	}
}
