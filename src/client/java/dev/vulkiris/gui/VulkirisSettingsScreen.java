package dev.vulkiris.gui;

import dev.vulkiris.config.VulkirisConfig;
import dev.vulkiris.config.VulkirisPresets;
import dev.vulkiris.render.VulkirisRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Settings screen: preset selector and share buttons on top, then two pages of controls
 * ("Look" and "Effects"). Every widget writes straight into {@link VulkirisConfig}, so changes
 * are visible immediately behind the (un-blurred) screen; the file is saved on close. Hand-tuning
 * any control marks the preset as "custom".
 */
public final class VulkirisSettingsScreen extends Screen {
	private static final int WIDGET_WIDTH = 150;
	private static final int WIDGET_HEIGHT = 20;
	private static final int GAP_X = 8;
	private static final int GAP_Y = 4;

	private final @Nullable Screen parent;
	/** 0 = Look, 1 = Effects, 2 = Style. */
	private int page;
	private int nextIndex;
	private Component status = Component.empty();

	public VulkirisSettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("vulkiris.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		VulkirisPresets.reloadUserPresets();
		VulkirisConfig config = VulkirisConfig.get();
		this.nextIndex = 0;
		int centerX = this.width / 2;

		// Preset row: < [preset name] >
		Button prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.cyclePreset(-1)).build());
		prev.setWidth(20);
		prev.setX(centerX - WIDGET_WIDTH - GAP_X / 2);
		prev.setY(this.rowY(0));
		Button presetButton = this.addRenderableWidget(Button.builder(this.presetLabel(), b -> this.cyclePreset(1)).build());
		presetButton.setWidth(2 * WIDGET_WIDTH + GAP_X - 48);
		presetButton.setX(centerX - WIDGET_WIDTH - GAP_X / 2 + 24);
		presetButton.setY(this.rowY(0));
		Button next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.cyclePreset(1)).build());
		next.setWidth(20);
		next.setX(centerX + WIDGET_WIDTH + GAP_X / 2 - 20);
		next.setY(this.rowY(0));
		this.nextIndex = 2;

		// Share row.
		this.addButton(Component.translatable("vulkiris.button.export"), () -> {
			VulkirisPresets.exportToClipboard(this.minecraft);
			this.status = Component.translatable("vulkiris.msg.exported");
		});
		this.addButton(Component.translatable("vulkiris.button.import"), () -> {
			String id = VulkirisPresets.importFromClipboard(this.minecraft);
			this.status = id != null
					? Component.translatable("vulkiris.msg.imported", VulkirisPresets.displayName(id))
					: Component.translatable("vulkiris.msg.import_failed");
			VulkirisRenderer.clearFailure();
			this.rebuild();
		});

		if (this.page == 0) {
			this.addToggle(() -> onOff("vulkiris.option.effects", config.enabled), () -> {
				config.enabled = !config.enabled;
				VulkirisRenderer.clearFailure();
			}, false);
			this.addCycle(() -> label("vulkiris.option.tonemap", Component.translatable("vulkiris.tonemap." + config.tonemap)), config::cycleTonemap);
			this.addSlider("vulkiris.option.brightness", 0.25f, 2.0f, config.exposure, v -> config.exposure = v);
			this.addSlider("vulkiris.option.tonemap_strength", 0.0f, 1.0f, config.tonemapStrength, v -> config.tonemapStrength = v);
			this.addSlider("vulkiris.option.warmth", 0.0f, 0.25f, config.warmth, v -> config.warmth = v);
			this.addSlider("vulkiris.option.saturation", 0.5f, 1.5f, config.saturation, v -> config.saturation = v);
			this.addSlider("vulkiris.option.contrast", 0.8f, 1.2f, config.contrast, v -> config.contrast = v);
			this.addSlider("vulkiris.option.vignette", 0.0f, 0.6f, config.vignette, v -> config.vignette = v);
			this.addToggle(() -> onOff("vulkiris.option.fxaa", config.fxaa), () -> config.fxaa = !config.fxaa);
			this.addCycle(() -> label("vulkiris.option.quality", Component.translatable("vulkiris.quality." + config.quality)), config::cycleQuality);
			this.addSlider("vulkiris.option.film_grain", 0.0f, 0.15f, config.filmGrain, v -> config.filmGrain = v);
		} else if (this.page == 1) {
			this.addToggle(() -> onOff("vulkiris.option.bloom", config.bloom), () -> config.bloom = !config.bloom);
			this.addSlider("vulkiris.option.bloom_intensity", 0.0f, 1.5f, config.bloomIntensity, v -> config.bloomIntensity = v);
			this.addSlider("vulkiris.option.bloom_threshold", 0.0f, 1.0f, config.bloomThreshold, v -> config.bloomThreshold = v);
			this.addSlider("vulkiris.option.light_bleed", 0.0f, 1.0f, config.lightBleed, v -> config.lightBleed = v);
			this.addSlider("vulkiris.option.ao", 0.0f, 1.0f, config.aoStrength, v -> config.aoStrength = v);
			this.addToggle(() -> onOff("vulkiris.option.fog", config.fog), () -> config.fog = !config.fog);
			this.addSlider("vulkiris.option.fog_density", 0.0f, 1.0f, config.fogDensity, v -> config.fogDensity = v);
			this.addSlider("vulkiris.option.sun_scatter", 0.0f, 2.0f, config.sunScatter, v -> config.sunScatter = v);
			this.addSlider("vulkiris.option.god_rays", 0.0f, 1.0f, config.godRays, v -> config.godRays = v);
			this.addSlider("vulkiris.option.sky", 0.0f, 1.5f, config.skyIntensity, v -> config.skyIntensity = v);
			this.addToggle(() -> onOff("vulkiris.option.water", config.water), () -> config.water = !config.water);
		} else {
			this.addCycle(() -> label("vulkiris.option.ssr", Component.translatable(ssrKey(config.ssrSteps))), config::cycleSsr);
			this.addSlider("vulkiris.option.sun_specular", 0.0f, 1.0f, config.sunSpecular, v -> config.sunSpecular = v);
			this.addToggle(() -> onOff("vulkiris.option.selective_bloom", config.selectiveBloom), () -> config.selectiveBloom = !config.selectiveBloom);
			this.addSlider("vulkiris.option.rim_light", 0.0f, 1.0f, config.rimLight, v -> config.rimLight = v);
			this.addToggle(() -> onOff("vulkiris.option.toon", config.toon), () -> config.toon = !config.toon);
		}

		// Bottom row: page switch + done.
		int bottomRow = this.nextIndex + (this.nextIndex % 2);
		String nextPageKey = this.page == 0 ? "vulkiris.button.page_effects" : this.page == 1 ? "vulkiris.button.page_style" : "vulkiris.button.page_look";
		Button pageButton = this.addRenderableWidget(Button.builder(
				Component.translatable(nextPageKey),
				b -> {
					this.page = (this.page + 1) % 3;
					this.rebuild();
				}).build());
		pageButton.setWidth(WIDGET_WIDTH);
		pageButton.setX(centerX - WIDGET_WIDTH - GAP_X / 2);
		pageButton.setY(this.rowY(bottomRow) + 8);
		Button done = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).build());
		done.setWidth(WIDGET_WIDTH);
		done.setX(centerX + GAP_X / 2);
		done.setY(this.rowY(bottomRow) + 8);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.text(this.font, this.title.getString(), (this.width - this.font.width(this.title)) / 2, 12, 0xFFFFFFFF, true);
		if (!this.status.getString().isEmpty()) {
			graphics.text(this.font, this.status.getString(), (this.width - this.font.width(this.status)) / 2, this.height - 12, 0xFFB7C2CE, true);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Keep the world visible (no blur) so adjustments can be judged live.
		if (this.minecraft != null && this.minecraft.level != null) {
			return;
		}
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		VulkirisConfig.get().save();
		VulkirisRenderer.clearFailure();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}

	private void cyclePreset(int direction) {
		VulkirisPresets.cycle(direction);
		VulkirisRenderer.clearFailure();
		this.status = Component.translatable("vulkiris.msg.preset", VulkirisPresets.displayName(VulkirisConfig.get().preset));
		this.rebuild();
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	private Component presetLabel() {
		return Component.translatable("vulkiris.preset.label", VulkirisPresets.displayName(VulkirisConfig.get().preset));
	}

	private static Component label(String key, Component value) {
		return Component.literal(Component.translatable(key).getString() + ": " + value.getString());
	}

	private static Component onOff(String key, boolean on) {
		return label(key, Component.translatable(on ? "vulkiris.on" : "vulkiris.off"));
	}

	private static String ssrKey(int steps) {
		return steps >= 48 ? "vulkiris.ssr.ultra" : steps >= 24 ? "vulkiris.ssr.high" : steps >= 12 ? "vulkiris.ssr.low" : "vulkiris.ssr.off";
	}

	private void markCustom() {
		VulkirisConfig.get().preset = VulkirisPresets.CUSTOM;
	}

	private void addButton(Component text, Runnable onPress) {
		this.place(this.addRenderableWidget(Button.builder(text, b -> onPress.run()).build()));
	}

	private void addToggle(Supplier<Component> text, Runnable onPress) {
		this.addToggle(text, onPress, true);
	}

	private void addToggle(Supplier<Component> text, Runnable onPress, boolean marksCustom) {
		Button button = this.addRenderableWidget(Button.builder(text.get(), b -> {
			onPress.run();
			if (marksCustom) {
				this.markCustom();
			}
			b.setMessage(text.get());
		}).build());
		this.place(button);
	}

	private void addCycle(Supplier<Component> text, Runnable onPress) {
		this.addToggle(text, onPress);
	}

	private void addSlider(String key, float min, float max, float current, Consumer<Float> setter) {
		double initial = (current - min) / (max - min);
		this.place(this.addRenderableWidget(new ConfigSlider(this, key, min, max, initial, setter)));
	}

	private void place(AbstractWidget widget) {
		int index = this.nextIndex++;
		int leftX = this.width / 2 - WIDGET_WIDTH - GAP_X / 2;
		int rightX = this.width / 2 + GAP_X / 2;
		widget.setWidth(WIDGET_WIDTH);
		widget.setX(index % 2 == 0 ? leftX : rightX);
		widget.setY(this.rowY(index));
	}

	private int rowY(int index) {
		return 28 + (index / 2) * (WIDGET_HEIGHT + GAP_Y);
	}

	private static final class ConfigSlider extends AbstractSliderButton {
		private final VulkirisSettingsScreen screen;
		private final String key;
		private final float min;
		private final float max;
		private final Consumer<Float> setter;

		ConfigSlider(VulkirisSettingsScreen screen, String key, float min, float max, double initialValue, Consumer<Float> setter) {
			super(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), initialValue);
			this.screen = screen;
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(String.format("%s: %.2f", Component.translatable(this.key).getString(), this.current())));
		}

		@Override
		protected void applyValue() {
			this.setter.accept(this.current());
			this.screen.markCustom();
		}

		private float current() {
			return this.min + (float) this.value * (this.max - this.min);
		}
	}
}
