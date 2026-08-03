package io.github.ikunkk02afk.workshopzone.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

public final class WorkshopClientConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "workshop_zone-client.json";
	private static WorkshopClientConfig current = WorkshopClientConfig.defaults();
	private static Path configPath;
	private static boolean initialized;

	private WorkshopClientConfigManager() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		current = load(configPath);
		initialized = true;
	}

	public static WorkshopClientConfig get() {
		return current;
	}

	public static synchronized boolean update(WorkshopClientConfig updated) {
		WorkshopClientConfig sanitized = updated == null ? WorkshopClientConfig.defaults() : updated;
		if (sanitized.equals(current)) {
			return false;
		}
		if (configPath != null) {
			try {
				save(configPath, sanitized);
			} catch (IOException exception) {
				WorkshopZone.LOGGER.warn("Failed to save client config {}", configPath, exception);
				return false;
			}
		}
		current = sanitized;
		return true;
	}

	static WorkshopClientConfig load(Path path) {
		WorkshopClientConfig defaults = WorkshopClientConfig.defaults();
		if (!Files.exists(path)) {
			try {
				save(path, defaults);
			} catch (IOException exception) {
				WorkshopZone.LOGGER.warn("Failed to create default client config {}", path, exception);
			}
			return defaults;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject()) {
				throw new IOException("Client config root must be a JSON object");
			}
			JsonObject json = root.getAsJsonObject();
			return new WorkshopClientConfig(
				readInt(json, "version", WorkshopClientConfig.CURRENT_VERSION),
				WorkshopSidebarPosition.fromId(readString(json, "sidebarPosition", "auto")),
				readBoolean(json, "autoAvoidRecipeViewers", true),
				readDouble(json, "customX", WorkshopClientConfig.DEFAULT_CUSTOM_X),
				readDouble(json, "customY", WorkshopClientConfig.DEFAULT_CUSTOM_Y)
			);
		} catch (Exception exception) {
			backupDamagedFile(path, exception);
			try {
				save(path, defaults);
			} catch (IOException saveException) {
				WorkshopZone.LOGGER.warn("Failed to replace damaged client config {}", path, saveException);
			}
			return defaults;
		}
	}

	static void save(Path path, WorkshopClientConfig config) throws IOException {
		Path parent = path.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		JsonObject json = new JsonObject();
		json.addProperty("version", WorkshopClientConfig.CURRENT_VERSION);
		json.addProperty("sidebarPosition", config.sidebarPosition().id());
		json.addProperty("autoAvoidRecipeViewers", config.autoAvoidRecipeViewers());
		json.addProperty("customX", config.customX());
		json.addProperty("customY", config.customY());
		try (Writer writer = Files.newBufferedWriter(temporary)) {
			GSON.toJson(json, writer);
		}
		try {
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void backupDamagedFile(Path path, Exception exception) {
		Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + Instant.now().toEpochMilli() + ".bak");
		try {
			Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
			WorkshopZone.LOGGER.warn("Damaged client config moved to {}; defaults will be used", backup, exception);
		} catch (IOException backupException) {
			WorkshopZone.LOGGER.warn("Ignoring damaged client config {}; backup failed", path, exception);
			WorkshopZone.LOGGER.debug("Client config backup failure", backupException);
		}
	}

	private static int readInt(JsonObject json, String key, int fallback) {
		try {
			return json.has(key) ? json.get(key).getAsInt() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static boolean readBoolean(JsonObject json, String key, boolean fallback) {
		try {
			return json.has(key) ? json.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static String readString(JsonObject json, String key, String fallback) {
		try {
			return json.has(key) ? json.get(key).getAsString() : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}

	private static double readDouble(JsonObject json, String key, double fallback) {
		try {
			if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) {
				return fallback;
			}
			double value = json.get(key).getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException exception) {
			return fallback;
		}
	}
}
