package io.github.ikunkk02afk.workshopzone.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopClientConfigTest {
	@TempDir
	Path tempDir;

	@Test
	void unknownPositionFallsBackToAuto() throws IOException {
		Path path = tempDir.resolve("client.json");
		Files.writeString(path, "{\"version\":1,\"sidebarPosition\":\"future-mode\"}");

		WorkshopClientConfig config = WorkshopClientConfigManager.load(path);

		assertEquals(WorkshopSidebarPosition.AUTO, config.sidebarPosition());
	}

	@Test
	void nonFiniteAndOutOfRangeCoordinatesAreSanitized() throws IOException {
		Path path = tempDir.resolve("client.json");
		Files.writeString(path, "{\"version\":1,\"customX\":\"NaN\",\"customY\":7.5}");

		WorkshopClientConfig config = WorkshopClientConfigManager.load(path);

		assertEquals(WorkshopClientConfig.DEFAULT_CUSTOM_X, config.customX());
		assertEquals(1.0, config.customY());
	}

	@Test
	void damagedJsonUsesDefaultsAndCreatesBackup() throws IOException {
		Path path = tempDir.resolve("client.json");
		Files.writeString(path, "{not-json");

		WorkshopClientConfig config = WorkshopClientConfigManager.load(path);

		assertEquals(WorkshopClientConfig.defaults(), config);
		try (var files = Files.list(tempDir)) {
			assertTrue(files.anyMatch(candidate -> candidate.getFileName().toString().startsWith("client.json.corrupt-")));
		}
	}

	@Test
	void configRoundTripPreservesStableValues() throws IOException {
		Path path = tempDir.resolve("client.json");
		WorkshopClientConfig expected = new WorkshopClientConfig(
			1, WorkshopSidebarPosition.BOTTOM, false, 0.25, 0.75
		);

		WorkshopClientConfigManager.save(path, expected);
		WorkshopClientConfig actual = WorkshopClientConfigManager.load(path);

		assertEquals(expected, actual);
		assertFalse(Files.exists(path.resolveSibling("client.json.tmp")));
	}

	@Test
	void resetRestoresAutoAvoidAndDefaultCustomPosition() {
		WorkshopClientConfig changed = new WorkshopClientConfig(
			1, WorkshopSidebarPosition.CUSTOM, false, 1.0, 1.0
		);

		assertEquals(WorkshopClientConfig.defaults(), changed.reset());
	}
}
