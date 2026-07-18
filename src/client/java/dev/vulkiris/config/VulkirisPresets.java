package dev.vulkiris.config;

import dev.vulkiris.VulkirisClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Built-in preset ladder plus user presets loaded from {@code config/vulkiris-presets/*.json}.
 * Presets can be shared as plain JSON through the clipboard (export/import buttons and keybind).
 */
public final class VulkirisPresets {
	public static final String CUSTOM = "custom";

	private static final Map<String, VulkirisConfig> BUILT_INS = new LinkedHashMap<>();
	private static final Map<String, VulkirisConfig> USER = new LinkedHashMap<>();

	static {
		builtIn("potato", c -> {
			c.quality = 0;
			c.sunlight = 0.0f;
			c.rimLight = 0.0f;
			c.bloom = false;
			c.lightBleed = 0.0f;
			c.aoStrength = 0.0f;
			c.water = false;
			c.godRays = 0.0f;
			c.ssrSteps = 0;
			c.sunSpecular = 0.0f;
			c.skyIntensity = 0.35f;
			c.fogDensity = 0.35f;
			c.vignette = 0.12f;
			c.fxaa = false;
		});
		builtIn("light", c -> {
			c.quality = 0;
			c.sunlight = 0.35f;
			c.rimLight = 0.15f;
			c.bloomIntensity = 0.25f;
			c.lightBleed = 0.15f;
			c.aoStrength = 0.3f;
			c.godRays = 0.0f;
			c.ssrSteps = 0;
			c.sunSpecular = 0.0f;
			c.skyIntensity = 0.5f;
		});
		builtIn("medium", c -> {
			// The defaults are the medium preset (quality tier: high).
		});
		builtIn("super", c -> {
			c.bloomIntensity = 0.38f;
			c.lightBleed = 0.3f;
			c.aoStrength = 0.65f;
			c.godRays = 0.55f;
			c.sunlight = 0.65f;
			c.rimLight = 0.3f;
			c.sunSpecular = 0.3f;
			c.skyIntensity = 0.75f;
		});
		builtIn("superplus", c -> {
			c.quality = 2;
			c.bloomIntensity = 0.42f;
			c.lightBleed = 0.32f;
			c.aoStrength = 0.7f;
			c.godRays = 0.7f;
			c.ssrSteps = 12;
			c.sunlight = 0.7f;
			c.rimLight = 0.3f;
			c.sunSpecular = 0.35f;
			c.skyIntensity = 0.85f;
		});
		builtIn("fabulous", c -> {
			c.quality = 2;
			c.bloomIntensity = 0.45f;
			c.lightBleed = 0.35f;
			c.aoStrength = 0.75f;
			c.godRays = 0.8f;
			c.ssrSteps = 24;
			c.sunlight = 0.75f;
			c.sunSpecular = 0.4f;
			c.skyIntensity = 1.0f;
			c.warmth = 0.06f;
			c.rimLight = 0.35f;
			c.fxaa = true;
		});
		builtIn("extreme", c -> {
			c.quality = 2;
			c.bloomIntensity = 0.5f;
			c.lightBleed = 0.4f;
			c.aoStrength = 0.85f;
			c.godRays = 1.0f;
			c.ssrSteps = 48;
			c.sunlight = 0.85f;
			c.sunSpecular = 0.45f;
			c.skyIntensity = 1.15f;
			c.fogDensity = 0.5f;
			c.rimLight = 0.4f;
			c.fxaa = true;
		});
		builtIn("reallife", c -> {
			c.quality = 2;
			c.tonemap = "aces";
			c.tonemapStrength = 0.95f;
			c.exposure = 0.78f;
			c.saturation = 0.98f;
			c.contrast = 1.05f;
			c.warmth = 0.03f;
			c.bloomIntensity = 0.3f;
			c.bloomThreshold = 0.8f;
			c.lightBleed = 0.2f;
			c.aoStrength = 0.9f;
			c.godRays = 0.6f;
			c.ssrSteps = 48;
			c.sunlight = 0.6f;
			c.sunSpecular = 0.5f;
			c.skyIntensity = 0.9f;
			c.vignette = 0.28f;
			c.rimLight = 0.0f;
			c.filmGrain = 0.045f;
			c.fxaa = true;
		});
	}

	private VulkirisPresets() {
	}

	private static void builtIn(String id, Consumer<VulkirisConfig> tweak) {
		VulkirisConfig values = new VulkirisConfig();
		tweak.accept(values);
		BUILT_INS.put(id, values.sanitized());
	}

	/** Re-scans {@code config/vulkiris-presets/} for user presets. */
	public static void reloadUserPresets() {
		USER.clear();
		Path dir = VulkirisConfig.presetsDir();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(path -> {
				try {
					VulkirisConfig values = VulkirisConfig.GSON.fromJson(Files.readString(path), VulkirisConfig.class);
					if (values != null) {
						String name = path.getFileName().toString();
						USER.put(name.substring(0, name.length() - ".json".length()), values.sanitized());
					}
				} catch (Exception e) {
					VulkirisClient.LOGGER.warn("Skipping unreadable preset {}", path, e);
				}
			});
		} catch (IOException e) {
			VulkirisClient.LOGGER.warn("Could not list presets in {}", dir, e);
		}
	}

	public static List<String> ids() {
		List<String> ids = new ArrayList<>(BUILT_INS.keySet());
		ids.addAll(USER.keySet());
		return ids;
	}

	public static Component displayName(String id) {
		if (BUILT_INS.containsKey(id)) {
			return Component.translatable("vulkiris.preset." + id);
		}
		if (CUSTOM.equals(id)) {
			return Component.translatable("vulkiris.preset.custom");
		}
		return Component.literal(id);
	}

	/** Applies a preset by id (built-in or user) to the live config and saves. */
	public static boolean apply(String id) {
		VulkirisConfig values = BUILT_INS.containsKey(id) ? BUILT_INS.get(id) : USER.get(id);
		if (values == null) {
			return false;
		}
		VulkirisConfig config = VulkirisConfig.get();
		config.copyVisualsFrom(values);
		config.preset = id;
		config.save();
		return true;
	}

	/** Applies the next preset in the cycle order and returns its id. */
	public static String cycle(int direction) {
		List<String> ids = ids();
		if (ids.isEmpty()) {
			return CUSTOM;
		}
		int index = ids.indexOf(VulkirisConfig.get().preset);
		int next = index < 0 ? 0 : Math.floorMod(index + direction, ids.size());
		String id = ids.get(next);
		apply(id);
		return id;
	}

	/** Copies the current settings to the system clipboard as shareable JSON. */
	public static void exportToClipboard(Minecraft minecraft) {
		minecraft.keyboardHandler.setClipboard(VulkirisConfig.GSON.toJson(VulkirisConfig.get()));
	}

	/**
	 * Imports settings JSON from the clipboard, applies it, and stores it as a user preset
	 * file so it joins the preset cycle. Returns the new preset id, or null on failure.
	 */
	public static String importFromClipboard(Minecraft minecraft) {
		try {
			String clipboard = minecraft.keyboardHandler.getClipboard();
			if (clipboard == null || clipboard.isBlank()) {
				return null;
			}
			VulkirisConfig values = VulkirisConfig.GSON.fromJson(clipboard, VulkirisConfig.class);
			if (values == null) {
				return null;
			}
			values.sanitized();

			Path dir = VulkirisConfig.presetsDir();
			Files.createDirectories(dir);
			String id = "imported";
			for (int i = 1; Files.exists(dir.resolve(id + ".json")); i++) {
				id = "imported-" + i;
			}
			Files.writeString(dir.resolve(id + ".json"), VulkirisConfig.GSON.toJson(values));

			reloadUserPresets();
			apply(id);
			return id;
		} catch (Exception e) {
			VulkirisClient.LOGGER.warn("Preset import failed", e);
			return null;
		}
	}
}
