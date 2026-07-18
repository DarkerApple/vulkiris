package com.terraformersmc.modmenu.api;

/**
 * Compile-time stub of the one ModMenu API method Vulkiris implements, declared locally so
 * building Vulkiris never needs to download ModMenu. This class is EXCLUDED from the built jar
 * (see build.gradle); at runtime the real interface from the installed ModMenu jar is used, and
 * its remaining methods keep their real default implementations.
 */
public interface ModMenuApi {
	ConfigScreenFactory<?> getModConfigScreenFactory();
}
