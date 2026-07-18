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
	/** How strongly the tonemap curve is applied (0 = untouched image). */
	public float tonemapStrength = 0.7f;
	public float exposure = 0.82f;
	public float saturation = 1.06f;
	public float contrast = 1.02f;
	/** Warm white balance shift; 0 is neutral. */
	public float warmth = 0.05f;

	public boolean bloom = true;
	public float bloomIntensity = 0.32f;
	public float bloomThreshold = 0.72f;

	public boolean fog = true;
	public float fogDensity = 0.45f;
	public float sunScatter = 0.6f;

	/** Sunset/sunrise gradients and day/night sky grading on sky, clouds, and far terrain. */
	public float skyIntensity = 0.6f;

	/** Screen-space ambient occlusion strength; 0 disables (shares the bloom blur, nearly free). */
	public float aoStrength = 0.55f;

	/** Water surface shading: sun glint, depth absorption, fresnel sky tint. */
	public boolean water = true;

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

	public void cycleTonemap() {
		tonemap = switch (tonemapMode()) {
			case 1 -> "filmic";
			case 2 -> "off";
			default -> "aces";
		};
	}

	private VulkirisConfig sanitized() {
		if (tonemap == null || tonemap.isBlank()) {
			tonemap = "aces";
		}
		tonemapStrength = clamp(tonemapStrength, 0.0f, 1.0f);
		exposure = clamp(exposure, 0.25f, 4.0f);
		saturation = clamp(saturation, 0.0f, 2.0f);
		contrast = clamp(contrast, 0.5f, 1.5f);
		warmth = clamp(warmth, 0.0f, 0.25f);
		bloomIntensity = clamp(bloomIntensity, 0.0f, 2.0f);
		bloomThreshold = clamp(bloomThreshold, 0.0f, 1.0f);
		fogDensity = clamp(fogDensity, 0.0f, 1.0f);
		sunScatter = clamp(sunScatter, 0.0f, 2.0f);
		skyIntensity = clamp(skyIntensity, 0.0f, 1.5f);
		aoStrength = clamp(aoStrength, 0.0f, 1.0f);
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
