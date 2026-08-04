package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import io.github.ikunkk02afk.workshopzone.label.WorkshopContainerResolver;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class WorkshopSearchContainerCollector {
	private final WorkshopContainerAccessService accessService;

	public WorkshopSearchContainerCollector(WorkshopContainerAccessService accessService) {
		this.accessService = Objects.requireNonNull(accessService, "accessService");
	}

	public Result collect(ServerPlayerEntity player, WorkshopSession session) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(session, "session");
		ServerWorld world = player.getServerWorld();
		List<WorkshopAccessibleContainer> containers = new ArrayList<>();
		Set<BlockPos> seenEntries = new HashSet<>();
		Set<BlockPos> seenRepresentatives = new HashSet<>();
		int candidates = 0;
		int denied = 0;
		int scanIndex = 0;
		for (WorkshopBlockEntry entry : session.scanResult().entries()) {
			int currentScanIndex = scanIndex++;
			if (!WorkshopItemSearchChecks.isSearchableContainer(entry.type()) || !seenEntries.add(entry.position())) {
				continue;
			}
			candidates++;
			BlockPos position = entry.position();
			if (!isLoaded(world, position)) {
				debugSkip(position, "chunk_unloaded");
				continue;
			}
			double distanceSquared = player.squaredDistanceTo(Vec3d.ofCenter(position));
			if (!WorkshopItemSearchChecks.isWithinDistance(distanceSquared)) {
				debugSkip(position, "out_of_range");
				continue;
			}
			WorkshopContainerResolver.Result resolved = WorkshopContainerResolver.resolve(world, position);
			if (!resolved.successful()) {
				debugSkip(position, resolved.status().name().toLowerCase(Locale.ROOT));
				continue;
			}
			LogicalContainer container = resolved.container();
			if (!container.representativePosition().equals(position) || container.type() != entry.type()) {
				debugSkip(position, "representative_or_type_changed");
				continue;
			}
			if (!seenRepresentatives.add(container.representativePosition())) {
				debugSkip(position, "duplicate_logical_container");
				continue;
			}
			WorkshopContainerAccessService.AccessResult access = accessService.canAccessContainer(
				player, world, entry, container
			);
			if (access != WorkshopContainerAccessService.AccessResult.ALLOW) {
				if (isDenied(access)) {
					denied++;
				}
				debugSkip(position, access.name().toLowerCase(Locale.ROOT));
				continue;
			}
			containers.add(new WorkshopAccessibleContainer(entry, container, distanceSquared, currentScanIndex));
		}
		return new Result(containers, candidates, denied);
	}

	private static boolean isDenied(WorkshopContainerAccessService.AccessResult access) {
		return switch (access) {
			case PLAYER_CANNOT_USE, BLOCKED, LOCKED, OPEN_CALLBACK_DENIED, SEARCH_CALLBACK_DENIED, CALLBACK_ERROR -> true;
			case ALLOW, UNGENERATED_LOOT -> false;
		};
	}

	private static boolean isLoaded(ServerWorld world, BlockPos position) {
		return world.getChunkManager().isChunkLoaded(
			ChunkSectionPos.getSectionCoord(position.getX()), ChunkSectionPos.getSectionCoord(position.getZ())
		);
	}

	private static void debugSkip(BlockPos position, String reason) {
		WorkshopZone.LOGGER.debug("Skipping workshop search container {}: {}", position, reason);
	}

	public record Result(
		List<WorkshopAccessibleContainer> containers,
		int candidateContainerCount,
		int deniedContainerCount
	) {
		public Result {
			containers = List.copyOf(containers);
			if (candidateContainerCount < 0 || deniedContainerCount < 0 || deniedContainerCount > candidateContainerCount
				|| containers.size() > candidateContainerCount) {
				throw new IllegalArgumentException("Invalid workshop container collection result");
			}
		}
	}
}
