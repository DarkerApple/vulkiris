package dev.vulkiris.gui;

import dev.vulkiris.VulkirisClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-game guide: a paged text viewer for the bundled shader pack documentation (localized —
 * the language file names which doc to load), with a button to open the full web guide.
 */
public final class VulkirisDocsScreen extends Screen {
	private static final String WEB_GUIDE = "https://github.com/DarkerApple/vulkiris/blob/claude/minecraft-lightweight-shader-t26cb6/docs/PACK_FORMAT.md";
	private static final int TEXT_WIDTH = 280;

	private final @Nullable Screen parent;
	private final List<FormattedCharSequence> lines = new ArrayList<>();
	private int page;
	private int linesPerPage = 12;

	public VulkirisDocsScreen(@Nullable Screen parent) {
		super(Component.translatable("vulkiris.screen.docs_title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (this.lines.isEmpty()) {
			this.loadText();
		}
		this.linesPerPage = Math.max(4, (this.height - 64 - 30) / (this.font.lineHeight + 2));
		int centerX = this.width / 2;
		int bottomY = this.height - 26;

		Button prev = this.addRenderableWidget(Button.builder(Component.literal("<"), b -> this.turnPage(-1)).build());
		prev.setWidth(20);
		prev.setX(centerX - 130);
		prev.setY(bottomY);
		Button next = this.addRenderableWidget(Button.builder(Component.literal(">"), b -> this.turnPage(1)).build());
		next.setWidth(20);
		next.setX(centerX + 110);
		next.setY(bottomY);
		Button web = this.addRenderableWidget(Button.builder(Component.translatable("vulkiris.button.web_guide"),
				b -> Util.getPlatform().openUri(WEB_GUIDE)).build());
		web.setWidth(102);
		web.setX(centerX - 106);
		web.setY(bottomY);
		Button done = this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).build());
		done.setWidth(102);
		done.setX(centerX + 4);
		done.setY(bottomY);
	}

	private void loadText() {
		String fileName = Component.translatable("vulkiris.docs.file").getString();
		Identifier id = Identifier.fromNamespaceAndPath(VulkirisClient.MOD_ID, "docs/" + fileName);
		try {
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
			if (resource.isEmpty()) {
				this.lines.add(Component.literal("Missing " + id).getVisualOrderText());
				return;
			}
			String text;
			try (InputStream in = resource.get().open()) {
				text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}
			for (String paragraph : text.split("\n", -1)) {
				if (paragraph.isBlank()) {
					this.lines.add(FormattedCharSequence.EMPTY);
				} else {
					this.lines.addAll(this.font.split(FormattedText.of(paragraph), TEXT_WIDTH));
				}
			}
		} catch (Exception e) {
			VulkirisClient.LOGGER.warn("Could not load guide {}", id, e);
			this.lines.add(Component.literal("Could not load the guide; see docs/PACK_FORMAT.md").getVisualOrderText());
		}
	}

	private int pageCount() {
		return Math.max(1, (this.lines.size() + this.linesPerPage - 1) / this.linesPerPage);
	}

	private void turnPage(int direction) {
		this.page = Math.floorMod(this.page + direction, this.pageCount());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.text(this.font, this.title.getString(), (this.width - this.font.width(this.title)) / 2, 12, 0xFFFFFFFF, true);
		int textX = (this.width - TEXT_WIDTH) / 2;
		int y = 30;
		int start = this.page * this.linesPerPage;
		for (int i = start; i < Math.min(start + this.linesPerPage, this.lines.size()); i++) {
			graphics.text(this.font, this.lines.get(i), textX, y, 0xFFE8EDF4);
			y += this.font.lineHeight + 2;
		}
		String pageLabel = (this.page + 1) + " / " + this.pageCount();
		graphics.text(this.font, pageLabel, (this.width - this.font.width(pageLabel)) / 2, this.height - 40, 0xFF8A97A6, true);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
