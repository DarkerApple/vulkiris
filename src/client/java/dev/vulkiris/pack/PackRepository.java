package dev.vulkiris.pack;

import dev.vulkiris.VulkirisClient;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Discovers Vulkiris shader packs (folders or zips) in {@code <game>/shaderpacks/}. */
public final class PackRepository {
	private PackRepository() {
	}

	public static Path dir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("shaderpacks");
	}

	public static void ensureDir() {
		try {
			Files.createDirectories(dir());
		} catch (IOException e) {
			VulkirisClient.LOGGER.warn("Could not create {}", dir(), e);
		}
	}

	/** Returns ids of packs that contain a vulkiris.pack.json (other packs are ignored). */
	public static List<String> scan() {
		List<String> ids = new ArrayList<>();
		Path dir = dir();
		if (!Files.isDirectory(dir)) {
			return ids;
		}
		try (Stream<Path> entries = Files.list(dir)) {
			entries.sorted().forEach(entry -> {
				String fileName = entry.getFileName().toString();
				if (Files.isDirectory(entry) && Files.isRegularFile(entry.resolve("vulkiris.pack.json"))) {
					ids.add(fileName);
				} else if (fileName.endsWith(".zip") && zipHasManifest(entry)) {
					ids.add(fileName.substring(0, fileName.length() - 4));
				}
			});
		} catch (IOException e) {
			VulkirisClient.LOGGER.warn("Could not scan {}", dir, e);
		}
		return ids;
	}

	/** Opens a pack by the id produced by {@link #scan()}. The caller owns the pack. */
	public static ShaderPack open(String id) throws IOException {
		Path folder = dir().resolve(id);
		if (Files.isDirectory(folder)) {
			return ShaderPack.load(folder);
		}
		Path zip = dir().resolve(id + ".zip");
		if (Files.isRegularFile(zip)) {
			return ShaderPack.load(zip);
		}
		throw new IOException("Shader pack not found: " + id);
	}

	private static boolean zipHasManifest(Path zip) {
		try (FileSystem fs = FileSystems.newFileSystem(zip)) {
			return Files.isRegularFile(fs.getPath("/vulkiris.pack.json"));
		} catch (IOException e) {
			return false;
		}
	}
}
