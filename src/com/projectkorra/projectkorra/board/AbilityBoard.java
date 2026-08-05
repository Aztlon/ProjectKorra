package com.projectkorra.projectkorra.board;

import net.md_5.bungee.api.ChatColor;

public interface AbilityBoard {

	BoardType getType();

	void show();

	void hide();

	boolean isVisible();

	void setVisible(boolean show);

	void destroy();

	void updateAll();

	void clearSlot(int slot);

	void setSlot(int slot, String ability, boolean cooldown);

	void setActiveSlot(int newSlot);

	void setAbilityCooldown(String name, boolean cooldown);

	default void updateMisc(String name, ChatColor color, boolean cooldown) {
	}
}