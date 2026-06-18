package com.projectkorra.projectkorra.util.particles;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

public final class BedrockPlayerDetector {

	private static boolean floodgateAvailable;

	private BedrockPlayerDetector() {
	}

	public static void reload() {
		floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate")
				|| Bukkit.getPluginManager().isPluginEnabled("Floodgate");
	}

	public static boolean isBedrockPlayer(final Player player) {
		if (!floodgateAvailable || player == null) {
			return false;
		}

		try {
			return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
		} catch (final LinkageError | RuntimeException ignored) {
			return false;
		}
	}
}
