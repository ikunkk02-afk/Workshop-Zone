package io.github.ikunkk02afk.workshopzone.session;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopScanResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopOpenChecksTest {
	private static final BlockPos TARGET = new BlockPos(2, 0, 0);

	@Test
	void exactSessionIdentityIsRequired() {
		WorkshopSession session = session();

		assertEquals(WorkshopOpenResult.SUCCESS, WorkshopOpenChecks.validateIdentity(session, 10, 3, 7, 7));
		assertEquals(WorkshopOpenResult.INVALID_SESSION, WorkshopOpenChecks.validateIdentity(session, 11, 3, 7, 7));
		assertEquals(WorkshopOpenResult.INVALID_SESSION, WorkshopOpenChecks.validateIdentity(null, 10, 3, 7, 7));
	}

	@Test
	void staleRevisionAndWrongSyncAreRejectedSeparately() {
		WorkshopSession session = session();

		assertEquals(WorkshopOpenResult.STALE_SNAPSHOT, WorkshopOpenChecks.validateIdentity(session, 10, 2, 7, 7));
		assertEquals(WorkshopOpenResult.INVALID_SESSION, WorkshopOpenChecks.validateIdentity(session, 10, 3, 8, 7));
		assertEquals(WorkshopOpenResult.INVALID_SESSION, WorkshopOpenChecks.validateIdentity(session, 10, 3, 7, 8));
	}

	@Test
	void targetMustExactlyMatchServerSnapshotEntry() {
		WorkshopSession session = session();

		assertTrue(WorkshopOpenChecks.findTarget(session.scanResult().entries(), TARGET).isPresent());
		assertFalse(WorkshopOpenChecks.findTarget(session.scanResult().entries(), new BlockPos(3, 0, 0)).isPresent());
		assertFalse(WorkshopOpenChecks.findTarget(session.scanResult().entries(), new BlockPos(1000, 64, 1000)).isPresent());
	}

	@Test
	void remoteOpenDistanceIsInclusiveAtEightBlocksAndRejectsInvalidValues() {
		assertTrue(WorkshopOpenChecks.isWithinRemoteOpenDistance(64.0));
		assertFalse(WorkshopOpenChecks.isWithinRemoteOpenDistance(Math.nextUp(64.0)));
		assertFalse(WorkshopOpenChecks.isWithinRemoteOpenDistance(Double.NaN));
		assertFalse(WorkshopOpenChecks.isWithinRemoteOpenDistance(Double.POSITIVE_INFINITY));
	}

	@Test
	void remoteOpenCooldownUsesServerTicks() {
		assertFalse(WorkshopOpenChecks.cooldownElapsed(104, 100));
		assertTrue(WorkshopOpenChecks.cooldownElapsed(105, 100));
	}

	private static WorkshopSession session() {
		WorkshopBlockEntry target = WorkshopBlockEntry.create(
			WorkshopBlockType.CHEST, TARGET, Identifier.ofVanilla("chest"), 4.0
		);
		WorkshopScanResult result = WorkshopScanResult.create(BlockPos.ORIGIN, 8, 4, List.of(target));
		return new WorkshopSession(
			10, 3, UUID.randomUUID(), World.OVERWORLD, BlockPos.ORIGIN, BlockPos.ORIGIN,
			WorkshopBlockType.CRAFTING_TABLE, 7, 100, 80, result
		);
	}
}
