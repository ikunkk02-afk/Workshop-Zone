package io.github.ikunkk02afk.workshopzone.session;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.network.WorkshopNetworking;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopAreaScanner;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockCatalog;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopScanResult;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.screen.BlastFurnaceScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class WorkshopSessionManager {
	public static final int REFRESH_COOLDOWN_TICKS = 20;
	public static final double MAX_CENTER_DISTANCE_SQUARED = 64.0;

	private static final WorkshopSessionManager INSTANCE = new WorkshopSessionManager();

	private final WorkshopSessionStore sessions = new WorkshopSessionStore();
	private final AtomicLong nextSessionId = new AtomicLong();
	private final WorkshopAreaScanner scanner = new WorkshopAreaScanner();
	private boolean eventsRegistered;
	private int cleanupTicker;

	private WorkshopSessionManager() {
	}

	public static WorkshopSessionManager getInstance() {
		return INSTANCE;
	}

	public void registerEvents() {
		if (eventsRegistered) {
			return;
		}
		eventsRegistered = true;
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) {
				clear(player, true);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> clear(newPlayer, true));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> clear(player, true));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player, false));
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	public Optional<WorkshopSession> get(UUID playerId) {
		return sessions.get(playerId);
	}

	public void open(ServerPlayerEntity player, BlockPos center, WorkshopBlockType type) {
		if (!matchesHandler(type, player.currentScreenHandler)) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		WorkshopScanResult result = scanner.scan(
			world, center, WorkshopAreaScanner.DEFAULT_HORIZONTAL_RADIUS, WorkshopAreaScanner.DEFAULT_VERTICAL_RADIUS
		);
		WorkshopSession session = new WorkshopSession(
			nextSessionId.incrementAndGet(), 0, player.getUuid(), world.getRegistryKey(), center,
			type, player.currentScreenHandler.syncId, now, now - REFRESH_COOLDOWN_TICKS, result
		);
		WorkshopZone.LOGGER.debug(
			"Created workshop session {} for player {} at {} syncId {}; scan found {} entries",
			session.sessionId(), player.getGameProfile().getName(), center, session.syncId(), result.size()
		);
		WorkshopSession previous = sessions.put(session);
		if (previous != null) {
			WorkshopNetworking.sendClear(player, previous);
		}
		WorkshopNetworking.sendSnapshot(player, session);
	}

	public void refresh(ServerPlayerEntity player, long sessionId, int syncId) {
		WorkshopSession session = sessions.get(player.getUuid()).orElse(null);
		if (session == null || session.sessionId() != sessionId || session.syncId() != syncId) {
			player.sendMessage(Text.translatable("message.workshop_zone.session.invalid"), true);
			return;
		}
		WorkshopSessionValidation validation = validate(player, session);
		if (validation != WorkshopSessionValidation.VALID) {
			player.sendMessage(Text.translatable(
				validation == WorkshopSessionValidation.OUT_OF_RANGE
					? "message.workshop_zone.session.out_of_range"
					: "message.workshop_zone.session.invalid"
			), true);
			clear(player, true);
			return;
		}
		long now = player.getServerWorld().getTime();
		if (!WorkshopSessionChecks.canRefresh(now, session.lastRefreshAt())) {
			player.sendMessage(Text.translatable("gui.workshop_zone.sidebar.refresh_cooldown"), true);
			return;
		}
		WorkshopScanResult result = scanner.scan(
			player.getServerWorld(), session.center(),
			WorkshopAreaScanner.DEFAULT_HORIZONTAL_RADIUS, WorkshopAreaScanner.DEFAULT_VERTICAL_RADIUS
		);
		WorkshopSession refreshed = session.refreshed(now, result);
		sessions.put(refreshed);
		WorkshopNetworking.sendSnapshot(player, refreshed);
	}

	public void clearIfSync(ServerPlayerEntity player, int syncId) {
		WorkshopSession session = sessions.get(player.getUuid()).orElse(null);
		if (session != null && session.syncId() == syncId) {
			clear(player, true);
		}
	}

	public void clear(ServerPlayerEntity player, boolean notifyClient) {
		WorkshopSession removed = sessions.remove(player.getUuid());
		if (removed != null && notifyClient) {
			WorkshopNetworking.sendClear(player, removed);
		}
	}

	public WorkshopSessionValidation validate(ServerPlayerEntity player, WorkshopSession session) {
		if (player.currentScreenHandler.syncId != session.syncId()) {
			return WorkshopSessionValidation.SYNC_MISMATCH;
		}
		ServerWorld world = player.getServerWorld();
		if (!world.getRegistryKey().equals(session.dimension())) {
			return WorkshopSessionValidation.DIMENSION_MISMATCH;
		}
		double distanceSquared = player.squaredDistanceTo(Vec3d.ofCenter(session.center()));
		if (!Double.isFinite(distanceSquared) || distanceSquared > MAX_CENTER_DISTANCE_SQUARED) {
			return WorkshopSessionValidation.OUT_OF_RANGE;
		}
		int chunkX = ChunkSectionPos.getSectionCoord(session.center().getX());
		int chunkZ = ChunkSectionPos.getSectionCoord(session.center().getZ());
		boolean centerLoaded = world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
		boolean centerMatches = centerLoaded && WorkshopBlockCatalog.vanilla()
			.find(world.getBlockState(session.center()).getBlock())
			.filter(type -> type == session.openedBlockType()).isPresent();
		return WorkshopSessionChecks.validate(
			session.syncId(), player.currentScreenHandler.syncId, true, distanceSquared,
			centerLoaded, centerMatches, matchesHandler(session.openedBlockType(), player.currentScreenHandler)
		);
	}

	private void onServerTick(MinecraftServer server) {
		if (++cleanupTicker < 20) {
			return;
		}
		cleanupTicker = 0;
		for (UUID playerId : sessions.playerIds()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
			WorkshopSession session = sessions.get(playerId).orElse(null);
			if (player == null) {
				sessions.remove(playerId);
			} else if (session != null && validate(player, session) != WorkshopSessionValidation.VALID) {
				clear(player, true);
			}
		}
	}

	public static boolean matchesHandler(WorkshopBlockType type, ScreenHandler handler) {
		return switch (type) {
			case CHEST, TRAPPED_CHEST, BARREL -> handler instanceof GenericContainerScreenHandler;
			case CRAFTING_TABLE -> handler instanceof CraftingScreenHandler;
			case FURNACE -> handler instanceof FurnaceScreenHandler;
			case BLAST_FURNACE -> handler instanceof BlastFurnaceScreenHandler;
			case SMOKER -> handler instanceof SmokerScreenHandler;
		};
	}
}
