package com.projectkorra.projectkorra.ability;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.OfflinePlayer;

import com.projectkorra.projectkorra.Element;

public abstract class AbstractSkill {

	public static final Map<String, AbstractSkill> CACHE = new HashMap<>();

	protected final String name;

	public AbstractSkill(String name) {
		this.name = name;
		CACHE.put(name, this);
	}

	public static AbstractSkill byName(String name) {
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
		return element.getColor() + name;
	}
}
