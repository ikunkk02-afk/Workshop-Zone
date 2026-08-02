package io.github.ikunkk02afk.workshopzone.session;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopScanResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WorkshopSessionTest {
	@Test
	void sessionCreationAndRefreshPreserveIdentity() {
		UUID playerId = UUID.randomUUID();
		WorkshopSession session = session(1, playerId, 4);
		WorkshopSession refreshed = session.refreshed(120, emptyResult());

		assertEquals(playerId, session.playerId());
		assertEquals(World.OVERWORLD, session.dimension());
		assertEquals(BlockPos.ORIGIN, session.scanCenter());
		assertEquals(BlockPos.ORIGIN, session.openedEntryPosition());
		assertEquals(1, refreshed.revision());
		assertEquals(120, refreshed.lastRefreshAt());
		assertEquals(session.sessionId(), refreshed.sessionId());
	}

	@Test
	void newSessionReplacesOldSessionForPlayer() {
		UUID playerId = UUID.randomUUID();
		WorkshopSessionStore store = new WorkshopSessionStore();
		WorkshopSession first = session(1, playerId, 4);
		WorkshopSession second = session(2, playerId, 5);

		assertNull(store.put(first));
		assertSame(first, store.put(second));
		assertSame(second, store.get(playerId).orElseThrow());
	}

	@Test
	void expiredSessionsCanBePurged() {
		WorkshopSessionStore store = new WorkshopSessionStore();
		store.put(session(1, UUID.randomUUID(), 4));
		store.put(session(2, UUID.randomUUID(), 5));

		assertEquals(1, store.removeIf(session -> session.syncId() == 4));
		assertEquals(1, store.playerIds().length);
	}

	private static WorkshopSession session(long id, UUID playerId, int syncId) {
		return new WorkshopSession(
			id, 0, playerId, World.OVERWORLD, BlockPos.ORIGIN, BlockPos.ORIGIN, WorkshopBlockType.CHEST,
			syncId, 100, 80, emptyResult()
		);
	}

	private static WorkshopScanResult emptyResult() {
		return WorkshopScanResult.create(BlockPos.ORIGIN, 8, 4, List.of());
	}
}
