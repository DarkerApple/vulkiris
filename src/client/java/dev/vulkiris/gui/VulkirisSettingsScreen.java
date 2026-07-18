package dev.vulkiris.gui;

import dev.vulkiris.config.VulkirisConfig;
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
 * Two-column settings screen. Every widget writes straight into {@link VulkirisConfig}, so
 * changes are visible immediately behind the (blurred) screen; the file is saved on close.
 */
public final class VulkirisSettingsScreen extends Screen {
	private static final int WIDGET_WIDTH = 150;
	private static final int WIDGET_HEIGHT = 20;
	private static final int GAP_X = 8;
	private static final int GAP_Y = 4;

	private final @Nullable Screen parent;
	private int nextIndex;

	public VulkirisSettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Vulkiris Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		VulkirisConfig config = VulkirisConfig.get();
		this.nextIndex = 0;

		this.addToggle(() -> "Effects: " + (config.enabled ? "ON" : "OFF"), () -> config.enabled = !config.enabled);
		this.addToggle(() -> "Tonemap: " + config.tonemap.toUpperCase(), config::cycleTonemap);
		this.addSlider("Brightness", 0.25f, 2.0f, config.exposure, value -> config.exposure = value);
		this.addSlider("Tonemap Strength", 0.0f, 1.0f, config.tonemapStrength, value -> config.tonemapStrength = value);
		this.addSlider("Warmth", 0.0f, 0.25f, config.warmth, value -> config.warmth = value);
		this.addSlider("Saturation", 0.5f, 1.5f, config.saturation, value -> config.saturation = value);
		this.addSlider("Contrast", 0.8f, 1.2f, config.contrast, value -> config.contrast = value);
		this.addToggle(() -> "Bloom: " + (config.bloom ? "ON" : "OFF"), () -> config.bloom = !config.bloom);
		this.addSlider("Bloom Intensity", 0.0f, 1.5f, config.bloomIntensity, value -> config.bloomIntensity = value);
		this.addToggle(() -> "Fog: " + (config.fog ? "ON" : "OFF"), () -> config.fog = !config.fog);
		this.addSlider("Fog Density", 0.0f, 1.0f, config.fogDensity, value -> config.fogDensity = value);
		this.addSlider("Sun Scatter", 0.0f, 2.0f, config.sunScatter, value -> config.sunScatter = value);
		this.addSlider("Sky & Sunsets", 0.0f, 1.5f, config.skyIntensity, value -> config.skyIntensity = value);
		this.addSlider("Ambient Occlusion", 0.0f, 1.0f, config.aoStrength, value -> config.aoStrength = value);
		this.addToggle(() -> "Water Shading: " + (config.water ? "ON" : "OFF"), () -> config.water = !config.water);
		this.addSlider("Vignette", 0.0f, 0.6f, config.vignette, value -> config.vignette = value);
		this.addToggle(() -> "FXAA: " + (config.fxaa ? "ON" : "OFF"), () -> config.fxaa = !config.fxaa);

		Button done = this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose()).build());
		done.setWidth(WIDGET_WIDTH);
		done.setX((this.width - WIDGET_WIDTH) / 2);
		done.setY(this.rowY(this.nextIndex + (this.nextIndex % 2)) + 8);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.text(this.font, this.title.getString(), (this.width - this.font.width(this.title)) / 2, 14, 0xFFFFFFFF, true);
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

	private void addToggle(Supplier<String> label, Runnable onPress) {
		Button button = this.addRenderableWidget(Button.builder(Component.literal(label.get()), b -> {
			onPress.run();
			b.setMessage(Component.literal(label.get()));
		}).build());
		this.place(button);
	}

	private void addSlider(String name, float min, float max, float current, Consumer<Float> setter) {
		double initial = (current - min) / (max - min);
		this.place(this.addRenderableWidget(new ConfigSlider(name, min, max, initial, setter)));
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
		return 32 + (index / 2) * (WIDGET_HEIGHT + GAP_Y);
	}

	private static final class ConfigSlider extends AbstractSliderButton {
		private final String name;
		private final float min;
		private final float max;
		private final Consumer<Float> setter;

		ConfigSlider(String name, float min, float max, double initialValue, Consumer<Float> setter) {
			super(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), initialValue);
			this.name = name;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(String.format("%s: %.2f", this.name, this.current())));
		}

		@Override
		protected void applyValue() {
			this.setter.accept(this.current());
		}

		private float current() {
			return this.min + (float) this.value * (this.max - this.min);
		}
	}
}
