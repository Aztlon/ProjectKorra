package com.projectkorra.projectkorra.board;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.configuration.ConfigManager;

import net.md_5.bungee.api.ChatColor;

public class ConfiguredAbilityBoardResolver implements AbilityBoardResolver {

	@Override
	public boolean shouldUseRpgBoard(final Player player) {
		return ConfigManager.languageConfig.get().getBoolean("Board.RPG.Enabled");
	}

	@Override
	public List<String> resolveObjectiveLines(final Player player, final int maxLines) {
		final List<String> templates = ConfigManager.languageConfig.get().getStringList("Board.RPG.Objectives.Lines");
		final List<String> resolved = new ArrayList<>();
		final int limit = Math.max(0, maxLines);

		for (final String template : templates) {
			if (resolved.size() >= limit) {
				break;
			}

			final String rendered = renderTemplate(player, template, resolved.size() + 1);
			if (!ChatColor.stripColor(rendered).trim().isEmpty()) {
				resolved.add(rendered);
			}
		}

		return resolved;
	}

	private String renderTemplate(final Player player, final String template, final int lineNumber) {
		String rendered = template == null ? "" : template;
		rendered = rendered.replace("{player_name}", player.getName());
		rendered = rendered.replace("{player_display_name}", player.getDisplayName());
		rendered = rendered.replace("{player_uuid}", player.getUniqueId().toString());
		rendered = rendered.replace("{world}", player.getWorld().getName());
		rendered = rendered.replace("{line_number}", String.valueOf(lineNumber));
		rendered = ChatColor.translateAlternateColorCodes('&', rendered);

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			rendered = applyPlaceholderApi(player, rendered);
		}

		return rendered;
	}

	private String applyPlaceholderApi(final Player player, final String rendered) {
		try {
			final Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
			return (String) placeholderApi.getMethod("setPlaceholders", Player.class, String.class).invoke(null, player, rendered);
		} catch (final ReflectiveOperationException | LinkageError e) {
			return rendered;
		}
	}
}