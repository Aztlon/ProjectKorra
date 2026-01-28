package com.projectkorra.projectkorra.util.logging;

import com.projectkorra.projectkorra.ProjectKorra;

import net.md_5.bungee.api.ChatColor;

public final class PkLang {
	public static final String PREFIX = "&8[&5ProjectKorra&8]&r";
	public static final String PREFIX_WARNING = "&8[&6ProjectKorra&8]&r";
	public static final String PREFIX_SEVERE = "&8[&cProjectKorra&8]&r";

	private static PkLang instance;
	private final ProjectKorra plugin;

	public PkLang(ProjectKorra plugin) {
		this.plugin = plugin;
		instance = this;
	}

	public static void info(String message) {
		instance.plugin.getServer().getConsoleSender().sendMessage(colorize(PREFIX + " " + message));
	}

	public static void warning(String message) {
		instance.plugin.getServer().getConsoleSender().sendMessage(colorize(PREFIX_WARNING + " " + message));
	}

	public static void severe(String message) {
		instance.plugin.getServer().getConsoleSender().sendMessage(colorize(PREFIX_SEVERE + " " + message));
	}

	public static String colorize(String message) {
		return ChatColor.translateAlternateColorCodes('&', message);
	}
}
