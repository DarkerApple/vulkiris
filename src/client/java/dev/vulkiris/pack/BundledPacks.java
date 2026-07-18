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

	public static void installMissing() {
		for (Map.Entry<String, List<String>> pack : PACKS.entrySet()) {
			Path target = PackRepository.dir().resolve(pack.getKey());
			if (Files.isDirectory(target)) {
				continue;
			}
			try {
				for (String file : pack.getValue()) {
					Identifier id = Identifier.fromNamespaceAndPath(VulkirisClient.MOD_ID, "bundled/" + pack.getKey() + "/" + file);
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
				VulkirisClient.LOGGER.info("Installed bundled shader pack '{}'", pack.getKey());
			} catch (Exception e) {
				VulkirisClient.LOGGER.warn("Could not install bundled pack '{}'", pack.getKey(), e);
			}
		}
	}
}
