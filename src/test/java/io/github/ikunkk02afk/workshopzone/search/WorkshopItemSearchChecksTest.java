package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.network.SearchWorkshopItemPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopScanResult;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionValidation;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemSearchChecksTest {
	@Test
	void identityRejectsWrongSessionRevisionAndSyncSeparately() {
		WorkshopSession session = session();
		assertEquals(WorkshopItemSearchResultCode.INVALID_SESSION, WorkshopItemSearchChecks.validateIdentity(
			session, request(12, 4, 9), 9, true, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemSearchResultCode.STALE_SESSION, WorkshopItemSearchChecks.validateIdentity(
			session, request(11, 5, 9), 9, true, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemSearchResultCode.INVALID_SESSION, WorkshopItemSearchChecks.validateIdentity(
			session, request(11, 4, 10), 9, true, true, WorkshopSessionValidation.VALID
		));
	}

	@Test
	void validIdentityRequiresDimensionHandlerAndSessionValidation() {
		WorkshopSession session = session();
		SearchWorkshopItemPayload request = request(11, 4, 9);
		assertEquals(WorkshopItemSearchResultCode.SUCCESS, WorkshopItemSearchChecks.validateIdentity(
			session, request, 9, true, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemSearchResultCode.INVALID_SESSION, WorkshopItemSearchChecks.validateIdentity(
			session, request, 9, false, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemSearchResultCode.INVALID_SESSION, WorkshopItemSearchChecks.validateIdentity(
			session, request, 9, true, false, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemSearchResultCode.INVALID_SESSION, WorkshopItemSearchChecks.validateIdentity(
			session, request, 9, true, true, WorkshopSessionValidation.OUT_OF_RANGE
		));
	}

	@Test
	void cooldownAndContainerTypeRulesAreStable() {
		assertFalse(WorkshopItemSearchChecks.cooldownElapsed(100, 91));
		assertTrue(WorkshopItemSearchChecks.cooldownElapsed(100, 90));
		assertTrue(WorkshopItemSearchChecks.isSearchableContainer(WorkshopBlockType.CHEST));
		assertTrue(WorkshopItemSearchChecks.isSearchableContainer(WorkshopBlockType.TRAPPED_CHEST));
		assertTrue(WorkshopItemSearchChecks.isSearchableContainer(WorkshopBlockType.BARREL));
		assertFalse(WorkshopItemSearchChecks.isSearchableContainer(WorkshopBlockType.FURNACE));
	}

	private static WorkshopSession session() {
		BlockPos center = new BlockPos(1, 64, 1);
		RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla("overworld"));
		return new WorkshopSession(
			11, 4, UUID.randomUUID(), dimension, center, center, WorkshopBlockType.CHEST,
			9, 0, 0, WorkshopScanResult.create(center, 8, 4, java.util.List.of())
		);
	}

	private static SearchWorkshopItemPayload request(long sessionId, long revision, int syncId) {
		return new SearchWorkshopItemPayload(7, sessionId, revision, syncId, Identifier.ofVanilla("iron_ingot"));
	}
}
