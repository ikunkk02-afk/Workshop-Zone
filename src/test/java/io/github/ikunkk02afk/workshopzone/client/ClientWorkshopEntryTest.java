package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientWorkshopEntryTest {
	@Test
	void labelIconsAreCopiedAndExposedAsAnUnmodifiableList() {
		List<String> mutable = new ArrayList<>(List.of("copper", "gold"));
		List<String> icons = ClientWorkshopEntry.immutablePreviewList(mutable, 4);
		mutable.clear();

		assertEquals(List.of("copper", "gold"), icons);
		assertThrows(UnsupportedOperationException.class, () -> icons.add("iron"));
		assertThrows(IllegalArgumentException.class, () -> ClientWorkshopEntry.immutablePreviewList(
			List.of("a", "b", "c", "d", "e"), 4
		));
	}
}
