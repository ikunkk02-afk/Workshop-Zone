package io.github.ikunkk02afk.workshopzone.session;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.api.WorkshopRemoteOpenCallback;
import io.github.ikunkk02afk.workshopzone.network.WorkshopNetworking;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopAreaScanner;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockCatalog;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopScanResult;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.screen.BlastFurnaceScreenHandler;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.CartographyTableScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.FurnaceScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.GrindstoneScreenHandler;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.SmokerScreenHandler;
import net.minecraft.screen.StonecutterScreenHandler;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class WorkshopSessionManager {
	public static final int REFRESH_COOLDOWN_TICKS = 20;
	public static final int OPEN_COOLDOWN_TICKS = 5;
	public static final double MAX_CENTER_DISTANCE_SQUARED = 64.0;
	public static final double MAX_REMOTE_OPEN_DISTANCE_SQUARED = 64.0;

	private static final WorkshopSessionManager INSTANCE = new WorkshopSessionManager();

	private final WorkshopSessionStore sessions = new WorkshopSessionStore();
	private final AtomicLong nextSessionId = new AtomicLong();
	private final WorkshopAreaScanner scanner = new WorkshopAreaScanner();
	private final Map<UUID, Long> lastOpenRequestTicks = new HashMap<>();
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
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			clear(handler.player, false);
			lastOpenRequestTicks.remove(handler.player.getUuid());
		});
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	public Optional<WorkshopSession> get(UUID playerId) {
		return sessions.get(playerId);
	}

	public void open(ServerPlayerEntity player, BlockPos center, WorkshopBlockType type) {
		createAndSendSession(player, center, type);
	}

	private WorkshopSession createAndSendSession(ServerPlayerEntity player, BlockPos center, WorkshopBlockType type) {
		if (!matchesHandler(type, player.currentScreenHandler)) {
			return null;
		}
		ServerWorld world = player.getServerWorld();
		BlockState openedState = world.getBlockState(center);
		if (WorkshopBlockCatalog.vanilla().find(openedState.getBlock()).filter(found -> found == type).isEmpty()) {
			return null;
		}
		long now = world.getTime();
		WorkshopScanResult result = scanner.scan(
			world, center, WorkshopAreaScanner.DEFAULT_HORIZONTAL_RADIUS, WorkshopAreaScanner.DEFAULT_VERTICAL_RADIUS
		);
		BlockPos openedEntryPosition = WorkshopAreaScanner.representativePosition(center, openedState);
		if (WorkshopOpenChecks.findTarget(result.entries(), openedEntryPosition).filter(entry -> entry.type() == type).isEmpty()) {
			WorkshopZone.LOGGER.debug(
				"Did not create workshop session for player {}: opened entry {} type {} was absent from scan",
				player.getGameProfile().getName(), openedEntryPosition, type
			);
			return null;
		}
		WorkshopSession session = new WorkshopSession(
			nextSessionId.incrementAndGet(), 0, player.getUuid(), world.getRegistryKey(), center, openedEntryPosition,
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
		return session;
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
			player.getServerWorld(), session.scanCenter(),
			WorkshopAreaScanner.DEFAULT_HORIZONTAL_RADIUS, WorkshopAreaScanner.DEFAULT_VERTICAL_RADIUS
		);
		WorkshopSession refreshed = session.refreshed(now, result);
		sessions.put(refreshed);
		WorkshopNetworking.sendSnapshot(player, refreshed);
	}

	public WorkshopOpenResult openTarget(
		ServerPlayerEntity player,
		long sessionId,
		long revision,
		int syncId,
		BlockPos targetPos
	) {
		ServerWorld world = player.getServerWorld();
		long now = world.getTime();
		long previousRequestTick = lastOpenRequestTicks.getOrDefault(player.getUuid(), now - OPEN_COOLDOWN_TICKS);
		lastOpenRequestTicks.put(player.getUuid(), now);
		WorkshopZone.LOGGER.debug(
			"Player {} requested workshop target {} with session {} revision {} syncId {}",
			player.getGameProfile().getName(), targetPos, sessionId, revision, syncId
		);

		WorkshopSession session = sessions.get(player.getUuid()).orElse(null);
		WorkshopOpenResult identity = WorkshopOpenChecks.validateIdentity(
			session, sessionId, revision, syncId, player.currentScreenHandler.syncId
		);
		if (identity != WorkshopOpenResult.SUCCESS) {
			return reject(player, targetPos, identity);
		}
		if (validate(player, session) != WorkshopSessionValidation.VALID) {
			return reject(player, targetPos, WorkshopOpenResult.INVALID_SESSION);
		}

		WorkshopBlockEntry expected = WorkshopOpenChecks.findTarget(session.scanResult().entries(), targetPos).orElse(null);
		if (expected == null) {
			return reject(player, targetPos, WorkshopOpenResult.NOT_IN_SESSION);
		}
		WorkshopTargetValidator.Result target = WorkshopTargetValidator.validate(
			new WorkshopTargetValidator.Access() {
				@Override
				public boolean isChunkLoaded(BlockPos position) {
					int chunkX = ChunkSectionPos.getSectionCoord(position.getX());
					int chunkZ = ChunkSectionPos.getSectionCoord(position.getZ());
					return world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
				}

				@Override
				public BlockState getBlockState(BlockPos position) {
					return world.getBlockState(position);
				}
			},
			expected
		);
		if (target.result() == WorkshopOpenResult.CHUNK_UNLOADED) {
			return reject(player, targetPos, WorkshopOpenResult.CHUNK_UNLOADED);
		}
		if (target.result() == WorkshopOpenResult.BLOCK_CHANGED) {
			refreshAfterTargetChanged(player, session);
			return reject(player, targetPos, WorkshopOpenResult.BLOCK_CHANGED);
		}
		BlockState targetState = target.state();
		WorkshopBlockType actualType = target.type();
		if (targetPos.equals(session.openedEntryPosition())) {
			return reject(player, targetPos, WorkshopOpenResult.CURRENT);
		}
		double distanceSquared = player.squaredDistanceTo(Vec3d.ofCenter(targetPos));
		if (!WorkshopOpenChecks.isWithinRemoteOpenDistance(distanceSquared)) {
			return reject(player, targetPos, WorkshopOpenResult.OUT_OF_RANGE);
		}
		if (!WorkshopOpenChecks.cooldownElapsed(now, previousRequestTick)) {
			return reject(player, targetPos, WorkshopOpenResult.COOLDOWN);
		}

		try {
			if (!WorkshopRemoteOpenCallback.EVENT.invoker().canOpen(player, world, targetPos, targetState, actualType)) {
				return reject(player, targetPos, WorkshopOpenResult.DENIED);
			}
		} catch (RuntimeException exception) {
			WorkshopZone.LOGGER.error(
				"Workshop remote-open callback failed for player {} target {}; denying request",
				player.getGameProfile().getName(), targetPos, exception
			);
			return reject(player, targetPos, WorkshopOpenResult.DENIED);
		}

		NamedScreenHandlerFactory factory = targetState.createScreenHandlerFactory(world, targetPos);
		if (factory == null) {
			return reject(player, targetPos, WorkshopOpenResult.UNAVAILABLE);
		}
		OptionalInt openedSyncId = player.openHandledScreen(factory);
		boolean opened = openedSyncId.isPresent()
			&& player.currentScreenHandler.syncId == openedSyncId.getAsInt()
			&& matchesHandler(actualType, player.currentScreenHandler);
		WorkshopZone.LOGGER.debug(
			"Workshop target {} type {} openHandledScreen success={} syncId={}",
			targetPos, actualType, opened, openedSyncId.isPresent() ? openedSyncId.getAsInt() : -1
		);
		if (!opened) {
			if (player.currentScreenHandler != player.playerScreenHandler) {
				player.closeHandledScreen();
			}
			return reject(player, targetPos, WorkshopOpenResult.UNAVAILABLE);
		}

		WorkshopSession next = createAndSendSession(player, targetPos, actualType);
		if (next == null) {
			player.closeHandledScreen();
			return reject(player, targetPos, WorkshopOpenResult.UNAVAILABLE);
		}
		WorkshopZone.LOGGER.debug(
			"Workshop switch completed for player {}: new session {} syncId {} target {} type {}",
			player.getGameProfile().getName(), next.sessionId(), next.syncId(), targetPos, actualType
		);
		return WorkshopOpenResult.SUCCESS;
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
		double distanceSquared = player.squaredDistanceTo(Vec3d.ofCenter(session.scanCenter()));
		if (!Double.isFinite(distanceSquared) || distanceSquared > MAX_CENTER_DISTANCE_SQUARED) {
			return WorkshopSessionValidation.OUT_OF_RANGE;
		}
		int chunkX = ChunkSectionPos.getSectionCoord(session.scanCenter().getX());
		int chunkZ = ChunkSectionPos.getSectionCoord(session.scanCenter().getZ());
		boolean centerLoaded = world.getChunkManager().isChunkLoaded(chunkX, chunkZ);
		boolean centerMatches = centerLoaded && WorkshopBlockCatalog.vanilla()
			.find(world.getBlockState(session.scanCenter()).getBlock())
			.filter(type -> type == session.openedBlockType()).isPresent();
		return WorkshopSessionChecks.validate(
			session.syncId(), player.currentScreenHandler.syncId, true, distanceSquared,
			centerLoaded, centerMatches, matchesHandler(session.openedBlockType(), player.currentScreenHandler)
		);
	}

	private void refreshAfterTargetChanged(ServerPlayerEntity player, WorkshopSession session) {
		WorkshopScanResult result = scanner.scan(
			player.getServerWorld(), session.scanCenter(),
			WorkshopAreaScanner.DEFAULT_HORIZONTAL_RADIUS, WorkshopAreaScanner.DEFAULT_VERTICAL_RADIUS
		);
		WorkshopSession refreshed = session.refreshed(player.getServerWorld().getTime(), result);
		sessions.put(refreshed);
		WorkshopNetworking.sendSnapshot(player, refreshed);
	}

	private WorkshopOpenResult reject(ServerPlayerEntity player, BlockPos targetPos, WorkshopOpenResult result) {
		WorkshopZone.LOGGER.debug(
			"Rejected workshop target {} for player {}: {}",
			targetPos, player.getGameProfile().getName(), result
		);
		player.sendMessage(Text.translatable(result.translationKey()), true);
		return result;
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
			case SMITHING_TABLE -> handler instanceof SmithingScreenHandler;
			case ANVIL -> handler instanceof AnvilScreenHandler;
			case STONECUTTER -> handler instanceof StonecutterScreenHandler;
			case GRINDSTONE -> handler instanceof GrindstoneScreenHandler;
			case LOOM -> handler instanceof LoomScreenHandler;
			case CARTOGRAPHY_TABLE -> handler instanceof CartographyTableScreenHandler;
			case BREWING_STAND -> handler instanceof BrewingStandScreenHandler;
			case ENCHANTING_TABLE -> handler instanceof EnchantmentScreenHandler;
		};
	}
}
