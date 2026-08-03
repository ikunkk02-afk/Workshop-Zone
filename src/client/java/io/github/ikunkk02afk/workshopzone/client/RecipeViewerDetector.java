package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class RecipeViewerDetector {
	private static Set<DetectedRecipeViewer> detected = Set.of();
	private static boolean initialized;

	private RecipeViewerDetector() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		FabricLoader loader = FabricLoader.getInstance();
		detected = detect(loader.getAllMods().stream()
			.map(container -> container.getMetadata().getId())
			.toList());
		initialized = true;
		WorkshopZone.LOGGER.debug(
			"Detected recipe viewers: {}",
			detected.isEmpty() ? "none" : detected.stream().map(DetectedRecipeViewer::displayName).toList()
		);
	}

	static Set<DetectedRecipeViewer> detect(Collection<String> loadedModIds) {
		EnumSet<DetectedRecipeViewer> matches = EnumSet.noneOf(DetectedRecipeViewer.class);
		for (DetectedRecipeViewer viewer : DetectedRecipeViewer.values()) {
			if (loadedModIds.contains(viewer.modId())) {
				matches.add(viewer);
			}
		}
		return matches.isEmpty()
			? Set.of()
			: Collections.unmodifiableSet(EnumSet.copyOf(matches));
	}

	public static Set<DetectedRecipeViewer> detected() {
		return detected;
	}

	public static boolean hasAny() {
		return !detected.isEmpty();
	}
}
