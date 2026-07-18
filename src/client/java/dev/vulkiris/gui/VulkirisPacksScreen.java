package dev.vulkiris.gui;

import dev.vulkiris.config.VulkirisConfig;
import dev.vulkiris.pack.PackRepository;
import dev.vulkiris.pack.PackSettingsStore;
import dev.vulkiris.pack.ShaderPack;
import dev.vulkiris.pipeline.DefaultPipeline;
import dev.vulkiris.pipeline.PackPipeline;
import dev.vulkiris.pipeline.PipelineManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Iris-style shader pack selector: lists the built-in default plus every pack found in
 * {@code shaderpacks/} (folders or zips with a {@code vulkiris.pack.json}). Selection applies
 * immediately and persists to the config.
 */
public final class VulkirisPacksScreen extends Screen {
	private static final int WIDGET_WIDTH = 210;
	private static final int WIDGET_HEIGHT = 20;
	private static final int GAP_Y = 4;
	private static final int MAX_LISTED = 10;

	private final @Nullable Screen parent;
	private Component status = Component.empty();

	public VulkirisPacksScreen(@Nullable Screen parent) {
		super(Component.translatable("vulkiris.screen.packs_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		PackRepository.ensureDir();
		boolean wide = this.width >= 2 * WIDGET_WIDTH + 30;
		int x = wide ? this.width / 2 - WIDGET_WIDTH - 10 : (this.width - WIDGET_WIDTH) / 2;
		int y = 32;

		String active = VulkirisConfig.get().pipeline;
		y = this.addEntry(x, y, marked("default".equals(active), Component.translatable("vulkiris.pipeline.default")), () -> this.select("default"));

		List<String> packs = PackRepository.scan();
		int listed = 0;
		for (String packId : packs) {
			if (listed++ >= MAX_LISTED) {
				break;
			}
			boolean current = ("pack:" + packId).equals(active);
			y = this.addEntry(x, y, marked(current, Component.literal(packId)), () -> this.select("pack:" + packId));
		}

		int bottomY = y + 10;
		Button openFolder = this.addRenderableWidget(Button.builder(Component.translatable("vulkiris.button.open_folder"),
				b -> Util.getPlatform().openPath(PackRepository.dir())).build());
		openFolder.setWidth(WIDGET_WIDTH / 2 - 2);
		openFolder.setX(x);
		openFolder.setY(bottomY);
		Button refresh = this.addRenderableWidget(Button.builder(Component.translatable("vulkiris.button.refresh"),
				b -> this.rebuild()).build());
		refresh.setWidth(WIDGET_WIDTH / 2 - 2);
		refresh.setX(x + WIDGET_WIDTH / 2 + 2);
		refresh.setY(bottomY);
		Button done = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).build());
		done.setWidth(WIDGET_WIDTH);
		done.setX(x);
		done.setY(bottomY + WIDGET_HEIGHT + GAP_Y);

		if (wide) {
			this.buildActivePackControls(this.width / 2 + 10, 32);
		}
	}

	/** Sliders and pack presets for the ACTIVE pack, when it declares any settings. */
	private void buildActivePackControls(int x, int y) {
		String active = VulkirisConfig.get().pipeline;
		if (active == null || !active.startsWith("pack:")) {
			return;
		}
		String packId = active.substring("pack:".length());
		try (ShaderPack opened = PackRepository.open(packId)) {
			if (opened.settings().isEmpty() && opened.presets().isEmpty()) {
				return;
			}
			// Pack presets: small buttons in rows of two.
			int count = 0;
			for (var preset : opened.presets().entrySet()) {
				Button presetButton = this.addRenderableWidget(Button.builder(Component.literal(preset.getKey()), b -> {
					PackSettingsStore.applyPreset(packId, preset.getValue());
					this.rebuild();
				}).build());
				presetButton.setWidth(WIDGET_WIDTH / 2 - 2);
				presetButton.setX(count % 2 == 0 ? x : x + WIDGET_WIDTH / 2 + 2);
				presetButton.setY(y);
				count++;
				if (count % 2 == 0) {
					y += WIDGET_HEIGHT + GAP_Y;
				}
			}
			if (count % 2 == 1) {
				y += WIDGET_HEIGHT + GAP_Y;
			}
			// Setting sliders.
			for (ShaderPack.Setting setting : opened.settings()) {
				float current = PackSettingsStore.value(packId, setting);
				var slider = this.addRenderableWidget(new PackSettingSlider(packId, setting, current));
				slider.setWidth(WIDGET_WIDTH);
				slider.setX(x);
				slider.setY(y);
				y += WIDGET_HEIGHT + GAP_Y;
			}
		} catch (Exception e) {
			// Unreadable pack: the list already shows it; nothing to build here.
		}
	}

	private static final class PackSettingSlider extends AbstractSliderButton {
		private final String packId;
		private final ShaderPack.Setting setting;

		PackSettingSlider(String packId, ShaderPack.Setting setting, float current) {
			super(0, 0, WIDGET_WIDTH, WIDGET_HEIGHT, Component.empty(),
					(current - setting.min()) / (setting.max() - setting.min()));
			this.packId = packId;
			this.setting = setting;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(String.format("%s: %.2f", this.setting.name(), this.current())));
		}

		@Override
		protected void applyValue() {
			PackSettingsStore.set(this.packId, this.setting.id(), this.current());
		}

		private float current() {
			return this.setting.min() + (float) this.value * (this.setting.max() - this.setting.min());
		}
	}

	private int addEntry(int x, int y, Component text, Runnable onPress) {
		Button button = this.addRenderableWidget(Button.builder(text, b -> {
			onPress.run();
			this.rebuild();
		}).build());
		button.setWidth(WIDGET_WIDTH);
		button.setX(x);
		button.setY(y);
		return y + WIDGET_HEIGHT + GAP_Y;
	}

	private void select(String pipelineId) {
		VulkirisConfig config = VulkirisConfig.get();
		config.pipeline = pipelineId;
		config.save();
		PipelineManager.setActive(pipelineId.startsWith("pack:")
				? new PackPipeline(pipelineId.substring("pack:".length()))
				: new DefaultPipeline());
		PipelineManager.active().clearFailure();
		this.status = Component.translatable("vulkiris.msg.pack_applied",
				pipelineId.startsWith("pack:") ? pipelineId.substring("pack:".length()) : Component.translatable("vulkiris.pipeline.default"));
	}

	private void rebuild() {
		this.clearWidgets();
		this.init();
	}

	private static Component marked(boolean active, Component text) {
		return active ? Component.literal("➤ ").append(text) : text;
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
		if (this.minecraft != null) {
			this.minecraft.setScreenAndShow(this.parent);
		}
	}
}
