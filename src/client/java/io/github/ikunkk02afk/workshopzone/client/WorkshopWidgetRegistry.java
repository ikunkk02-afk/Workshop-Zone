package io.github.ikunkk02afk.workshopzone.client;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class WorkshopWidgetRegistry {
	private WorkshopWidgetRegistry() {
	}

	static <T, U extends T> U replaceSingle(List<T> widgets, Predicate<T> isWorkshopWidget, Supplier<U> factory) {
		widgets.removeIf(isWorkshopWidget);
		U widget = factory.get();
		widgets.add(widget);
		return widget;
	}
}
