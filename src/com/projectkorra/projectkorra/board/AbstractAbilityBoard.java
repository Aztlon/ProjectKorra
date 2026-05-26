package com.projectkorra.projectkorra.board;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.logging.PkLang;

import net.md_5.bungee.api.ChatColor;

abstract class AbstractAbilityBoard implements AbilityBoard {

	private final BoardType type;
	protected final Player player;
	protected final BendingPlayer bendingPlayer;
	protected final Scoreboard scoreboard;
	protected final Objective objective;
	private final BoardLine[] hotbarLines = new BoardLine[9];

	protected int selectedSlot;
	protected String prefix;
	protected String emptySlot;
	protected ChatColor selectedColor;
	protected ChatColor altColor;

	protected AbstractAbilityBoard(final BoardType type, final BendingPlayer bendingPlayer, final String objectiveName, final String titlePath) {
		this.type = type;
		this.bendingPlayer = bendingPlayer;
		this.player = bendingPlayer.getPlayer();
		this.selectedSlot = this.player.getInventory().getHeldItemSlot() + 1;
		this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

		final String title = colorize(ConfigManager.languageConfig.get().getString(titlePath));
		this.objective = this.scoreboard.registerNewObjective(objectiveName, "dummy", title);
		this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);

		this.prefix = ChatColor.stripColor(ConfigManager.languageConfig.get().getString("Board.Prefix.Text"));
		this.emptySlot = colorize(ConfigManager.languageConfig.get().getString("Board.EmptySlot"));
		updateColors();
	}

	@Override
	public BoardType getType() {
		return this.type;
	}

	protected final void registerHotbarLine(final int slot, final BoardLine line) {
		this.hotbarLines[slot - 1] = line;
	}

	protected final BoardLine getHotbarLine(final int slot) {
		return this.hotbarLines[slot - 1];
	}

	protected final String colorize(final String input) {
		return PkLang.format(input);
	}

	protected final String renderAbilityText(final int slot, final String ability, final boolean cooldown) {
		final StringBuilder rendered = new StringBuilder();

		if (ability == null || ability.isEmpty()) {
			rendered.append(this.emptySlot.replace("{slot_number}", String.valueOf(slot)));
		} else {
			final CoreAbility coreAbility = CoreAbility.getAbility(ChatColor.stripColor(ability));
			if (coreAbility == null) {
				if (cooldown || this.bendingPlayer.isOnCooldown(ability)) {
					rendered.append(ChatColor.STRIKETHROUGH);
				}

				rendered.append(ability);
			} else {
				rendered.append(coreAbility.getMovePreviewWithoutCooldownTimer(this.player, cooldown));
			}
		}

		return rendered.toString();
	}

	protected final String getHotbarPrefix(final int slot) {
		return (slot == this.selectedSlot ? this.selectedColor : this.altColor) + this.prefix;
	}

	protected final String getMiscPadding() {
		final int spaces = Math.max(0, this.prefix.length() + 1);
		return " ".repeat(spaces);
	}

	protected ChatColor getElementColor() {
		if (this.bendingPlayer.getElements().size() > 1) {
			return Element.AVATAR.getColor();
		} else if (this.bendingPlayer.getElements().size() == 1) {
			return this.bendingPlayer.getElements().get(0).getColor();
		} else {
			return ChatColor.WHITE;
		}
	}

	protected ChatColor getColor(final String from, final ChatColor def) {
		if (from != null && from.equalsIgnoreCase("element")) {
			return getElementColor();
		}

		try {
			return ChatColor.of(from);
		} catch (final Exception e) {
			ProjectKorra.plugin.getLogger().warning("Couldn't parse board color from '" + from + "', using default!");
			return def;
		}
	}

	@Override
	public void hide() {
		this.player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}

	@Override
	public void show() {
		this.player.setScoreboard(this.scoreboard);
		updateAll();
	}

	@Override
	public boolean isVisible() {
		return this.player.getScoreboard().equals(this.scoreboard);
	}

	@Override
	public void setVisible(final boolean show) {
		if (show) {
			show();
		} else {
			hide();
		}
	}

	@Override
	public void destroy() {
		this.scoreboard.clearSlot(DisplaySlot.SIDEBAR);
		this.objective.unregister();
	}

	public void updateColors() {
		this.selectedColor = getColor(ConfigManager.languageConfig.get().getString("Board.Prefix.SelectedColor"), ChatColor.WHITE);
		this.altColor = getColor(ConfigManager.languageConfig.get().getString("Board.Prefix.NonSelectedColor"), ChatColor.DARK_GRAY);
	}

	@Override
	public void updateAll() {
		updateColors();
		for (int slot = 1; slot <= 9; slot++) {
			setSlot(slot, this.bendingPlayer.getAbilities().get(slot), false);
		}
	}

	@Override
	public void clearSlot(final int slot) {
		setSlot(slot, null, false);
	}

	@Override
	public void setSlot(final int slot, final String ability, final boolean cooldown) {
		if (slot < 1 || slot > 9) {
			return;
		}

		getHotbarLine(slot).update(getHotbarPrefix(slot), renderAbilityText(slot, ability, cooldown));
	}

	@Override
	public void setActiveSlot(final int newSlot) {
		if (newSlot < 1 || newSlot > 9) {
			return;
		}

		final int oldSlot = this.selectedSlot;
		this.selectedSlot = newSlot;
		setSlot(oldSlot, this.bendingPlayer.getAbilities().get(oldSlot), false);
		setSlot(newSlot, this.bendingPlayer.getAbilities().get(newSlot), false);
	}

	@Override
	public void setAbilityCooldown(final String name, final boolean cooldown) {
		this.bendingPlayer.getAbilities().entrySet().stream()
				.filter(entry -> name.equals(entry.getValue()))
				.forEach(entry -> setSlot(entry.getKey(), name, cooldown));
	}
}