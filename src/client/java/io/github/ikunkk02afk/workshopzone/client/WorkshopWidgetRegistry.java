package io.github.ikunkk02afk.workshopzone.client;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class WorkshopWidgetRegistry {
	private WorkshopWidgetRegistry() {
	}

	static <T> T replaceSingle(List<T> widgets, Predicate<T> isWorkshopWidget, Supplier<T> factory) {
		widgets.removeIf(isWorkshopWidget);
		T widget = factory.get();
		widgets.add(widget);
		return widget;
	}
}
