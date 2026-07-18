package dev.vulkiris.pack;

import com.google.gson.reflect.TypeToken;
import dev.vulkiris.VulkirisClient;
import dev.vulkiris.config.VulkirisConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists per-pack setting values (declared by the pack's {@code settings} array) to
 * {@code config/vulkiris-packsettings/<packId>.json}. Values are read every frame by
 * {@link dev.vulkiris.pipeline.PackPipeline} and uploaded in the pack settings uniform block.
 */
public final class PackSettingsStore {
	private static final Type MAP_TYPE = new TypeToken<Map<String, Float>>() {
	}.getType();
	private static final Map<String, Map<String, Float>> CACHE = new HashMap<>();

	private PackSettingsStore() {
	}

	public static Map<String, Float> values(String packId) {
		return CACHE.computeIfAbsent(packId, PackSettingsStore::loadFile);
	}

	public static float value(String packId, ShaderPack.Setting setting) {
		Float stored = values(packId).get(setting.id());
		float value = stored != null ? stored : setting.def();
		return Math.max(setting.min(), Math.min(setting.max(), value));
	}

	public static void set(String packId, String settingId, float value) {
		values(packId).put(settingId, value);
		save(packId);
	}

	public static void applyPreset(String packId, Map<String, Float> presetValues) {
		values(packId).putAll(presetValues);
		save(packId);
	}

	private static Map<String, Float> loadFile(String packId) {
		Path path = dir().resolve(packId + ".json");
		if (Files.isRegularFile(path)) {
			try {
				Map<String, Float> loaded = VulkirisConfig.GSON.fromJson(Files.readString(path), MAP_TYPE);
				if (loaded != null) {
					return new HashMap<>(loaded);
				}
			} catch (Exception e) {
				VulkirisClient.LOGGER.warn("Could not read pack settings {}", path, e);
			}
		}
		return new HashMap<>();
	}

	private static void save(String packId) {
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve(packId + ".json"), VulkirisConfig.GSON.toJson(values(packId)));
		} catch (Exception e) {
			VulkirisClient.LOGGER.warn("Could not save pack settings for {}", packId, e);
		}
	}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("vulkiris-packsettings");
	}
}
