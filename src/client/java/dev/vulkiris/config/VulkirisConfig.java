package dev.vulkiris.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.vulkiris.VulkirisClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VulkirisConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static VulkirisConfig instance = new VulkirisConfig();

	public boolean enabled = true;

	/** One of: "aces", "filmic", "off". */
	public String tonemap = "aces";
	public float exposure = 1.0f;
	public float saturation = 1.08f;
	public float contrast = 1.02f;

	public boolean bloom = true;
	public float bloomIntensity = 0.35f;
	public float bloomThreshold = 0.72f;

	public boolean fog = true;
	public float fogDensity = 0.45f;
	public float sunScatter = 0.6f;

	/** 0 disables the vignette. */
	public float vignette = 0.18f;

	public boolean fxaa = false;

	public static VulkirisConfig get() {
		return instance;
	}

	public static void load() {
		Path path = configPath();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				VulkirisConfig loaded = GSON.fromJson(reader, VulkirisConfig.class);
				if (loaded != null) {
					instance = loaded.sanitized();
					return;
				}
			} catch (Exception e) {
				VulkirisClient.LOGGER.warn("Could not read {}; keeping defaults", path, e);
			}
		}
		instance = new VulkirisConfig();
		instance.save();
	}

	public void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			VulkirisClient.LOGGER.warn("Could not save {}", path, e);
		}
	}

	public int tonemapMode() {
		return switch (tonemap == null ? "" : tonemap.toLowerCase()) {
			case "aces" -> 1;
			case "filmic" -> 2;
			default -> 0;
		};
	}

	private VulkirisConfig sanitized() {
		exposure = clamp(exposure, 0.25f, 4.0f);
		saturation = clamp(saturation, 0.0f, 2.0f);
		contrast = clamp(contrast, 0.5f, 1.5f);
		bloomIntensity = clamp(bloomIntensity, 0.0f, 2.0f);
		bloomThreshold = clamp(bloomThreshold, 0.0f, 1.0f);
		fogDensity = clamp(fogDensity, 0.0f, 1.0f);
		sunScatter = clamp(sunScatter, 0.0f, 2.0f);
		vignette = clamp(vignette, 0.0f, 1.0f);
		return this;
	}

	private static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("vulkiris.json");
	}
}
