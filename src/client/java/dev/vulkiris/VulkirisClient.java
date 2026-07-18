package dev.vulkiris;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.vulkiris.config.VulkirisConfig;
import dev.vulkiris.config.VulkirisPresets;
import dev.vulkiris.gui.VulkirisSettingsScreen;
import dev.vulkiris.render.VulkirisEggs;
import dev.vulkiris.render.VulkirisRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public final class VulkirisClient implements ClientModInitializer {
	public static final String MOD_ID = "vulkiris";
	public static final Logger LOGGER = LogUtils.getLogger();

	private static KeyMapping toggleKey;
	private static KeyMapping settingsKey;
	private static KeyMapping cyclePresetKey;
	private static KeyMapping reloadKey;

	@Override
	public void onInitializeClient() {
		VulkirisConfig.load();
		VulkirisPresets.reloadUserPresets();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vulkiris.toggle", GLFW.GLFW_KEY_K, category));
		settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vulkiris.settings", GLFW.GLFW_KEY_O, category));
		cyclePresetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vulkiris.cycle_preset", GLFW.GLFW_KEY_P, category));
		reloadKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vulkiris.reload_config", GLFW.GLFW_KEY_UNKNOWN, category));

		ClientTickEvents.END_CLIENT_TICK.register(VulkirisClient::onEndTick);
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> VulkirisRenderer.captureWaterDepth(context.gameRenderer()));
		// Secret chat words swap the whole look; the message is swallowed instead of sent.
		ClientSendMessageEvents.ALLOW_CHAT.register(message ->
				!VulkirisEggs.handleChatMessage(Minecraft.getInstance(), message));

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			DeviceInfo info = RenderSystem.getDevice().getDeviceInfo();
			LOGGER.info("Vulkiris running on the {} backend ({}, {})", info.backendName(), info.vendorName(), info.name());
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VulkirisRenderer.shutdown());

		LOGGER.info("Vulkiris initialized");
	}

	private static void onEndTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			VulkirisConfig config = VulkirisConfig.get();
			config.enabled = !config.enabled;
			config.save();
			VulkirisRenderer.clearFailure();
			feedback(client, Component.translatable(config.enabled ? "vulkiris.msg.enabled" : "vulkiris.msg.disabled"));
		}
		while (settingsKey.consumeClick()) {
			if (client.screen == null) {
				client.setScreenAndShow(new VulkirisSettingsScreen(null));
			}
		}
		while (cyclePresetKey.consumeClick()) {
			String id = VulkirisPresets.cycle(1);
			VulkirisRenderer.clearFailure();
			feedback(client, Component.translatable("vulkiris.msg.preset", VulkirisPresets.displayName(id)));
		}
		while (reloadKey.consumeClick()) {
			VulkirisConfig.load();
			VulkirisPresets.reloadUserPresets();
			VulkirisRenderer.clearFailure();
			feedback(client, Component.translatable("vulkiris.msg.reloaded"));
		}
	}

	public static void feedback(Minecraft client, Component message) {
		if (client.player != null) {
			client.player.sendOverlayMessage(message);
		}
	}
}
