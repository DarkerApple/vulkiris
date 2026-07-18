package dev.vulkiris.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.vulkiris.config.VulkirisConfig;
import net.minecraft.client.player.LocalPlayer;

/**
 * First-person viewmodel adjustments: scale, offset, rotation, and an extra attack-swing
 * "punch" layered on top of the vanilla animation. Applied once at the top of the hand
 * render, so both hands and held items are affected together.
 */
public final class VulkirisViewmodel {
	private VulkirisViewmodel() {
	}

	public static void apply(PoseStack poseStack, LocalPlayer player, float frameInterp) {
		VulkirisConfig config = VulkirisConfig.get();
		boolean identity = config.vmScale == 1.0f && config.vmOffsetX == 0.0f && config.vmOffsetY == 0.0f
				&& config.vmRotation == 0.0f && config.vmSwing == 0.0f;
		if (identity) {
			return;
		}

		poseStack.translate(config.vmOffsetX, config.vmOffsetY, 0.0f);
		if (config.vmRotation != 0.0f) {
			poseStack.mulPose(Axis.YP.rotationDegrees(config.vmRotation));
		}
		if (config.vmScale != 1.0f) {
			poseStack.scale(config.vmScale, config.vmScale, config.vmScale);
		}

		// Attack punch: a quick roll + dip driven by the vanilla swing curve.
		if (config.vmSwing > 0.0f && player != null) {
			float attack = player.getAttackAnim(frameInterp);
			if (attack > 0.0f) {
				float punch = (float) Math.sin(attack * Math.PI);
				poseStack.mulPose(Axis.ZP.rotationDegrees(-9.0f * punch * config.vmSwing));
				poseStack.mulPose(Axis.XP.rotationDegrees(4.0f * punch * config.vmSwing));
				poseStack.translate(0.0f, -0.04f * punch * config.vmSwing, -0.05f * punch * config.vmSwing);
			}
		}
	}
}
