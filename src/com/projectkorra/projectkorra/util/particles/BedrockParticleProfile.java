package com.projectkorra.projectkorra.util.particles;

import java.util.Locale;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;

import com.projectkorra.projectkorra.configuration.ConfigManager;

public final class BedrockParticleProfile {

	private static final String PATH = "Properties.BedrockParticles.";

	private static boolean enabled = true;
	private static boolean debug = false;
	private static boolean packetEventsSafetyNet = true;
	private static int maxCount = 8;
	private static String blockReplacementMode = "COLORED_DUST";
	private static Particle airParticle = Particle.WHITE_SMOKE;
	private static Color airColor = Color.fromRGB(0xDAEAFF);
	private static Color earthColor = Color.fromRGB(0x8A6A45);
	private static Color sandColor = Color.fromRGB(0xD8C071);
	private static Color metalColor = Color.fromRGB(0xB8B8B8);
	private static Color waterColor = Color.fromRGB(0x7FD9FF);
	private static Color iceColor = Color.fromRGB(0xDFF9FF);
	private static Color plantColor = Color.fromRGB(0x4FA35C);

	private BedrockParticleProfile() {
	}

	public static void reload() {
		final FileConfiguration config = ConfigManager.getConfig();
		enabled = config.getBoolean(PATH + "Enabled", true);
		debug = config.getBoolean(PATH + "Debug", false);
		packetEventsSafetyNet = config.getBoolean(PATH + "UsePacketEventsSafetyNet", true);
		maxCount = Math.max(1, config.getInt(PATH + "MaxCount", 8));
		blockReplacementMode = config.getString(PATH + "BlockReplacementMode", "COLORED_DUST").toUpperCase(Locale.ROOT);
		airParticle = readParticle(config.getString(PATH + "Profiles.Air.Particle", "WHITE_SMOKE"), Particle.WHITE_SMOKE);
		airColor = readColor(config.getString(PATH + "Profiles.Air.Color", "daeaff"), Color.fromRGB(0xDAEAFF));
		earthColor = readColor(config.getString(PATH + "Profiles.Earth.Color", "8a6a45"), Color.fromRGB(0x8A6A45));
		sandColor = readColor(config.getString(PATH + "Profiles.Sand.Color", "d8c071"), Color.fromRGB(0xD8C071));
		metalColor = readColor(config.getString(PATH + "Profiles.Metal.Color", "b8b8b8"), Color.fromRGB(0xB8B8B8));
		waterColor = readColor(config.getString(PATH + "Profiles.Water.Color", "7fd9ff"), Color.fromRGB(0x7FD9FF));
		iceColor = readColor(config.getString(PATH + "Profiles.Ice.Color", "dff9ff"), Color.fromRGB(0xDFF9FF));
		plantColor = readColor(config.getString(PATH + "Profiles.Plant.Color", "4fa35c"), Color.fromRGB(0x4FA35C));
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean isDebug() {
		return debug;
	}

	public static boolean usePacketEventsSafetyNet() {
		return packetEventsSafetyNet;
	}

	public static int getMaxCount() {
		return maxCount;
	}

	public static String getBlockReplacementMode() {
		return blockReplacementMode;
	}

	public static Particle getAirParticle() {
		return airParticle;
	}

	public static Color getAirColor() {
		return airColor;
	}

	public static Color getEarthColor() {
		return earthColor;
	}

	public static Color getSandColor() {
		return sandColor;
	}

	public static Color getMetalColor() {
		return metalColor;
	}

	public static Color getWaterColor() {
		return waterColor;
	}

	public static Color getIceColor() {
		return iceColor;
	}

	public static Color getPlantColor() {
		return plantColor;
	}

	static int capAmount(final int amount) {
		return Math.max(1, Math.min(amount <= 0 ? 1 : amount, maxCount));
	}

	private static Particle readParticle(final String value, final Particle fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return Particle.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (final IllegalArgumentException ignored) {
			return fallback;
		}
	}

	private static Color readColor(String hex, final Color fallback) {
		if (hex == null) {
			return fallback;
		}
		hex = hex.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (hex.length() != 6) {
			return fallback;
		}
		try {
			return Color.fromRGB(Integer.parseInt(hex, 16));
		} catch (final NumberFormatException ignored) {
			return fallback;
		}
	}
}
