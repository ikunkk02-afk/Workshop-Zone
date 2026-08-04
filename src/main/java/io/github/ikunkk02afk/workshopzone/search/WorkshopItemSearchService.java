package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import io.github.ikunkk02afk.workshopzone.label.WorkshopContainerResolver;
import io.github.ikunkk02afk.workshopzone.network.SearchWorkshopItemPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorkshopItemSearchService {
	private final WorkshopSessionManager sessions;
	private final WorkshopContainerAccessService accessService;
	private final WorkshopSearchContainerCollector containerCollector;
	private final Map<UUID, Long> lastSearchTicks = new HashMap<>();

	public WorkshopItemSearchService(WorkshopSessionManager sessions) {
		this(sessions, new WorkshopContainerAccessService());
	}

	public WorkshopItemSearchService(WorkshopSessionManager sessions, WorkshopContainerAccessService accessService) {
		this.sessions = sessions;
		this.accessService = accessService;
		this.containerCollector = new WorkshopSearchContainerCollector(accessService);
	}

	public WorkshopItemSearchResultPayload search(ServerPlayerEntity player, SearchWorkshopItemPayload request) {
		WorkshopZone.LOGGER.debug(
			"Player {} requested workshop item search requestId {} item {} session {}",
			player.getGameProfile().getName(), request.requestId(), request.targetItemId(), request.sessionId()
		);
		WorkshopSession session = sessions.get(player.getUuid()).orElse(null);
		WorkshopItemSearchResultCode identity = WorkshopItemSearchChecks.validateIdentity(
			session,
			request,
			player.currentScreenHandler.syncId,
			session != null && session.dimension().equals(player.getServerWorld().getRegistryKey()),
			session != null && WorkshopSessionManager.matchesHandler(session.openedBlockType(), player.currentScreenHandler),
			session == null ? null : sessions.validate(player, session)
		);
		if (identity != WorkshopItemSearchResultCode.SUCCESS) {
			return reject(request, identity, 0, 0);
		}

		Item targetItem = Registries.ITEM.getOrEmpty(request.targetItemId()).orElse(null);
		if (targetItem == null || targetItem == Items.AIR) {
			return reject(request, WorkshopItemSearchResultCode.INVALID_ITEM, 0, 0);
		}
		long now = player.getServerWorld().getTime();
		long previous = lastSearchTicks.getOrDefault(player.getUuid(), now - WorkshopItemSearchChecks.COOLDOWN_TICKS);
		if (!WorkshopItemSearchChecks.cooldownElapsed(now, previous)) {
			return reject(request, WorkshopItemSearchResultCode.COOLDOWN, 0, 0);
		}
		lastSearchTicks.put(player.getUuid(), now);

		try {
			WorkshopItemSearchResult result = searchSession(player, session, request.targetItemId(), targetItem);
			WorkshopZone.LOGGER.debug(
				"Workshop search requestId {} session {} considered {} candidates, {} accessible, {} matching, total {}",
				request.requestId(), session.sessionId(), result.candidateContainerCount(), result.accessibleContainerCount(),
				result.totalMatchingContainers(), result.totalItemCount()
			);
			return result.toPayload(request);
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.error(
				"Workshop item search failed for player {} requestId {}",
				player.getGameProfile().getName(), request.requestId(), exception
			);
			return reject(request, WorkshopItemSearchResultCode.INTERNAL_ERROR, 0, 0);
		}
	}

	public void clear(ServerPlayerEntity player) {
		lastSearchTicks.remove(player.getUuid());
	}

	private WorkshopItemSearchResult searchSession(
		ServerPlayerEntity player,
		WorkshopSession session,
		Identifier targetItemId,
		Item targetItem
	) {
		ServerWorld world = player.getServerWorld();
		List<WorkshopItemSearchContainerResult> matches = new ArrayList<>();
		WorkshopSearchContainerCollector.Result collected = containerCollector.collect(player, session);
		int candidates = collected.candidateContainerCount();
		int accessible = 0;
		long totalItemCount = 0L;
		for (WorkshopAccessibleContainer accessibleContainer : collected.containers()) {
			LogicalContainer container = accessibleContainer.container();
			WorkshopContainerAccessService.AccessResult access = accessService.canSearchItem(
				player, world, container, targetItem
			);
			if (access != WorkshopContainerAccessService.AccessResult.ALLOW) {
				WorkshopZone.LOGGER.debug(
					"Skipping workshop item search container {}: {}",
					container.representativePosition(), access.name().toLowerCase(java.util.Locale.ROOT)
				);
				continue;
			}
			accessible++;
			WorkshopItemSearchCounter.Count count = WorkshopItemSearchCounter.count(container.inventory(), targetItem);
			if (count.itemCount() <= 0) {
				continue;
			}
			totalItemCount += count.itemCount();
			matches.add(new WorkshopItemSearchContainerResult(
				container.representativePosition(), container.memberPositions(), count.itemCount(), count.matchingSlotCount(),
				accessibleContainer.distanceSquared(), count.multipleVariants(), accessibleContainer.scanIndex()
			));
		}
		if (accessible == 0) {
			return WorkshopItemSearchResult.empty(
				WorkshopItemSearchResultCode.NO_ACCESSIBLE_CONTAINERS, targetItemId, candidates, 0
			);
		}
		if (matches.isEmpty()) {
			return WorkshopItemSearchResult.empty(WorkshopItemSearchResultCode.NOT_FOUND, targetItemId, candidates, accessible);
		}
		matches.sort(WorkshopItemSearchPlanner.RESULT_ORDER);
		int totalMatching = matches.size();
		List<WorkshopItemSearchContainerResult> returned = List.copyOf(
			matches.subList(0, Math.min(matches.size(), WorkshopItemSearchResultPayload.MAX_RESULTS))
		);
		return new WorkshopItemSearchResult(
			WorkshopItemSearchResultCode.SUCCESS, targetItemId, totalItemCount, totalMatching,
			totalMatching > returned.size(), returned, candidates, accessible
		);
	}

	private WorkshopItemSearchResultPayload reject(
		SearchWorkshopItemPayload request,
		WorkshopItemSearchResultCode result,
		int candidates,
		int accessible
	) {
		WorkshopZone.LOGGER.debug(
			"Rejected workshop item search requestId {} item {}: {}",
			request.requestId(), request.targetItemId(), result
		);
		return WorkshopItemSearchResult.empty(result, request.targetItemId(), candidates, accessible).toPayload(request);
	}

}
