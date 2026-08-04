package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopItemCatalogPayload;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemCatalogChecksTest {
	@Test
	void identityValidationRejectsSessionRevisionAndHandlerMismatchesBeforeScanning() {
		WorkshopSession session = session();
		RequestWorkshopItemCatalogPayload request = new RequestWorkshopItemCatalogPayload(1, 11, 4, 9);

		assertEquals(WorkshopItemCatalogResultCode.SUCCESS, WorkshopItemCatalogChecks.validateIdentity(
			session, request, 9, true, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemCatalogResultCode.INVALID_SESSION, WorkshopItemCatalogChecks.validateIdentity(
			null, request, 9, true, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemCatalogResultCode.INVALID_SESSION, WorkshopItemCatalogChecks.validateIdentity(
			session, new RequestWorkshopItemCatalogPayload(1, 12, 4, 9), 9, true, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemCatalogResultCode.STALE_SESSION, WorkshopItemCatalogChecks.validateIdentity(
			session, new RequestWorkshopItemCatalogPayload(1, 11, 5, 9), 9, true, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemCatalogResultCode.INVALID_SESSION, WorkshopItemCatalogChecks.validateIdentity(
			session, new RequestWorkshopItemCatalogPayload(1, 11, 4, 10), 9, true, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemCatalogResultCode.INVALID_SESSION, WorkshopItemCatalogChecks.validateIdentity(
			session, request, 9, false, true, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemCatalogResultCode.INVALID_SESSION, WorkshopItemCatalogChecks.validateIdentity(
			session, request, 9, true, false, WorkshopSessionValidation.VALID
		));
		assertEquals(WorkshopItemCatalogResultCode.INVALID_SESSION, WorkshopItemCatalogChecks.validateIdentity(
			session, request, 9, true, true, WorkshopSessionValidation.OUT_OF_RANGE
		));
	}

	@Test
	void catalogCooldownIsAnIndependentTenTickWindow() {
		assertFalse(WorkshopItemCatalogChecks.cooldownElapsed(109, 100));
		assertTrue(WorkshopItemCatalogChecks.cooldownElapsed(110, 100));
		assertEquals(10, WorkshopItemCatalogChecks.COOLDOWN_TICKS);
	}

	private static WorkshopSession session() {
		RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.ofVanilla("overworld"));
		return new WorkshopSession(
			11, 4, UUID.randomUUID(), dimension, BlockPos.ORIGIN, BlockPos.ORIGIN,
			WorkshopBlockType.CHEST, 9, 0, 0,
			WorkshopScanResult.create(BlockPos.ORIGIN, 8, 4, List.of())
		);
	}
}
