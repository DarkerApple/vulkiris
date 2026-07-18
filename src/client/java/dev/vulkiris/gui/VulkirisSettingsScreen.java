package dev.vulkiris.gui;

import dev.vulkiris.config.VulkirisConfig;
import dev.vulkiris.config.VulkirisPresets;
import dev.vulkiris.pipeline.PipelineManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Vulkiris settings with sidebar navigation: category buttons on the left (Presets, Look,
 * Effects, Style, Viewmodel, plus Shader Packs and Guide screens), content widgets on the
 * right. Every widget writes straight into {@link VulkirisConfig}; the file is saved on
 * close, and the world stays un-blurred behind the screen so changes are judged live.
 */
public final class VulkirisSettingsScreen extends Screen {
	private static final int SIDEBAR_X = 8;
	private static final int SIDEBAR_WIDTH = 92;
	private static final int WIDGET_WIDTH = 150;
	private static final int WIDGET_HEIGHT = 20;
	private static final int ROW_HEIGHT = WIDGET_HEIGHT + 4;
	private static final int TOP_Y = 28;

	private static final int TAB_PRESETS = 0;
	private static final int TAB_LOOK = 1;
	private static final int TAB_EFFECTS = 2;
	private static final int TAB_STYLE = 3;
	private static final int TAB_VIEWMODEL = 4;
	private static final String[] TAB_KEYS = {
			"vulkiris.tab.presets", "vulkiris.tab.look", "vulkiris.tab.effects", "vulkiris.tab.style", "vulkiris.tab.viewmodel"
	};

	private final @Nullable Screen parent;
	private int tab = TAB_PRESETS;
	private int nextIndex;
	private int columns = 2;
	private Component status = Component.empty();

	public VulkirisSettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("vulkiris.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		VulkirisConfig config = VulkirisConfig.get();
		this.nextIndex = 0;
		int contentSpace = this.width - (SIDEBAR_X + SIDEBAR_WIDTH + 8) - 8;
		this.columns = contentSpace >= 2 * WIDGET_WIDTH + 8 ? 2 : 1;

		// --- Sidebar. ---
		int sideY = TOP_Y;
		for (int i = 0; i < TAB_KEYS.length; i++) {
			int tabIndex = i;
			Button tabButton = this.addRenderableWidget(Button.builder(Component.translatable(TAB_KEYS[i]), b -> {
				this.tab = tabIndex;
				this.rebuild();
			}).build());
			tabButton.setWidth(SIDEBAR_WIDTH);
			tabButton.setX(SIDEBAR_X);
			tabButton.setY(sideY);
			tabButton.active = this.tab != i;
			sideY += ROW_HEIGHT;
		}
		sideY += 8;
		Button packs = this.addRenderableWidget(Button.builder(Component.translatable("vulkiris.button.packs"),
				b -> this.minecraft.setScreenAndShow(new VulkirisPacksScreen(this))).build());
		packs.setWidth(SIDEBAR_WIDTH);
		packs.setX(SIDEBAR_X);
		packs.setY(sideY);
		sideY += ROW_HEIGHT;
		Button guide = this.addRenderableWidget(Button.builder(Component.translatable("vulkiris.button.guide"),
				b -> this.minecraft.setScreenAndShow(new VulkirisDocsScreen(this))).build());
		guide.setWidth(SIDEBAR_WIDTH);
		guide.setX(SIDEBAR_X);
		guide.setY(sideY);
		Button done = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).build());
		done.setWidth(SIDEBAR_WIDTH);
		done.setX(SIDEBAR_X);
		done.setY(this.height - 26);

		// --- Content. ---
		switch (this.tab) {
			case TAB_PRESETS -> this.buildPresets(config);
			case TAB_LOOK -> this.buildLook(config);
			case TAB_EFFECTS -> this.buildEffects(config);
			case TAB_STYLE -> this.buildStyle(config);
			default -> this.buildViewmodel(config);
		}
	}

	private void buildPresets(VulkirisConfig config) {
		List<String> ids = VulkirisPresets.ids();
		for (String id : ids) {
			boolean active = id.equals(config.preset);
			Component name = VulkirisPresets.displayName(id);
			this.place(this.addRenderableWidget(Button.builder(active ? Component.literal("➤ ").append(name) : name, b -> {
				VulkirisPresets.apply(id);
				PipelineManager.active().clearFailure();
				this.status = Component.translatable("vulkiris.msg.preset", VulkirisPresets.displayName(id));
				this.rebuild();
			}).build()));
		}
		if (this.nextIndex % 2 == 1) {
			this.nextIndex++;
		}
		this.place(this.addRenderableWidget(Button.builder(Component.translatable("vulkiris.button.export"), b -> {
			VulkirisPresets.exportToClipboard(this.minecraft);
			this.status = Component.translatable("vulkiris.msg.exported");
		}).build()));
		this.place(this.addRenderableWidget(Button.builder(Component.translatable("vulkiris.button.import"), b -> {
			String id = VulkirisPresets.importFromClipboard(this.minecraft);
			this.status = id != null
					? Component.translatable("vulkiris.msg.imported", VulkirisPresets.displayName(id))
					: Component.translatable("vulkiris.msg.import_failed");
			PipelineManager.active().clearFailure();
			this.rebuild();
		}).build()));
	}

	private void buildLook(VulkirisConfig config) {
		this.addToggle(() -> onOff("vulkiris.option.effects", config.enabled), () -> {
			config.enabled = !config.enabled;
			PipelineManager.active().clearFailure();
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
	}

	private void buildEffects(VulkirisConfig config) {
		this.addSlider("vulkiris.option.sunlight", 0.0f, 1.5f, config.sunlight, v -> config.sunlight = v);
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
	}

	private void buildStyle(VulkirisConfig config) {
		this.addCycle(() -> label("vulkiris.option.ssr", Component.translatable(ssrKey(config.ssrSteps))), config::cycleSsr);
		this.addSlider("vulkiris.option.sun_specular", 0.0f, 1.0f, config.sunSpecular, v -> config.sunSpecular = v);
		this.addToggle(() -> onOff("vulkiris.option.selective_bloom", config.selectiveBloom), () -> config.selectiveBloom = !config.selectiveBloom);
		this.addSlider("vulkiris.option.rim_light", 0.0f, 1.0f, config.rimLight, v -> config.rimLight = v);
		this.addToggle(() -> onOff("vulkiris.option.toon", config.toon), () -> config.toon = !config.toon);
	}

	private void buildViewmodel(VulkirisConfig config) {
		this.addPlainSlider("vulkiris.option.vm_scale", 0.5f, 1.5f, config.vmScale, v -> config.vmScale = v);
		this.addPlainSlider("vulkiris.option.vm_offset_x", -0.5f, 0.5f, config.vmOffsetX, v -> config.vmOffsetX = v);
		this.addPlainSlider("vulkiris.option.vm_offset_y", -0.5f, 0.5f, config.vmOffsetY, v -> config.vmOffsetY = v);
		this.addPlainSlider("vulkiris.option.vm_rotation", -60.0f, 60.0f, config.vmRotation, v -> config.vmRotation = v);
		this.addPlainSlider("vulkiris.option.vm_swing", 0.0f, 1.0f, config.vmSwing, v -> config.vmSwing = v);
		this.place(this.addRenderableWidget(Button.builder(Component.translatable("vulkiris.button.vm_reset"), b -> {
			config.vmScale = 1.0f;
			config.vmOffsetX = 0.0f;
			config.vmOffsetY = 0.0f;
			config.vmRotation = 0.0f;
			config.vmSwing = 0.0f;
			this.rebuild();
		}).build()));
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
		PipelineManager.active().clearFailure();
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
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
		this.place(this.addRenderableWidget(new ConfigSlider(this, key, min, max, initial, setter, true)));
	}

	/** A slider that does not mark the preset as custom (viewmodel settings). */
	private void addPlainSlider(String key, float min, float max, float current, Consumer<Float> setter) {
		double initial = (current - min) / (max - min);
		this.place(this.addRenderableWidget(new ConfigSlider(this, key, min, max, initial, setter, false)));
	}

	private void place(AbstractWidget widget) {
		int index = this.nextIndex++;
		int contentLeft = SIDEBAR_X + SIDEBAR_WIDTH + 8;
		int contentSpace = this.width - contentLeft - 8;
		widget.setWidth(WIDGET_WIDTH);
		if (this.columns == 2) {
			int groupLeft = contentLeft + (contentSpace - (2 * WIDGET_WIDTH + 8)) / 2;
			widget.setX(index % 2 == 0 ? groupLeft : groupLeft + WIDGET_WIDTH + 8);
			widget.setY(TOP_Y + (index / 2) * ROW_HEIGHT);
		} else {
			widget.setX(contentLeft + (contentSpace - WIDGET_WIDTH) / 2);
			widget.setY(TOP_Y + index * ROW_HEIGHT);
		}
	}

	private static final class ConfigSlider extends AbstractSliderButton {
		private final VulkirisSettingsScreen screen;
		private final String key;
		private final float min;
		private final float max;
		private final Consumer<Float> setter;
		private final boolean marksCustom;

		ConfigSlider(VulkirisSettingsScreen screen, String key, float min, float max, double initialValue, Consumer<Float> setter, boolean marksCustom) {
			super(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(), initialValue);
			this.screen = screen;
			this.key = key;
			this.min = min;
			this.max = max;
			this.setter = setter;
			this.marksCustom = marksCustom;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(String.format("%s: %.2f", Component.translatable(this.key).getString(), this.current())));
		}

		@Override
		protected void applyValue() {
			this.setter.accept(this.current());
			if (this.marksCustom) {
				this.screen.markCustom();
			}
		}

		private float current() {
			return this.min + (float) this.value * (this.max - this.min);
		}
	}
}
