package dev.vulkiris.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.vulkiris.gui.VulkirisSettingsScreen;

/** ModMenu integration: adds a config button that opens the Vulkiris settings screen. */
public final class VulkirisModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VulkirisSettingsScreen::new;
	}
}
