package dev.vulkiris.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * Offscreen render targets for the post chain: a full-resolution snapshot of the scene
 * (color + depth, so passes never read the target they are writing) and two half-resolution
 * ping-pong targets for bloom.
 */
final class PostFxTargets {
	private static final Vector4fc CLEAR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

	RenderTarget sceneCopy;
	RenderTarget bloomA;
	RenderTarget bloomB;
	private int width = -1;
	private int height = -1;

	void ensure(int targetWidth, int targetHeight) {
		// Check every target so a partially-failed allocation is rebuilt on retry.
		if (this.sceneCopy != null && this.bloomA != null && this.bloomB != null
				&& this.width == targetWidth && this.height == targetHeight) {
			return;
		}
		this.close();
		this.width = targetWidth;
		this.height = targetHeight;
		this.sceneCopy = create(targetWidth, targetHeight, true);
		int bloomWidth = Math.max(1, targetWidth / 2);
		int bloomHeight = Math.max(1, targetHeight / 2);
		this.bloomA = create(bloomWidth, bloomHeight, false);
		this.bloomB = create(bloomWidth, bloomHeight, false);
	}

	void close() {
		if (this.sceneCopy != null) {
			this.sceneCopy.destroyBuffers();
			this.sceneCopy = null;
		}
		if (this.bloomA != null) {
			this.bloomA.destroyBuffers();
			this.bloomA = null;
		}
		if (this.bloomB != null) {
			this.bloomB.destroyBuffers();
			this.bloomB = null;
		}
		this.width = -1;
		this.height = -1;
	}

	private static RenderTarget create(int width, int height, boolean useDepth) {
		RenderTargetDescriptor descriptor = new RenderTargetDescriptor(width, height, useDepth, CLEAR, GpuFormat.RGBA8_UNORM);
		RenderTarget target = descriptor.allocate();
		descriptor.prepare(target);
		return target;
	}
}
