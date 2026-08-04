package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogEntry;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemCatalogResultCode;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemCatalogResultFilterTest {
	@Test
	void onlyCurrentCatalogRequestForCurrentSessionRevisionAndScreenIsAccepted() {
		WorkshopItemCatalogPayload payload = payload();

		assertTrue(WorkshopItemCatalogResultFilter.matches(payload, 7, 12, 3, 9));
		assertFalse(WorkshopItemCatalogResultFilter.matches(payload, 8, 12, 3, 9));
		assertFalse(WorkshopItemCatalogResultFilter.matches(payload, 7, 13, 3, 9));
		assertFalse(WorkshopItemCatalogResultFilter.matches(payload, 7, 12, 4, 9));
		assertFalse(WorkshopItemCatalogResultFilter.matches(payload, 7, 12, 3, 10));
		assertFalse(WorkshopItemCatalogResultFilter.matches(null, 7, 12, 3, 9));
	}

	@Test
	void clientCatalogCopiesEntriesAndSupportsMembershipChecks() {
		ClientWorkshopItemCatalog catalog = ClientWorkshopItemCatalog.fromPayload(payload());

		assertTrue(catalog.contains(Identifier.ofVanilla("iron_ingot")));
		assertFalse(catalog.contains(Identifier.ofVanilla("iron_chestplate")));
		assertThrows(UnsupportedOperationException.class, () -> catalog.entries().add(catalog.entries().getFirst()));
	}

	private static WorkshopItemCatalogPayload payload() {
		return new WorkshopItemCatalogPayload(
			7, 12, 3, 9, WorkshopItemCatalogResultCode.SUCCESS, 1, false,
			List.of(new WorkshopItemCatalogEntry(Identifier.ofVanilla("iron_ingot"), 64, 1, false))
		);
	}
}
