package dev.vulkiris.pack;

import dev.vulkiris.VulkirisClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Installs the named shaders that ship with Vulkiris by extracting them from the mod's
 * resources into {@code shaderpacks/} on startup. Extraction is skipped when the folder
 * already exists, so users can open and edit them freely — they double as examples.
 */
public final class BundledPacks {
	private static final Map<String, List<String>> PACKS = Map.of(
			"vulkiris-aurora", List.of("vulkiris.pack.json", "shaders/aurora.fsh"),
			"vulkiris-noir", List.of("vulkiris.pack.json", "shaders/noir.fsh"),
			"vulkiris-crt", List.of("vulkiris.pack.json", "shaders/crt.fsh"));

	private BundledPacks() {
	}

	/** Hidden packs installed only when their Easter egg is discovered. */
	private static final Map<String, List<String>> EGG_PACKS = Map.of(
			"vulkiris-sannabi", List.of("vulkiris.pack.json", "shaders/sannabi.fsh"),
			"vulkiris-matrix", List.of("vulkiris.pack.json", "shaders/matrix.fsh"),
			"vulkiris-herobrine", List.of("vulkiris.pack.json", "shaders/herobrine.fsh"),
			"vulkiris-backrooms", List.of("vulkiris.pack.json", "shaders/backrooms.fsh"));

	public static void installMissing() {
		for (Map.Entry<String, List<String>> pack : PACKS.entrySet()) {
			install(pack.getKey(), pack.getValue());
		}
	}

	/** Installs an egg-unlocked pack. Returns true only when it was newly installed. */
	public static boolean installEggPack(String packId) {
		List<String> files = EGG_PACKS.get(packId);
		if (files == null || Files.isDirectory(PackRepository.dir().resolve(packId))) {
			return false;
		}
		return install(packId, files);
	}

	private static boolean install(String packId, List<String> files) {
		Path target = PackRepository.dir().resolve(packId);
		if (Files.isDirectory(target)) {
			return false;
		}
		try {
			for (String file : files) {
				Identifier id = Identifier.fromNamespaceAndPath(VulkirisClient.MOD_ID, "bundled/" + packId + "/" + file);
				Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
				if (resource.isEmpty()) {
					throw new IllegalStateException("Missing bundled resource " + id);
				}
				Path out = target.resolve(file);
				Files.createDirectories(out.getParent());
				try (InputStream in = resource.get().open()) {
					Files.write(out, in.readAllBytes());
				}
			}
			VulkirisClient.LOGGER.info("Installed bundled shader pack '{}'", packId);
			return true;
		} catch (Exception e) {
			VulkirisClient.LOGGER.warn("Could not install bundled pack '{}'", packId, e);
			return false;
		}
	}
}
