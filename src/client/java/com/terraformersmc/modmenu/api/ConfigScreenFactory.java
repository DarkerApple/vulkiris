package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/**
 * Compile-time stub of ModMenu's config screen factory, declared locally so building Vulkiris
 * never needs to download ModMenu. This class is EXCLUDED from the built jar (see build.gradle);
 * at runtime the real interface from the installed ModMenu jar is used instead.
 */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
	S create(Screen parent);
}
