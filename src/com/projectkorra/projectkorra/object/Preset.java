package com.projectkorra.projectkorra.object;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ExternalPersistenceCoordinator;
import com.projectkorra.projectkorra.OfflineBendingPlayer;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.board.BendingBoardManager;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.event.PlayerBindChangeEvent;
import com.projectkorra.projectkorra.storage.internal.InternalPlayerDataStore;
import com.projectkorra.projectkorra.persistence.external.BendingPlayerMutationOperation;
import com.projectkorra.projectkorra.persistence.external.MutationSource;
import com.projectkorra.projectkorra.util.logging.PkLang;

/**
 * A savable association of abilities and hotbar slots, stored per player.
 *
 * @author kingbirdy
 *
 */
public class Preset {

	/**
	 * ConcurrentHashMap that stores a list of every Player's {@link Preset
	 * presets}, keyed to their UUID
	 */
	public static Map<UUID, List<Preset>> presets = new ConcurrentHashMap<>();
	public static FileConfiguration config = ConfigManager.presetConfig.get();
	public static HashMap<String, ArrayList<String>> externalPresets = new HashMap<>();
	private final UUID uuid;
	private final HashMap<Integer, String> abilities;
	private final String name;

	/**
	 * Creates a new {@link Preset}
	 *
	 * @param uuid The UUID of the Player who the Preset belongs to
	 * @param name The name of the Preset
	 * @param abilities A HashMap of the abilities to be saved in the Preset,
	 *            keyed to the slot they're bound to
	 */
	public Preset(final UUID uuid, final String name, final HashMap<Integer, String> abilities) {
		this.uuid = uuid;
		this.name = name;
		this.abilities = abilities;
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(uuid)) return;
		if (!presets.containsKey(uuid)) {
			presets.put(uuid, new ArrayList<>());
		}
		presets.get(uuid).add(this);
	}

	/**
	 * Unload a Player's Presets from those stored in memory.
	 *
	 * @param player The Player who's Presets should be unloaded
	 */
	public static void unloadPreset(final Player player) {
		final UUID uuid = player.getUniqueId();
		presets.remove(uuid);
	}

	/**
	 * Load a Player's Presets into memory.
	 *
	 * @param player The Player who's Presets should be loaded
	 */
	public static void loadPresets(final Player player) {
		if (ExternalPersistenceCoordinator.isExternalMode()) return;
		new BukkitRunnable() {
			@Override
			public void run() {
				final UUID uuid = player.getUniqueId();
				if (uuid == null) {
					return;
				}
				try {
					final List<InternalPlayerDataStore.PresetRow> rows = InternalPlayerDataStore.loadPresets(uuid);
					for (final InternalPlayerDataStore.PresetRow row : rows) new Preset(uuid, row.name(), new HashMap<>(row.abilities()));
					if (!rows.isEmpty()) PkLang.info("Loaded " + rows.size() + " presets for " + player.getName());
				} catch (final SQLException ex) {
					ex.printStackTrace();
				}
			}
		}.runTaskAsynchronously(ProjectKorra.plugin);
	}

	/**
	 * Reload a Player's Presets from those stored in memory.
	 *
	 * @param player The Player who's Presets should be unloaded
	 */
	public static void reloadPreset(final Player player) {
		unloadPreset(player);
		loadPresets(player);
	}

	/**
	 * Binds the abilities from a Preset for the given Player.
	 *
	 * @param player The Player the Preset should be bound for
	 * @param preset The Preset that should be bound
	 * @return True if all abilities were successfully bound, or false otherwise
	 */
	public static boolean bindPreset(final Player player, final Preset preset) {
		final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
		if (bPlayer == null) {
			return false;
		}

		final HashMap<Integer, String> abilities = new HashMap<>(preset.abilities);
		boolean boundAll = true;
		for (int i = 1; i <= 9; i++) {
			final CoreAbility coreAbil = CoreAbility.getAbility(abilities.get(i));
			if (coreAbil != null && !bPlayer.canBind(coreAbil)) {
				abilities.remove(i);
				boundAll = false;
			} else {
				final PlayerBindChangeEvent event = new PlayerBindChangeEvent(player, abilities.get(i), i, true, false);
				Bukkit.getPluginManager().callEvent(event);
				if (event.isCancelled()) {
					abilities.remove(i);
					boundAll = false;
				}
			}
		}
		bPlayer.setAbilities(abilities);
		BendingBoardManager.updateAllSlots(player);
		return boundAll;
	}

	/**
	 * Checks if a Preset with a certain name exists for a given Player.
	 *
	 * @param player The player who's Presets should be checked
	 * @param name The name of the Preset to look for
	 * @return true if the Preset exists, false otherwise
	 */
	public static boolean presetExists(final Player player, final String name) {
		if (!presets.containsKey(player.getUniqueId())) {
			return false;
		}
		boolean exists = false;
		for (final Preset preset : presets.get(player.getUniqueId())) {
			if (preset.name.equalsIgnoreCase(name)) {
				exists = true;
			}
		}
		return exists;
	}

	/**
	 * Gets a Preset for the specified Player.
	 *
	 * @param player The Player who's Preset should be gotten
	 * @param name The name of the Preset to get
	 * @return The Preset, if it exists, or null otherwise
	 */
	public static Preset getPreset(final Player player, final String name) {
		if (!presets.containsKey(player.getUniqueId())) {
			return null;
		}
		for (final Preset preset : presets.get(player.getUniqueId())) {
			if (preset.name.equalsIgnoreCase(name)) {
				return preset;
			}
		}
		return null;
	}

	public static void loadExternalPresets() {
		final HashMap<String, ArrayList<String>> presets = new HashMap<String, ArrayList<String>>();
		for (final String name : config.getKeys(false)) {
			if (!presets.containsKey(name)) {
				if (!config.getStringList(name).isEmpty() && config.getStringList(name).size() <= 9) {
					presets.put(name.toLowerCase(), (ArrayList<String>) config.getStringList(name));
				}
			}
		}
		externalPresets = presets;
	}

	public static boolean externalPresetExists(final String name) {
		for (final String preset : externalPresets.keySet()) {
			if (name.equalsIgnoreCase(preset)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the contents of a Preset for the specified Player.
	 *
	 * @param player The Player who's Preset should be gotten
	 * @param name The name of the Preset who's contents should be gotten
	 * @return HashMap of ability names keyed to hotbar slots, if the Preset
	 *         exists, or null otherwise
	 */
	public static HashMap<Integer, String> getPresetContents(final Player player, final String name) {
		if (!presets.containsKey(player.getUniqueId())) {
			return null;
		}
		for (final Preset preset : presets.get(player.getUniqueId())) {
			if (preset.name.equalsIgnoreCase(name)) {
				return preset.abilities;
			}
		}
		return null;
	}

	public static boolean bindExternalPreset(final Player player, final String name) {
		boolean boundAll = true;
		int slot = 0;
		final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
		if (bPlayer == null) {
			return false;
		}

		final HashMap<Integer, String> abilities = new HashMap<Integer, String>();

		if (externalPresetExists(name.toLowerCase())) {
			for (final String ability : externalPresets.get(name.toLowerCase())) {
				slot++;
				final CoreAbility coreAbil = CoreAbility.getAbility(ability);
				if (coreAbil != null) {
					abilities.put(slot, coreAbil.getName());
				}
			}

			for (int i = 1; i <= 9; i++) {
				final String abil = abilities.get(i);
				final CoreAbility coreAbil = CoreAbility.getAbility(abil);
				if (coreAbil != null && (!bPlayer.canBind(coreAbil))) {
					abilities.remove(i);
					boundAll = false;
				} else {
					final PlayerBindChangeEvent event = new PlayerBindChangeEvent(player, abil, i, true, false);
					Bukkit.getPluginManager().callEvent(event);
					if (event.isCancelled()) {
						abilities.remove(i);
						boundAll = false;
					}
				}
			}
			bPlayer.setAbilities(abilities);
			BendingBoardManager.updateAllSlots(player);
			return boundAll;
		}
		return false;
	}

	/**
	 * Deletes the Preset from the database.
	 */
	public CompletableFuture<Boolean> delete() {
		if (ExternalPersistenceCoordinator.isExternalMode()) {
			final OfflineBendingPlayer bender = BendingPlayer.getCachedOffline(Bukkit.getOfflinePlayer(this.uuid));
			if (bender == null) return CompletableFuture.completedFuture(false);
			return ExternalPersistenceCoordinator.mutate(bender, new BendingPlayerMutationOperation.DeletePreset(this.name),
					MutationSource.projectKorra("preset-delete")).thenApply(result -> result.accepted()).toCompletableFuture();
		}
		Preset instance = this;
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		new BukkitRunnable() {
			@Override
			public void run() {
				try {
					InternalPlayerDataStore.deletePreset(uuid, name);
					presets.get(uuid).remove(instance);
					future.complete(true);
				} catch (final SQLException e) {
					e.printStackTrace();
					future.complete(false);
				}
			}
		}.runTaskAsynchronously(ProjectKorra.plugin);
		return future;
	}

	/**
	 * Gets the name of the preset.
	 *
	 * @return The name of the preset
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Saves the Preset to the database async
	 */
	public CompletableFuture<Boolean> save(final Player player) {
		if (ExternalPersistenceCoordinator.isExternalMode()) {
			final OfflineBendingPlayer bender = BendingPlayer.getCachedOffline(Bukkit.getOfflinePlayer(this.uuid));
			if (bender == null) return CompletableFuture.completedFuture(false);
			return ExternalPersistenceCoordinator.mutate(bender, new BendingPlayerMutationOperation.UpsertPreset(this.name, this.abilities),
					MutationSource.projectKorra("preset-save")).thenApply(result -> {
					if (!result.accepted()) removeExternalPreset(this.uuid, this.name);
					return result.accepted();
				}).toCompletableFuture();
		}
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		new BukkitRunnable() {
			@Override
			public void run() {
				try {
					InternalPlayerDataStore.insertPreset(uuid, name, abilities);
					future.complete(true);
				} catch (final SQLException e) {
					e.printStackTrace();
					future.complete(false);
				}
			}
		}.runTaskAsynchronously(ProjectKorra.plugin);
		return future;
	}

	public HashMap<Integer, String> getAbilities() {
		return abilities;
	}

	public UUID getUUID() {
		return uuid;
	}

	public record BindResult(boolean persisted, boolean boundAll) {}

	public static CompletionStage<BindResult> bindPresetAsync(final Player player, final Preset preset) {
		if (!ExternalPersistenceCoordinator.isExternalMode()) return CompletableFuture.completedFuture(new BindResult(true, bindPreset(player, preset)));
		return bindAbilitiesAsync(player, new HashMap<>(preset.abilities), "preset-bind");
	}

	public static CompletionStage<BindResult> bindExternalPresetAsync(final Player player, final String name) {
		if (!ExternalPersistenceCoordinator.isExternalMode()) return CompletableFuture.completedFuture(new BindResult(true, bindExternalPreset(player, name)));
		final ArrayList<String> configured = externalPresets.get(name.toLowerCase());
		if (configured == null) return CompletableFuture.completedFuture(new BindResult(false, false));
		final HashMap<Integer, String> abilities = new HashMap<>();
		for (int index = 0; index < configured.size() && index < 9; index++) {
			final CoreAbility ability = CoreAbility.getAbility(configured.get(index));
			if (ability != null) abilities.put(index + 1, ability.getName());
		}
		return bindAbilitiesAsync(player, abilities, "external-preset-bind");
	}

	private static CompletionStage<BindResult> bindAbilitiesAsync(final Player player, final HashMap<Integer, String> supplied, final String detail) {
		final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
		if (bPlayer == null) return CompletableFuture.completedFuture(new BindResult(false, false));
		boolean boundAll = true;
		for (int slot = 1; slot <= 9; slot++) {
			final String name = supplied.get(slot);
			final CoreAbility ability = CoreAbility.getAbility(name);
			if (ability != null && !bPlayer.canBind(ability)) { supplied.remove(slot); boundAll = false; continue; }
			final PlayerBindChangeEvent event = new PlayerBindChangeEvent(player, name, slot, true, false);
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) { supplied.remove(slot); boundAll = false; }
		}
		final boolean finalBoundAll = boundAll;
		return bPlayer.requestMutationAsync(new BendingPlayerMutationOperation.ReplaceBinds(supplied), MutationSource.projectKorra(detail))
				.thenApply(result -> {
					if (result.accepted()) BendingBoardManager.updateAllSlots(player);
					return new BindResult(result.accepted(), finalBoundAll);
				});
	}

	public static void replaceExternalPresets(final UUID uuid, final Map<String, Map<Integer, String>> supplied) {
		presets.remove(uuid);
		for (final Map.Entry<String, Map<Integer, String>> entry : supplied.entrySet()) {
			new Preset(uuid, entry.getKey(), new HashMap<>(entry.getValue()));
		}
	}

	public static void applyExternalPresetMutation(final UUID uuid, final String name, final Map<Integer, String> abilities) {
		removeExternalPreset(uuid, name);
		new Preset(uuid, name, new HashMap<>(abilities));
	}

	public static void removeExternalPreset(final UUID uuid, final String name) {
		final List<Preset> values = presets.get(uuid);
		if (values == null) return;
		values.removeIf(preset -> preset.name.equalsIgnoreCase(name));
		if (values.isEmpty()) presets.remove(uuid);
	}

	public static void clearExternalPresets(final UUID uuid) {
		presets.remove(uuid);
	}

	/** Returns a defensive snapshot used only for atomic external projection rollback. */
	public static Map<String, Map<Integer, String>> snapshotExternalPresets(final UUID uuid) {
		final Map<String, Map<Integer, String>> snapshot = new java.util.LinkedHashMap<>();
		final List<Preset> values = presets.get(uuid);
		if (values != null) for (final Preset preset : values) snapshot.put(preset.name, Map.copyOf(preset.abilities));
		return Map.copyOf(snapshot);
	}
}
