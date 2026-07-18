package dev.vulkiris.pipeline;

import dev.vulkiris.VulkirisClient;

/**
 * Holds the active {@link VulkirisPipeline}. Today the only pipeline is the built-in
 * default; when shader-pack pipelines land, selecting a pack swaps the active pipeline here
 * (shutting the previous one down) without touching the render hooks.
 */
public final class PipelineManager {
	private static VulkirisPipeline active = new DefaultPipeline();

	private PipelineManager() {
	}

	public static VulkirisPipeline active() {
		return active;
	}

	public static void setActive(VulkirisPipeline pipeline) {
		if (pipeline == null || pipeline == active) {
			return;
		}
		VulkirisPipeline previous = active;
		active = pipeline;
		previous.shutdown();
		VulkirisClient.LOGGER.info("Vulkiris pipeline switched to {}", pipeline.id());
	}
}
