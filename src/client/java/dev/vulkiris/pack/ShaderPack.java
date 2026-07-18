package dev.vulkiris.pack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A loaded Vulkiris shader pack: a folder or zip in {@code shaderpacks/} containing
 * {@code vulkiris.pack.json} plus GLSL files. See docs/PACK_FORMAT.md for the format.
 */
public final class ShaderPack implements Closeable {
	private static final Gson GSON = new Gson();
	private static final int MAX_PASSES = 8;
	private static final int MAX_INCLUDE_DEPTH = 8;
	private static final Pattern RESOURCE_IMPORT = Pattern.compile("#moj_import\\s*<([^>]+)>");
	private static final Pattern LOCAL_IMPORT = Pattern.compile("#moj_import\\s*\"([^\"]+)\"");
	private static final Pattern VERSION_LINE = Pattern.compile("^#version[^\\n]*\\n");

	/** One fullscreen pass. {@code scale} sizes its output target relative to the screen. */
	public record Pass(String fragment, float scale) {
	}

	private final String id;
	private final Path root;
	private final @org.jspecify.annotations.Nullable FileSystem zipFs;
	private final String name;
	private final String author;
	private final boolean waterDepth;
	private final List<Pass> passes;

	private ShaderPack(String id, Path root, FileSystem zipFs, String name, String author, boolean waterDepth, List<Pass> passes) {
		this.id = id;
		this.root = root;
		this.zipFs = zipFs;
		this.name = name;
		this.author = author;
		this.waterDepth = waterDepth;
		this.passes = passes;
	}

	/** Loads a pack from a folder or a .zip file. The caller owns the returned pack. */
	public static ShaderPack load(Path source) throws IOException {
		String id = fileName(source);
		FileSystem zipFs = null;
		Path root = source;
		if (!Files.isDirectory(source)) {
			zipFs = FileSystems.newFileSystem(source);
			root = zipFs.getPath("/");
			if (id.endsWith(".zip")) {
				id = id.substring(0, id.length() - 4);
			}
		}
		try {
			JsonObject json = GSON.fromJson(Files.readString(root.resolve("vulkiris.pack.json")), JsonObject.class);
			String name = json.has("name") ? json.get("name").getAsString() : id;
			String author = json.has("author") ? json.get("author").getAsString() : "";
			boolean waterDepth = json.has("waterDepth") && json.get("waterDepth").getAsBoolean();
			List<Pass> passes = new ArrayList<>();
			JsonArray passArray = json.getAsJsonArray("passes");
			if (passArray == null || passArray.isEmpty()) {
				throw new IOException("vulkiris.pack.json declares no passes");
			}
			for (var element : passArray) {
				JsonObject pass = element.getAsJsonObject();
				float scale = pass.has("scale") ? pass.get("scale").getAsFloat() : 1.0f;
				passes.add(new Pass(pass.get("fragment").getAsString(), Math.max(0.1f, Math.min(1.0f, scale))));
			}
			if (passes.size() > MAX_PASSES) {
				throw new IOException("Too many passes (max " + MAX_PASSES + ")");
			}
			return new ShaderPack(id, root, zipFs, name, author, waterDepth, List.copyOf(passes));
		} catch (IOException | RuntimeException e) {
			if (zipFs != null) {
				zipFs.close();
			}
			throw e instanceof IOException io ? io : new IOException("Invalid vulkiris.pack.json", e);
		}
	}

	public String id() {
		return this.id;
	}

	public String name() {
		return this.name;
	}

	public String author() {
		return this.author;
	}

	/** Whether the pack wants the pre-translucent depth snapshot bound as WaterDepthSampler. */
	public boolean waterDepth() {
		return this.waterDepth;
	}

	public List<Pass> passes() {
		return this.passes;
	}

	/** Reads a pack shader file with {@code #moj_import} directives expanded. */
	public String readShader(String relativePath) throws IOException {
		return this.expand(Files.readString(this.root.resolve(relativePath)), 0);
	}

	private String expand(String source, int depth) throws IOException {
		if (depth > MAX_INCLUDE_DEPTH) {
			throw new IOException("#moj_import nesting too deep");
		}
		StringBuilder out = new StringBuilder();
		for (String line : source.split("\n", -1)) {
			Matcher resource = RESOURCE_IMPORT.matcher(line);
			Matcher local = LOCAL_IMPORT.matcher(line);
			if (resource.find()) {
				out.append(stripVersion(readResourceInclude(resource.group(1)))).append('\n');
			} else if (local.find()) {
				out.append(stripVersion(this.expand(Files.readString(this.root.resolve(local.group(1))), depth + 1))).append('\n');
			} else {
				out.append(line).append('\n');
			}
		}
		return out.toString();
	}

	/** Resolves {@code <ns:file.glsl>} the way vanilla does: assets/ns/shaders/include/file.glsl. */
	private static String readResourceInclude(String reference) throws IOException {
		String namespace = "minecraft";
		String path = reference;
		int colon = reference.indexOf(':');
		if (colon >= 0) {
			namespace = reference.substring(0, colon);
			path = reference.substring(colon + 1);
		}
		Identifier id = Identifier.fromNamespaceAndPath(namespace, "shaders/include/" + path);
		Optional<Resource> found = Minecraft.getInstance().getResourceManager().getResource(id);
		if (found.isEmpty()) {
			throw new IOException("Unknown include " + reference);
		}
		try (InputStream in = found.get().open()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String stripVersion(String source) {
		return VERSION_LINE.matcher(source).replaceFirst("");
	}

	private static String fileName(Path path) {
		Path name = path.getFileName();
		return name == null ? path.toString() : name.toString();
	}

	@Override
	public void close() throws IOException {
		if (this.zipFs != null) {
			this.zipFs.close();
		}
	}
}
