package dev.vulkiris.render;

import dev.vulkiris.VulkirisClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Chat-triggered Easter-egg looks. Typing a secret word in chat (exact match, any case)
 * swallows the message and cross-fades the whole screen into a themed grade; typing the
 * same word again fades back to normal. Session-only — never saved to the config.
 */
public final class VulkirisEggs {
	public static final int NONE = 0;
	public static final int SANNABI = 1;
	public static final int MATRIX = 2;
	public static final int HEROBRINE = 3;

	private static final Map<String, Integer> WORDS = Map.of(
			"sannabi", SANNABI,
			"산나비", SANNABI,
			"matrix", MATRIX,
			"herobrine", HEROBRINE);

	private static int mode = NONE;
	private static int fadingTo = NONE;
	private static float strength;

	private VulkirisEggs() {
	}

	/** Returns true when the chat message was a secret word and should not be sent. */
	public static boolean handleChatMessage(Minecraft client, String message) {
		Integer triggered = WORDS.get(message.trim().toLowerCase(Locale.ROOT));
		if (triggered == null) {
			return false;
		}
		if (fadingTo == triggered) {
			fadingTo = NONE;
			VulkirisClient.feedback(client, Component.translatable("vulkiris.msg.egg.off"));
		} else {
			fadingTo = triggered;
			mode = triggered;
			VulkirisClient.feedback(client, Component.translatable("vulkiris.msg.egg." + triggered));
		}
		return true;
	}

	/** Advances the cross-fade; called once per frame from the uniform preparation. */
	public static void tick() {
		float target = fadingTo == NONE ? 0.0f : 1.0f;
		strength += (target - strength) * 0.06f;
		if (strength < 0.002f) {
			strength = 0.0f;
			if (fadingTo == NONE) {
				mode = NONE;
			}
		}
	}

	public static int mode() {
		return mode;
	}

	public static float strength() {
		return Math.min(strength, 1.0f);
	}

	public static void reset() {
		mode = NONE;
		fadingTo = NONE;
		strength = 0.0f;
	}
}
