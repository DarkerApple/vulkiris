package dev.vulkiris.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vulkiris.render.VulkirisViewmodel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
	private void vulkiris$applyViewmodel(
			float frameInterp,
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			LocalPlayer player,
			int lightCoords,
			CallbackInfo ci) {
		VulkirisViewmodel.apply(poseStack, player, frameInterp);
	}
}
