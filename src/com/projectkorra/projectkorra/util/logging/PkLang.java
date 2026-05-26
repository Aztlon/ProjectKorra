package com.projectkorra.projectkorra.util.logging;

import java.awt.*;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.projectkorra.projectkorra.ProjectKorra;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;

public final class PkLang {
	public static final String PREFIX = "&8[&5ProjectKorra&8]&r";
	public static final String PREFIX_WARNING = "&8[&6ProjectKorra&8]&r";
	public static final String PREFIX_SEVERE = "&8[&cProjectKorra&8]&r";

	private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

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

	/**
	 * Converts a hexcode to an RGB array
	 * @param hexcode - the hexcode to be converted
	 */
	public static int[] hexToRGB(String hexcode) {
		if (hexcode.length() != 6) return new int[] { 0, 0, 0 };
		int red = Integer.parseInt(hexcode.substring(0, 2), 16);
		int green = Integer.parseInt(hexcode.substring(2, 4), 16);
		int blue = Integer.parseInt(hexcode.substring(4, 6), 16);
		return new int[] { red, green, blue };
	}

	/**
	 * Convert a hexcode to a {@link ChatColor}
	 * @param hexcode - the hexcode to be converted
	 */
	public static ChatColor hexToChatColor(String hexcode) {
		Color color = hexToColor(hexcode);
		return ChatColor.of(color);
	}

	/**
	 * Converts a hexcode to a {@link Color}
	 * @param hexcode - the hexcode to be converted
	 */
	public static Color hexToColor(String hexcode) {
		int[] rgb = hexToRGB(hexcode);
		return new Color(rgb[0], rgb[1], rgb[2], 0);
	}

	public static String format(String message) {
		if (message == null)
			return "";

		try {
			// LEGACY AND HEX REPLACEMENTS
			if (message.contains("&#")) {
				message = replaceHexCodes(message);
			}

			// Apply standard color code translation
			if (message.contains("&")) {
				message = ChatColor.translateAlternateColorCodes('&', message);
			}

			return message;
		} catch (Exception e) {
			return message;
		}
	}

	private static String replaceHexCodes(String message) {
		Matcher matcher = HEX_PATTERN.matcher(message);
		StringBuilder sb = new StringBuilder();

		while (matcher.find()) {
			String hex = matcher.group(1);
			ChatColor chatColor = hexToChatColor(hex);

			if (chatColor == null) {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
			} else {
				matcher.appendReplacement(sb, Matcher.quoteReplacement(chatColor.toString()));
			}
		}

		matcher.appendTail(sb);
		return sb.toString();
	}

	public static String colorize(String message) {
		return ChatColor.translateAlternateColorCodes('&', message);
	}
}
