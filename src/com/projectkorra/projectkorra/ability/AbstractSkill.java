package com.projectkorra.projectkorra.ability;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.Element;

import net.md_5.bungee.api.ChatColor;

public abstract class AbstractSkill {

	public static final Map<String, AbstractSkill> CACHE = new HashMap<>();

	protected final String name;

	public AbstractSkill(String name) {
		this.name = name;
		CACHE.put(name, this);
	}

	public static @Nullable AbstractSkill byName(String name) {
		return CACHE.get(name);
	}

	public static boolean isLocked(String name, OfflinePlayer player) {
		AbstractSkill skill = byName(name);
		return skill != null && (skill.isEnabled() && skill.isLocked(player));
	}

	public abstract boolean isEnabled();
	public abstract boolean isLocked(OfflinePlayer player);

	public String name() {
		return name;
	}

	public String displayName(Element element) {
		return element.getColor().toString() + ChatColor.BOLD + name;
	}
}
