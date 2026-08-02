package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkshopWidgetRegistryTest {
	@Test
	void repeatedInitializationKeepsExactlyOneWorkshopWidget() {
		List<String> widgets = new ArrayList<>(List.of("vanilla", "workshop-old"));

		WorkshopWidgetRegistry.replaceSingle(widgets, value -> value.startsWith("workshop-"), () -> "workshop-first");
		WorkshopWidgetRegistry.replaceSingle(widgets, value -> value.startsWith("workshop-"), () -> "workshop-current");

		assertEquals(List.of("vanilla", "workshop-current"), widgets);
	}
}
