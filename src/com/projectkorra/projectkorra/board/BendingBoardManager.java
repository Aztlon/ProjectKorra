package com.projectkorra.projectkorra.board;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.storage.DBConnection;

import com.projectkorra.projectkorra.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;

/**
 * Manages every individual {@link AbilityBoard}
 */
public final class BendingBoardManager {
	
	private BendingBoardManager() {}
	
	private static final Set<String> disabledWorlds = new HashSet<>();
	private static final Map<String, ChatColor> trackedCooldowns = new ConcurrentHashMap<>();
	private static final Set<UUID> disabledPlayers = Collections.synchronizedSet(new HashSet<>());
	private static final Map<Player, BoardHolder> scoreboardPlayers = new ConcurrentHashMap<>();
	private static AbilityBoardResolver boardResolver = new ConfiguredAbilityBoardResolver();

	private static boolean enabled;

	public static void setup() {
		loadDisabledPlayers();
		initialize();
		Bukkit.getOnlinePlayers().forEach(BendingBoardManager::getBoard);
	}

	public static void reload() {
		scoreboardPlayers.values().forEach(holder -> holder.board().destroy());
		scoreboardPlayers.clear();
		initialize();
	}

	private static void initialize() {
		enabled = ConfigManager.getConfig().getBoolean("Properties.BendingBoard");
		
		disabledWorlds.clear();
		disabledWorlds.addAll(ConfigManager.getConfig().getStringList("Properties.DisabledWorlds"));
		trackedCooldowns.clear();
		
		if (ConfigManager.languageConfig.get().contains("Board.Extras")) {
			ConfigurationSection section = ConfigManager.languageConfig.get().getConfigurationSection("Board.Extras");
			for (String key : section.getKeys(false)) {
				try {
					trackedCooldowns.put(key, ChatColor.of(section.getString(key)));
				} catch (Exception e) {
					ProjectKorra.plugin.getLogger().warning("Couldn't parse color from 'Board.Extras." + key + "', using white.");
					trackedCooldowns.put(key, ChatColor.WHITE);
				}
			}
		}
	}
	
	/**
	 * Check if the player has the BendingBoard toggled off (disabled)
	 * @param player {@link Player} to check toggled
	 * @return true if player has the BendingBoard toggled off
	 */
	public static boolean isDisabled(Player player) {
		return disabledPlayers.contains(player.getUniqueId());
	}

	public static void changeWorld(Player player) {
		getBoard(player).ifPresent((b) -> b.setVisible(!disabledWorlds.contains(player.getWorld().getName())));
	}

	public static void setBoardResolver(final AbilityBoardResolver resolver) {
		boardResolver = resolver == null ? new ConfiguredAbilityBoardResolver() : resolver;
	}

	public static boolean shouldUseRpgBoard(final Player player) {
		return boardResolver != null && boardResolver.shouldUseRpgBoard(player);
	}

	public static List<String> resolveObjectiveLines(final Player player, final int maxLines) {
		return boardResolver == null ? Collections.emptyList() : boardResolver.resolveObjectiveLines(player, maxLines);
	}

	/**
	 * Toggles the bendingboard for the given player if the board is enabled and they are in a bending enabled world
	 * @param player Player with the bendingboard
	 * @param force True to ignore the enabled conditions and force a toggle update
	 */
	public static void toggleBoard(Player player, boolean force) {
		if (!force && (!enabled || disabledWorlds.contains(player.getWorld().getName()))) {
			ChatUtil.sendBrandingMessage(player, ChatColor.RED + ConfigManager.languageConfig.get().getString("Commands.Board.Disabled"));
			return;
		}

		if (scoreboardPlayers.containsKey(player)) {
			scoreboardPlayers.get(player).board().hide();
			scoreboardPlayers.get(player).board().destroy();
			disabledPlayers.add(player.getUniqueId());
			scoreboardPlayers.remove(player);
			ChatUtil.sendBrandingMessage(player, ChatColor.RED + ConfigManager.languageConfig.get().getString("Commands.Board.ToggledOff"));
		} else {
			disabledPlayers.remove(player.getUniqueId());
			getBoard(player).ifPresent(AbilityBoard::show);
			ChatUtil.sendBrandingMessage(player, ChatColor.GREEN + ConfigManager.languageConfig.get().getString("Commands.Board.ToggledOn"));
		}
	}

	/**
	 * Gets the player's bendingboard if not disabled for some reason, and if it does not exist but
	 * should one will be created and stored for them
	 * @param player the player to get the bendingboard of
	 * @return empty if the board is disabled
	 */
	public static Optional<AbilityBoard> getBoard(Player player) {
		if (!enabled || disabledPlayers.contains(player.getUniqueId()) || !player.hasPermission("bending.command.board")) {
			return Optional.empty();
		}

		if (!player.isOnline()) {
			return Optional.empty();
		}

		final BoardHolder holder = ensureBoard(player);
		return holder == null ? Optional.empty() : Optional.of(holder.board());
	}

	/**
	 * Sets the bendingboard to match the player's current slots, update the active slot, and match cooldowns
	 * @param player Player with the bendingboard, silently ignored if board is disabled
	 */
	public static void updateAllSlots(Player player) {
		getBoard(player).ifPresent(AbilityBoard::updateAll);
	}

	/**
	 * Update the player's bendingboard based on the given information.
	 * <ul>
	 * <li>If the player has a multiability bound, all slots will be updated
	 * <li>If the name is null or empty, the given slot is cleared
	 * <li>Combos or names without an ability are updated on the extras portion
	 * <li>The specific slot is updated if is in bounds [1, 9]
	 * <li>The given ability name is set to match forceCooldown
	 * </ul>
	 * <br>
	 * @param player Player with the bendingboard, silently ignored if board is disabled
	 * @param name Name of the ability being affected
	 * @param forceCooldown Force if the name is strikethroughed on the board
	 * @param slot Slot being affected, use 0 if more than one slot is involved
	 */
	public static void updateBoard(Player player, String name, boolean forceCooldown, int slot) {
		getBoard(player).ifPresent((board) -> {
			if (MultiAbilityManager.hasMultiAbilityBound(player)) {
				board.updateAll();
			}
			
			if (name == null || name.isEmpty()) {
				board.clearSlot(slot);
				return;
			}
			
			CoreAbility coreAbility = CoreAbility.getAbility(name);
			if (coreAbility instanceof ComboAbility) {
				board.updateMisc(name, coreAbility.getElement().getColor(), forceCooldown);
			} else if (coreAbility == null && trackedCooldowns.containsKey(name)) {
				board.updateMisc(name, trackedCooldowns.get(name), forceCooldown);
			} else if (coreAbility != null && slot > 0) {
				board.setSlot(slot, name, forceCooldown);
			} else {
				board.setAbilityCooldown(name, forceCooldown);
			}
		});
	}

	/**
	 * Sets the active slot on the player's bendingboard
	 * @param player Player with the bendingboad, silently ignored if board is disabled
	 * @param newSlot New slot to be set as the active one
	 */
	public static void changeActiveSlot(Player player, int newSlot) {
		getBoard(player).ifPresent((board) -> board.setActiveSlot(newSlot));
	}

	private static BoardHolder ensureBoard(final Player player) {
		final BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(player);
		if (bendingPlayer == null) {
			return null;
		}

		final BoardType desiredType = resolveBoardType(player);
		BoardHolder holder = scoreboardPlayers.get(player);
		final boolean wasVisible = holder != null && holder.board().isVisible();

		if (holder == null || holder.type() != desiredType) {
			if (holder != null) {
				holder.board().destroy();
			}

			final AbilityBoard board = createBoard(bendingPlayer, desiredType);
			board.updateAll();
			if (wasVisible) {
				board.show();
			}

			holder = new BoardHolder(desiredType, board);
			scoreboardPlayers.put(player, holder);
		}

		return holder;
	}

	private static AbilityBoard createBoard(final BendingPlayer bendingPlayer, final BoardType type) {
		if (type == BoardType.RPG) {
			return new RpgAbilityBoard(bendingPlayer);
		}

		return new PkAbilityBoard(bendingPlayer);
	}

	private static BoardType resolveBoardType(final Player player) {
		if (shouldUseRpgBoard(player) && hasObjectiveLines(player)) {
			return BoardType.RPG;
		}

		return BoardType.PK;
	}

	private static boolean hasObjectiveLines(final Player player) {
		for (final String line : resolveObjectiveLines(player, 4)) {
			if (line != null && !ChatColor.stripColor(line).trim().isEmpty()) {
				return true;
			}
		}

		return false;
	}
	

	/**
	 * Some abilities use internal cooldowns with custom names that don't correspond to bound abilities' names.
	 * Adds the internal cooldown name and color to the map of tracked abilities so as they can appear on the bending board.
	 * @param cooldownName the internal cooldown name
	 * @param color the color to use when rendering the board entry
	 */
	public static void addCooldownToTrack(String cooldownName, ChatColor color) {
		trackedCooldowns.put(cooldownName, color);
	}

	/**
	 * Load into memory the list of players who have toggled the bending board off.
	 */
	public static void loadDisabledPlayers() {
		Bukkit.getScheduler().runTaskAsynchronously(ProjectKorra.plugin, () -> {
			Set<UUID> disabled = new HashSet<>();
			try {
				final ResultSet rs = DBConnection.sql.readQuery("SELECT uuid FROM pk_board WHERE enabled = 0");
				while (rs.next()) disabled.add(UUID.fromString(rs.getString("uuid")));
			} catch (SQLException e) {
				e.printStackTrace();
			}
			disabledPlayers.clear();
			disabledPlayers.addAll(disabled);
		});
	}

	/**
	 * Called on player logout
	 * Removes the board instance and stores the player's toggle preference for the bending board in the database
	 * @param player
	 */
	public static void clean(final Player player) {
		final BoardHolder holder = scoreboardPlayers.remove(player);
		if (holder != null) {
			holder.board().destroy();
		}
		final UUID uuid = player.getUniqueId();
		final String updateQuery = "UPDATE pk_board SET enabled = " + (disabledPlayers.contains(uuid) ? 0 : 1) + " WHERE uuid = ?";
		Bukkit.getScheduler().runTaskAsynchronously(ProjectKorra.plugin, () -> {
			try {
				PreparedStatement ps = DBConnection.sql.getConnection().prepareStatement("SELECT enabled FROM pk_board WHERE uuid = ? LIMIT 1");
				ps.setString(1, uuid.toString());
				PreparedStatement ps2;
				if (!ps.executeQuery().next()) { // if the entry doesn't exist in the DB, create it.
					ps2 = DBConnection.sql.getConnection().prepareStatement("INSERT INTO pk_board (uuid, enabled) VALUES (?, 1)");
				} else { // if the entry exists in the DB, update it
					ps2 = DBConnection.sql.getConnection().prepareStatement(updateQuery);
				}
				ps2.setString(1, uuid.toString());
				ps2.execute();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		});
	}
}
