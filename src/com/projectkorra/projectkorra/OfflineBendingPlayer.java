package com.projectkorra.projectkorra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.ability.AbstractSkill;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.storage.DBConnection;
import com.projectkorra.projectkorra.event.BendingPlayerCreationEvent;
import com.projectkorra.projectkorra.event.BendingPlayerInitializationFailureEvent;
import com.projectkorra.projectkorra.util.Cooldown;
import com.projectkorra.projectkorra.util.logging.PkLang;

import lombok.Getter;
import lombok.Setter;

public class OfflineBendingPlayer extends Bender {

	/**
	 * ConcurrentHashMap that contains all instances of ALL BendingPlayer, with UUID
	 * key.
	 */
	protected static final Map<UUID, OfflineBendingPlayer> PLAYERS = new ConcurrentHashMap<>();

	/**
	 * ConcurrentHashMap that contains all instances of online BendingPlayer, with UUID
	 * key.
	 */
	protected static final Map<UUID, BendingPlayer> ONLINE_PLAYERS = new ConcurrentHashMap<>();
	private static final Map<UUID, CompletableFuture<OfflineBendingPlayer>> LOADS = new ConcurrentHashMap<>();
	private static final Map<UUID, BendingPlayerInitializationState> INITIALIZATION_STATES = new ConcurrentHashMap<>();

	@Getter
	protected final OfflinePlayer player;
	protected boolean loading;

	@Setter @Getter
	private int currentSlot;
	private long lastAccessed;
	private long uncacheTime = 30_000; //This is the default time to unload after when the data is accessed by code, NOT when logging out
	private BukkitTask uncache;

	public OfflineBendingPlayer(@NotNull OfflinePlayer player) {
		super(player.getUniqueId());
		this.player = player;
		this.loading = true;

		this.lastAccessed = System.currentTimeMillis();
	}

	public OfflineBendingPlayer(@NotNull UUID playerUUID) {
		this(Bukkit.getOfflinePlayer(playerUUID));
	}

	protected static CompletableFuture<OfflineBendingPlayer> loadAsync(@NotNull final UUID uuid, final boolean onStartup) {
		final OfflineBendingPlayer cached = PLAYERS.get(uuid);
		if (cached != null && INITIALIZATION_STATES.get(uuid) == BendingPlayerInitializationState.READY) {
			if (Bukkit.getPlayer(uuid) != null && !(cached instanceof BendingPlayer)) return promoteAsync(cached);
			if (!(cached instanceof BendingPlayer)) cached.lastAccessed = System.currentTimeMillis();
			return CompletableFuture.completedFuture(cached);
		}
		return LOADS.computeIfAbsent(uuid, key -> startAtomicLoad(key, onStartup));
	}

	private static CompletableFuture<OfflineBendingPlayer> startAtomicLoad(final UUID uuid, final boolean onStartup) {
		INITIALIZATION_STATES.put(uuid, BendingPlayerInitializationState.LOADING);
		final CompletableFuture<OfflineBendingPlayer> result = new CompletableFuture<>();
		final Runnable hydrate = () -> {
			BendingPlayerInitializationPhase phase = BendingPlayerInitializationPhase.DATABASE_LOOKUP;
			try {
				final OfflinePlayer identity = Bukkit.getOfflinePlayer(uuid);
				final OfflineBendingPlayer snapshot = new OfflineBendingPlayer(identity);
				ResultSet row = DBConnection.sql.readQuery("SELECT * FROM pk_players WHERE uuid = '" + uuid + "'");
				if (row == null) throw new SQLException("Player lookup returned no ResultSet");
				if (!row.next()) {
					phase = BendingPlayerInitializationPhase.ROW_CREATION;
					final String name = sql(identity.getName() == null ? uuid.toString() : identity.getName());
					try {
						DBConnection.sql.modifyQueryAsync("INSERT INTO pk_players (uuid, player) VALUES ('" + uuid + "', '" + name + "')", false).join();
					} catch (final CompletionException duplicateOrFailure) {
						row = DBConnection.sql.readQuery("SELECT * FROM pk_players WHERE uuid = '" + uuid + "'");
						if (row == null || !row.next()) throw duplicateOrFailure;
					}
					row = DBConnection.sql.readQuery("SELECT * FROM pk_players WHERE uuid = '" + uuid + "'");
					if (row == null || !row.next()) throw new SQLException("Created player row could not be reread");
					PkLang.info("Created new BendingPlayer for " + identity.getName());
				}
				phase = BendingPlayerInitializationPhase.HYDRATION;
				hydrateRow(snapshot, row, onStartup);
				snapshot.loading = false;
				phase = BendingPlayerInitializationPhase.MAIN_THREAD_FINALIZATION;
				Bukkit.getScheduler().callSyncMethod(ProjectKorra.plugin, () -> publish(snapshot)).get();
				result.complete(PLAYERS.get(uuid));
			} catch (final Throwable raw) {
				final Throwable error = raw instanceof java.util.concurrent.ExecutionException && raw.getCause() != null ? raw.getCause() : raw;
				failInitialization(uuid, Bukkit.getOfflinePlayer(uuid).getName(), phase, error, result);
			}
		};
		// Always enqueue so computeIfAbsent publishes the shared future before it can complete.
		Bukkit.getScheduler().runTaskAsynchronously(ProjectKorra.plugin, hydrate);
		result.whenComplete((value, error) -> LOADS.remove(uuid, result));
		return result;
	}

	private static void hydrateRow(final OfflineBendingPlayer target, final ResultSet row, final boolean onStartup) throws Exception {
		final UUID uuid = target.getUUID();
		final String currentName = target.player.getName();
		final String storedName = row.getString("player");
		if (currentName != null && (storedName == null || !currentName.equalsIgnoreCase(storedName))) {
			DBConnection.sql.modifyQueryAsync("UPDATE pk_players SET player = '" + sql(currentName) + "' WHERE uuid = '" + uuid + "'", false).join();
		}
		parseElements(target, row.getString("element"), false, onStartup);
		parseElements(target, row.getString("subelement"), true, onStartup);
		for (int slot = 1; slot <= 9; slot++) {
			final String value = row.getString("slot" + slot);
			if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) {
				if (value != null && !value.equalsIgnoreCase("null")) repairSlot(uuid, slot, value);
				continue;
			}
			final CoreAbility ability = CoreAbility.getAbility(value);
			if (ability == null || !ability.isEnabled()) {
				repairSlot(uuid, slot, value);
				continue;
			}
			target.abilities.put(slot, ability.getName());
		}
		target.permaRemoved = "true".equalsIgnoreCase(row.getString("permaremoved"));
		if (ProjectKorra.isDatabaseCooldownsEnabled()) {
			try (ResultSet cooldowns = DBConnection.sql.readQuery("SELECT * FROM pk_cooldowns WHERE uuid = '" + uuid + "'")) {
				if (cooldowns != null) while (cooldowns.next()) target.cooldowns.put(cooldowns.getString("cooldown"), new Cooldown(cooldowns.getLong("value"), true));
			}
		}
	}

	private static void parseElements(final OfflineBendingPlayer target, final String field, final boolean subelements, final boolean onStartup) throws Exception {
		if (field == null || field.isBlank() || field.equalsIgnoreCase("null") || field.equals("-")) return;
		final String[] sections = field.split(";", 2);
		final String core = sections[0];
		if (!subelements) {
			if (core.contains("a")) target.elements.add(Element.AIR);
			if (core.contains("w")) target.elements.add(Element.WATER);
			if (core.contains("e")) target.elements.add(Element.EARTH);
			if (core.contains("f")) target.elements.add(Element.FIRE);
			if (core.contains("c")) target.elements.add(Element.NON);
		} else {
			final String[] codes = {"wf","m","v","s","c","l","t","f","x","i","h","b","d","p","r","k","j","w","a"};
			final SubElement[] values = {Element.WHITE_FIRE,Element.METAL,Element.LAVA,Element.SAND,Element.COMBUSTION,Element.LIGHTNING,Element.SPIRITUAL,Element.FLIGHT,Element.SUFFOCATION,Element.ICE,Element.HEALING,Element.BLOOD,Element.DAY_BLOOD,Element.PLANT,Element.BLUE_FIRE,Element.CHI,Element.ARCHER,Element.WARRIOR,Element.BLACK_SAND};
			String remaining = core;
			for (int i = 0; i < codes.length; i++) if (remaining.contains(codes[i])) { target.subelements.add(values[i]); remaining = remaining.replace(codes[i], ""); }
		}
		if (sections.length == 1) return;
		for (final String addonName : sections[1].split(",")) {
			if (addonName.isBlank()) continue;
			Element addon = Element.getElement(addonName);
			if (addon == null && onStartup) {
				final long deadline = System.currentTimeMillis() + 5_000L;
				while (addon == null && System.currentTimeMillis() < deadline) {
					Thread.sleep(50L);
					addon = Element.getElement(addonName);
				}
			}
			if (addon == null || (subelements && !(addon instanceof SubElement))) throw new IllegalStateException("Unresolved addon " + (subelements ? "subelement " : "element ") + addonName);
			if (subelements) target.subelements.add((SubElement) addon); else target.elements.add(addon);
		}
	}

	private static void repairSlot(final UUID uuid, final int slot, final String value) {
		PkLang.warning("Repairing corrupt ability binding for " + uuid + " slot " + slot + ": " + value);
		DBConnection.sql.modifyQueryAsync("UPDATE pk_players SET slot" + slot + " = NULL WHERE uuid = '" + uuid + "'", false).join();
	}

	private static OfflineBendingPlayer publish(final OfflineBendingPlayer snapshot) {
		final UUID uuid = snapshot.getUUID();
		final Player online = Bukkit.getPlayer(uuid);
		if (online == null || !online.isOnline()) {
			PLAYERS.put(uuid, snapshot);
			ONLINE_PLAYERS.remove(uuid);
			INITIALIZATION_STATES.put(uuid, BendingPlayerInitializationState.READY);
			snapshot.uncacheAfter(30_000L);
			return snapshot;
		}
		final BendingPlayer ready = copyToOnline(snapshot, online);
		ready.postLoad();
		PLAYERS.put(uuid, ready);
		ONLINE_PLAYERS.put(uuid, ready);
		INITIALIZATION_STATES.put(uuid, BendingPlayerInitializationState.READY);
		Bukkit.getPluginManager().callEvent(new BendingPlayerCreationEvent(ready));
		return ready;
	}

	private static BendingPlayer copyToOnline(final OfflineBendingPlayer source, final Player player) {
		final BendingPlayer target = new BendingPlayer(player);
		target.abilities = new java.util.HashMap<>(source.abilities);
		target.elements.addAll(source.elements); target.subelements.addAll(source.subelements);
		target.toggledElements.addAll(source.toggledElements); target.toggledPassives.addAll(source.toggledPassives);
		target.toggled = source.toggled; target.allPassivesToggled = source.allPassivesToggled;
		target.permaRemoved = source.permaRemoved; target.cooldowns.putAll(source.cooldowns); target.loading = false;
		return target;
	}

	private static CompletableFuture<OfflineBendingPlayer> promoteAsync(final OfflineBendingPlayer cached) {
		final CompletableFuture<OfflineBendingPlayer> result = new CompletableFuture<>();
		Bukkit.getScheduler().runTask(ProjectKorra.plugin, () -> {
			try { result.complete(publish(cached)); } catch (Throwable error) { failInitialization(cached.getUUID(), cached.getName(), BendingPlayerInitializationPhase.MAIN_THREAD_FINALIZATION, error, result); }
		});
		return result;
	}

	private static void failInitialization(final UUID uuid, final String name, final BendingPlayerInitializationPhase phase,
			final Throwable error, final CompletableFuture<OfflineBendingPlayer> future) {
		INITIALIZATION_STATES.put(uuid, BendingPlayerInitializationState.FAILED);
		PLAYERS.remove(uuid); ONLINE_PLAYERS.remove(uuid);
		final Runnable event = () -> Bukkit.getPluginManager().callEvent(new BendingPlayerInitializationFailureEvent(uuid, name, phase, error));
		if (Bukkit.isPrimaryThread()) event.run(); else Bukkit.getScheduler().runTask(ProjectKorra.plugin, event);
		future.completeExceptionally(error);
	}

	private static String sql(final String value) { return value.replace("'", "''"); }

	static BendingPlayerInitializationState getInitializationState(final UUID uuid) { return INITIALIZATION_STATES.get(uuid); }
	static void allowRetry(final UUID uuid) { if (INITIALIZATION_STATES.get(uuid) == BendingPlayerInitializationState.FAILED) INITIALIZATION_STATES.remove(uuid); }

	@Deprecated
	private static CompletableFuture<OfflineBendingPlayer> legacyLoadAsync(@NotNull final UUID uuid, boolean onStartup) {
		CompletableFuture<OfflineBendingPlayer> future = new CompletableFuture<>();
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

		//If we already have the players data cached from an OfflineBendingPlayer instance
		if (PLAYERS.get(uuid) != null) {
			OfflineBendingPlayer oBendingPlayer = PLAYERS.get(uuid); //Get cached instance
			if (offlinePlayer.isOnline() && !(oBendingPlayer instanceof BendingPlayer)) {
				oBendingPlayer = convertToOnline(oBendingPlayer); //Convert to online instance
				((BendingPlayer)oBendingPlayer).postLoad();
			}
			if (!(oBendingPlayer instanceof BendingPlayer)) {
				oBendingPlayer.lastAccessed = System.currentTimeMillis();
			}
			future.complete(oBendingPlayer);
			return future;
		}

		Runnable runnable = () -> {
			OfflineBendingPlayer bPlayer = new OfflineBendingPlayer(offlinePlayer);
			if (offlinePlayer.isOnline()) {
				bPlayer = new BendingPlayer(((Player)offlinePlayer));
				ONLINE_PLAYERS.put(uuid, (BendingPlayer)bPlayer);
			}

			PLAYERS.put(uuid, bPlayer);

			final ResultSet rs2 = DBConnection.sql.readQuery("SELECT * FROM pk_players WHERE uuid = '" + uuid + "'");
			try {
				if (!rs2.next()) { // Data doesn't exist, we want a completely new player.
					DBConnection.sql.modifyQuery("INSERT INTO pk_players (uuid, player, slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9) VALUES ('" + uuid.toString() + "', '" + offlinePlayer.getName() + "', 'null', 'null', 'null', 'null', 'null', 'null', 'null', 'null', 'null')");
					Bukkit.getScheduler().runTask(ProjectKorra.plugin, () -> PkLang.info("Created new BendingPlayer for " + offlinePlayer.getName()));
					OfflineBendingPlayer newPlayer;
					if (offlinePlayer.isOnline()) {
						newPlayer = new BendingPlayer((Player)offlinePlayer);
						//Call postLoad() on the main thread and wait for it to complete
						Bukkit.getScheduler().callSyncMethod(ProjectKorra.plugin, () -> {
							((BendingPlayer)newPlayer).postLoad();
							return true;
						}).get();
						ONLINE_PLAYERS.put(uuid, (BendingPlayer) newPlayer);
					} else {
						newPlayer = new OfflineBendingPlayer(offlinePlayer);
					}
					PLAYERS.put(uuid, newPlayer);
					future.complete(newPlayer);
				} else {
					// The player has at least played before.
					final String player2 = rs2.getString("player");
					if (!offlinePlayer.getName().equalsIgnoreCase(player2)) {
						DBConnection.sql.modifyQuery("UPDATE pk_players SET player = '" + offlinePlayer.getName() + "' WHERE uuid = '" + uuid.toString() + "'");
						// They have changed names.
						PkLang.info("Updating Player Name for " + offlinePlayer.getName());
					}
					final String subelementField = rs2.getString("subelement");
					final String elementField = rs2.getString("element");
					final String permaremovedField = rs2.getString("permaremoved");

					//Load the elements
					if (elementField != null && !elementField.equalsIgnoreCase("NULL")) {
						final boolean hasAddon = elementField.contains(";");
						final String[] split = elementField.split(";");
						if (split.length > 0 && !split[0].equals("")) { // Player has an element.
							if (split[0].contains("a")) {
								bPlayer.elements.add(Element.AIR);
							}
							if (split[0].contains("w")) {
								bPlayer.elements.add(Element.WATER);
							}
							if (split[0].contains("e")) {
								bPlayer.elements.add(Element.EARTH);
							}
							if (split[0].contains("f")) {
								bPlayer.elements.add(Element.FIRE);
							}
							if (split[0].contains("c")) {
								bPlayer.elements.add(Element.NON);
							}
						}
						if (hasAddon) {
							/*
							 * Because plugins which depend on ProjectKorra
							 * would be loaded after ProjectKorra, addon
							 * elements would = null. To work around this, we
							 * keep trying to load in the elements from the
							 * database until it successfully loads everything
							 * in, or it times out.
							 */
							final CopyOnWriteArrayList<String> addonClone = new CopyOnWriteArrayList<>(Arrays.asList(split[split.length - 1].split(",")));
							final long startTime = System.currentTimeMillis();
							final long timeoutLength = 5_000; // How long until it should time out attempting to load addons in.
							OfflineBendingPlayer finalBPlayer = bPlayer;
							Predicate<List<String>> func = (elements) -> {
								if (System.currentTimeMillis() - startTime > timeoutLength) {
									PkLang.severe("ProjectKorra has timed out after attempting to load in the following addon elements: " + addonClone.toString());
									PkLang.severe("These elements have taken too long to load in, resulting in users having lost these element.");
									return true;
								} else {
									PkLang.info("Attempting to load in the following addon elements... " + elements.toString());
									for (final String addon : elements) {
										if (Element.getElement(addon) != null) {
											finalBPlayer.elements.add(Element.getElement(addon));
											elements.remove(addon);
										}
									}
									if (elements.isEmpty()) {
										PkLang.info("Successfully loaded in all addon elements!");
										return true;
									}
								}
								return false;
							};

							if (onStartup) { //If we are doing this on startup, addon elements aren't loaded yet. So do this async
								new BukkitRunnable() {
									@Override
									public void run() {
										if (func.test(addonClone)) {
											this.cancel();
										}
									}
								}.runTaskTimer(ProjectKorra.plugin, 0, 5);
							} else func.test(addonClone); //Addon elements should be loaded so
						}
					}

					//Load subelements
					if (subelementField != null && !subelementField.equalsIgnoreCase("NULL")) {
						final boolean hasAddon = subelementField.contains(";");
						final String[] split = subelementField.split(";");

						//If the subelements aren't defined, we give them now
						/*if (subelementField.equals("-")) {
							boolean shouldSave = false;
							if (offlinePlayer instanceof Player) { //Only if the player is online though
								subloop:
								for (final SubElement sub : Element.getAllSubElements()) {
									if (sub instanceof Element.MultiSubElement) { //If it's a multisub, check if they have any of the parent element and perm for the sub of that parent
										for (Element parent : ((Element.MultiSubElement) sub).getParentElements()) {
											if (((Player) offlinePlayer).hasPermission("bending." + parent.getName() + "." + sub.getName() + sub.getType().getBending()) && bPlayer.elements.contains(sub.getParentElement())) {
												bPlayer.subelements.add(sub);
												continue subloop;
											}
										}
									} else if (((Player)offlinePlayer).hasPermission("bending." + sub.getParentElement().getName().toLowerCase() + "." + sub.getName().toLowerCase() + sub.getType().getBending())
											&& bPlayer.elements.contains(sub.getParentElement())) {
										bPlayer.subelements.add(sub);
										shouldSave = true;
									}
								}
								if (shouldSave) bPlayer.saveSubElements();
							}
						} else*/ if (split.length > 0 && !split[0].equals("")) {
							if (split[0].contains("m")) {
								bPlayer.subelements.add(Element.METAL);
							}
							if (split[0].contains("v")) {
								bPlayer.subelements.add(Element.LAVA);
							}
							if (split[0].contains("s")) {
								bPlayer.subelements.add(Element.SAND);
							}
							if (split[0].contains("c")) {
								bPlayer.subelements.add(Element.COMBUSTION);
							}
							if (split[0].contains("l")) {
								bPlayer.subelements.add(Element.LIGHTNING);
							}
							if (split[0].contains("t")) {
								bPlayer.subelements.add(Element.SPIRITUAL);
							}
							if (split[0].contains("f")) {
								bPlayer.subelements.add(Element.FLIGHT);
							}
							if (split[0].contains("x")) {
								bPlayer.subelements.add(Element.SUFFOCATION);
							}
							if (split[0].contains("i")) {
								bPlayer.subelements.add(Element.ICE);
							}
							if (split[0].contains("h")) {
								bPlayer.subelements.add(Element.HEALING);
							}
							if (split[0].contains("b")) {
								bPlayer.subelements.add(Element.BLOOD);
							}
							if (split[0].contains("d")) {
								bPlayer.subelements.add(Element.DAY_BLOOD);
							}
							if (split[0].contains("p")) {
								bPlayer.subelements.add(Element.PLANT);
							}
							if (split[0].contains("r")) {
								bPlayer.subelements.add(Element.BLUE_FIRE);
							}
							if (split[0].contains("k")) {
								bPlayer.subelements.add(Element.CHI);
							}
							if (split[0].contains("j")) {
								bPlayer.subelements.add(Element.ARCHER);
							}
							if (split[0].contains("w")) {
								bPlayer.subelements.add(Element.WARRIOR);
							}
							if (split[0].contains("a")) {
								bPlayer.subelements.add(Element.BLACK_SAND);
							}
							if (split[0].contains("wf")) {
								bPlayer.subelements.add(Element.WHITE_FIRE);
							}
						}
						if (hasAddon) {
							final CopyOnWriteArrayList<String> addonClone = new CopyOnWriteArrayList<>(Arrays.asList(split[split.length - 1].split(",")));
							final long startTime = System.currentTimeMillis();
							final long timeoutLength = 5_000; // How long until it should time out attempting to load addons in.
							OfflineBendingPlayer finalBPlayer1 = bPlayer;
							Predicate<List<String>> func = (elements) -> {
								if (System.currentTimeMillis() - startTime > timeoutLength) {
									PkLang.severe("ProjectKorra has timed out after attempting to load in the following addon subelements: " + addonClone.toString());
									PkLang.severe("These subelements have taken too long to load in, resulting in users having lost these subelement.");
									return true;
								} else {
									PkLang.info("Attempting to load in the following addon subelements... " + elements.toString());
									for (final String addon : elements) {
										if (Element.getElement(addon) != null && Element.getElement(addon) instanceof SubElement) {
											finalBPlayer1.subelements.add((SubElement) Element.getElement(addon));
											elements.remove(addon);
										}
									}

									if (elements.isEmpty()) {
										PkLang.info("Successfully loaded in all addon subelements!");
										return true;
									}
									return false;
								}
							};
							if (onStartup) { //If we are doing this on startup, addon elements aren't loaded yet. So do this async
								new BukkitRunnable() {
									@Override
									public void run() {
										if (func.test(addonClone)) {
											this.cancel();
										}
									}
								}.runTaskTimer(ProjectKorra.plugin, 0, 5);
							} else func.test(addonClone); //Addon elements should be loaded by now
						}
					}

					//Load the abilities
					final ConcurrentHashMap<Integer, String> abilitiesClone = new ConcurrentHashMap<>();
					for (int i = 1; i <= 9; i++) {
						final String ability = rs2.getString("slot" + i);
						abilitiesClone.put(i, ability);
					}
					final long startTime = System.currentTimeMillis();
					final long timeoutLength = 5_000; // How long until it should time out attempting to load addons in.
					OfflineBendingPlayer finalBPlayer2 = bPlayer;
					Predicate<Map<Integer, String>> func = (abils) -> {
						if (System.currentTimeMillis() - startTime > timeoutLength) {
							PkLang.severe("ProjectKorra has timed out after attempting to load in the following abilities: " + abilitiesClone);
							PkLang.severe("These abilities have taken too long to load in, resulting in users having lost these abilities.");
							return true;
						} else {
							for (final Map.Entry<Integer, String> set : abils.entrySet()) {
								if (set.getValue() == null || set.getValue().equalsIgnoreCase("null")) {
									abils.remove(set.getKey());
								} else if (CoreAbility.getAbility(set.getValue()) != null && CoreAbility.getAbility(set.getValue()).isEnabled()) {
									finalBPlayer2.abilities.put(set.getKey(), set.getValue());
									abils.remove(set.getKey());
								}
							}

							if (abils.isEmpty()) {
								PkLang.info("Successfully loaded in all abilities!");
								return true;
							}
							return false;
						}
					};
					if (onStartup) { //If we are doing this on startup, addon elements aren't loaded yet. So do this async
						new BukkitRunnable() {
							@Override
							public void run() {
								if (func.test(abilitiesClone)) {
									this.cancel();
								}
							}
						}.runTaskTimer(ProjectKorra.plugin, 0, 5);
					} else func.test(abilitiesClone); //Addon elements should be loaded by now

					//Load permaRemove
					if (permaremovedField != null && permaremovedField.equalsIgnoreCase("true")) bPlayer.permaRemoved = true;

					//Load cooldowns
					if (ProjectKorra.isDatabaseCooldownsEnabled()) {
						try (ResultSet rs = DBConnection.sql.readQuery("SELECT * FROM pk_cooldowns WHERE uuid = '" + uuid + "'")) {
							while (rs.next()) {
								final String name = rs.getString("cooldown");
								final long value = rs.getLong("value");
								bPlayer.cooldowns.put(name, new Cooldown(value, true));
							}
						} catch (final SQLException e) {
							e.printStackTrace();
						}
					}

					bPlayer.loading = false;
					//Call postLoad() on the main thread and wait for it to complete
					if (bPlayer instanceof BendingPlayer finalBPlayer3) {
						Bukkit.getScheduler().callSyncMethod(ProjectKorra.plugin, () -> {
							finalBPlayer3.postLoad();
							return true;
						}).get();
					} else {
						bPlayer.uncacheAfter(30_000);
					}

					future.complete(bPlayer);
				}
			} catch (final SQLException | ExecutionException | InterruptedException ex) {
				ex.printStackTrace();
				future.cancel(true);
			}
		};

		if (!Bukkit.isPrimaryThread()) runnable.run();
		else Bukkit.getScheduler().runTaskAsynchronously(ProjectKorra.plugin, runnable);

		return future;
	}

	/**
	 * Saves the subelements of a BendingPlayer to the database.
	 */
	public void saveSubElements() {
		final StringBuilder subs = new StringBuilder();
		if (this.hasSubElement(Element.METAL)) {
			subs.append("m");
		}
		if (this.hasSubElement(Element.LAVA)) {
			subs.append("v");
		}
		if (this.hasSubElement(Element.SAND)) {
			subs.append("s");
		}
		if (this.hasSubElement(Element.COMBUSTION)) {
			subs.append("c");
		}
		if (this.hasSubElement(Element.LIGHTNING)) {
			subs.append("l");
		}
		if (this.hasSubElement(Element.SPIRITUAL)) {
			subs.append("t");
		}
		if (this.hasSubElement(Element.FLIGHT)) {
			subs.append("f");
		}
		if (this.hasSubElement(Element.SUFFOCATION)) {
			subs.append("x");
		}
		if (this.hasSubElement(Element.ICE)) {
			subs.append("i");
		}
		if (this.hasSubElement(Element.HEALING)) {
			subs.append("h");
		}
		if (this.hasSubElement(Element.BLOOD)) {
			subs.append("b");
		}
		if (this.hasSubElement(Element.DAY_BLOOD)) {
			subs.append("d");
		}
		if (this.hasSubElement(Element.PLANT)) {
			subs.append("p");
		}
		if (this.hasSubElement(Element.BLUE_FIRE)) {
			subs.append("r");
		}
		if (this.hasSubElement(Element.CHI)) {
			subs.append("k");
		}
		if (this.hasSubElement(Element.ARCHER)) {
			subs.append("j");
		}
		if (this.hasSubElement(Element.WARRIOR)) {
			subs.append("w");
		}
		if (this.hasSubElement(Element.BLACK_SAND)) {
			subs.append("a");
		}
		if (this.hasSubElement(Element.WHITE_FIRE)) {
			subs.append("wf");
		}
		boolean hasAddon = false;
		List<SubElement> addonSubs = Arrays.asList(Element.getAddonSubElements());
		for (final Element element : this.getSubElements()) {
			if (addonSubs.contains(element)) {
				if (!hasAddon) {
					hasAddon = true;
					subs.append(";");
				}
				subs.append(element.getName() + ",");
			}
		}

		if (subs.length() == 0) {
			subs.append("NULL");
		}

		DBConnection.sql.modifyQuery("UPDATE pk_players SET subelement = '" + subs.toString() + "' WHERE uuid = '" + uuid + "'");
	}

	/** Saves a snapshot of the current subelements and reports SQL completion. */
	public CompletableFuture<Void> saveSubElementsAsync() {
		final StringBuilder value = new StringBuilder();
		final SubElement[] core = {Element.METAL,Element.LAVA,Element.SAND,Element.COMBUSTION,Element.LIGHTNING,Element.SPIRITUAL,Element.FLIGHT,Element.SUFFOCATION,Element.ICE,Element.HEALING,Element.BLOOD,Element.DAY_BLOOD,Element.PLANT,Element.BLUE_FIRE,Element.CHI,Element.ARCHER,Element.WARRIOR,Element.BLACK_SAND,Element.WHITE_FIRE};
		final String[] codes = {"m","v","s","c","l","t","f","x","i","h","b","d","p","r","k","j","w","a","wf"};
		for (int i = 0; i < core.length; i++) if (this.hasSubElement(core[i])) value.append(codes[i]);
		boolean separator = false;
		final List<SubElement> addons = Arrays.asList(Element.getAddonSubElements());
		for (final SubElement element : List.copyOf(this.getSubElements())) if (addons.contains(element)) {
			if (!separator) { value.append(';'); separator = true; }
			value.append(element.getName()).append(',');
		}
		if (value.length() == 0) value.append("NULL");
		return DBConnection.sql.modifyQueryAsync("UPDATE pk_players SET subelement = '" + sql(value.toString()) + "' WHERE uuid = '" + uuid + "'");
	}

	/**
	 * Saves the elements of a BendingPlayer to the database.
	 */
	public void saveElements() {
		final StringBuilder elements = new StringBuilder();
		if (this.hasElement(Element.AIR)) {
			elements.append("a");
		}
		if (this.hasElement(Element.WATER)) {
			elements.append("w");
		}
		if (this.hasElement(Element.EARTH)) {
			elements.append("e");
		}
		if (this.hasElement(Element.FIRE)) {
			elements.append("f");
		}
		if (this.hasElement(Element.NON)) {
			elements.append("c");
		}
		boolean hasAddon = false;
		List<Element> addonElements = Arrays.asList(Element.getAddonElements());
		for (final Element element : this.getElements()) {
			if (addonElements.contains(element)) {
				if (!hasAddon) {
					hasAddon = true;
					elements.append(";");
				}
				elements.append(element.getName() + ",");
			}
		}

		if (elements.length() == 0) {
			elements.append("NULL");
		}

		DBConnection.sql.modifyQuery("UPDATE pk_players SET element = '" + elements.toString() + "' WHERE uuid = '" + uuid + "'");
	}

	/** Saves a snapshot of the current elements and reports SQL completion. */
	public CompletableFuture<Void> saveElementsAsync() {
		final StringBuilder value = new StringBuilder();
		if (this.hasElement(Element.AIR)) value.append('a');
		if (this.hasElement(Element.WATER)) value.append('w');
		if (this.hasElement(Element.EARTH)) value.append('e');
		if (this.hasElement(Element.FIRE)) value.append('f');
		if (this.hasElement(Element.NON)) value.append('c');
		boolean separator = false;
		final List<Element> addons = Arrays.asList(Element.getAddonElements());
		for (final Element element : List.copyOf(this.getElements())) if (addons.contains(element)) {
			if (!separator) { value.append(';'); separator = true; }
			value.append(element.getName()).append(',');
		}
		if (value.length() == 0) value.append("NULL");
		return DBConnection.sql.modifyQueryAsync("UPDATE pk_players SET element = '" + sql(value.toString()) + "' WHERE uuid = '" + uuid + "'");
	}

	/**
	 * Binds an ability to the hotbar slot that the player is on.
	 *
	 * @param ability The ability name to bind
	 * @see #bindAbility(String, int)
	 */
	public void bindAbility(final String ability) {
		bindAbility(ability, getCurrentSlot() + 1);
	}

	public void clearAbility(final int slot) {
		this.setAbility(slot, null);
	}

	public void clearAbilities() {
		for (int i = 1; i <= 9; i++) {
			this.setAbility(i, null);
		}
	}

	/**
	 * Gets the name of the {@link BendingPlayer}.
	 *
	 * @return the player name
	 */
	public String getName() {
		return this.player.getName();
	}

	/**
	 * Gets the Ability bound to the slot that the player is in.
	 *
	 * @return The Ability name bounded to the slot
	 */
	public String getBoundAbilityName() {
		final int slot = getCurrentSlot() + 1;
		final String name = this.getAbilities().get(slot);

		return name != null ? name : "";
	}

	@Override
	public boolean boundAbilityMatches(final String abilityName) {
		return abilityName.equals(getBoundAbilityName());
	}

	@Override
	public boolean hasUnlocked(String abilityName) {
		return !AbstractSkill.isLocked(abilityName, player);
	}

	/**
	 * Checks to see if a player can BloodBend.
	 *
	 * @return true If player has permission node "bending.earth.bloodbending"
	 */
	public boolean canBloodbend() {
		return super.canBloodbend() && !AbstractSkill.isLocked("BloodbendingSub", player);
	}

	public boolean canBloodbendAtAnytime() {
		return super.canBloodbendAtAnytime() && !AbstractSkill.isLocked("DayBlood", player);
	}

	public boolean canCombustionbend() {
		return super.canCombustionbend() && !AbstractSkill.isLocked("Combustion", player);
	}

	public boolean canIcebend() {
		return super.canIcebend() && !AbstractSkill.isLocked("PhaseChange", player);
	}

	/**
	 * Checks to see if a player can LavaBend.
	 *
	 * @return true If player has permission node "bending.earth.lavabending"
	 */
	public boolean canLavabend() {
		return super.canLavabend() && !AbstractSkill.isLocked("LavaFlow", player);
	}

	public boolean canLightningbend() {
		return super.canLightningbend() && !AbstractSkill.isLocked("Lightning", player);
	}

	public boolean canUseBlueFire() {
		return super.canUseBlueFire() && !AbstractSkill.isLocked("BlueFire", player);
	}

	/**
	 * Checks to see if a player can MetalBend.
	 *
	 * @return true If player has permission node "bending.earth.metalbending"
	 */
	public boolean canMetalbend() {
		return super.canMetalbend() && !AbstractSkill.isLocked("FerroControl", player);
	}

	/**
	 * Checks to see if a player can PlantBend.
	 *
	 * @return true If player has permission node "bending.ability.plantbending"
	 */
	public boolean canPlantbend() {
		return super.canPlantbend() && !AbstractSkill.isLocked("Plantbending", player);
	}

	/**
	 * Checks to see if a player can SandBend.
	 *
	 * @return true If player has permission node "bending.earth.sandbending"
	 */
	public boolean canSandbend() {
		return super.canSandbend() && !AbstractSkill.isLocked("DensityShift", player);
	}

	/**
	 * Checks to see if a player can use Flight.
	 *
	 * @return true If player has permission node "bending.air.flight"
	 */
	public boolean canUseFlight() {
		return super.canUseFlight() && !AbstractSkill.isLocked("Flight", player);
	}

	/**
	 * Checks to see if a player can use SpiritualProjection.
	 *
	 * @return true If player has permission node
	 *         "bending.air.spiritualprojection"
	 */
	public boolean canUseSpiritualProjection() {
		return super.canUseSpiritualProjection() && !AbstractSkill.isLocked("SpiritualProjection", player);
	}

	/**
	 * Checks to see if a player can use Suffocate.
	 */
	public boolean canUseSuffocation() {
		return super.canUseSuffocation() && !AbstractSkill.isLocked("Suffocate", player);
	}

	/**
	 * Checks to see if a player can use Water Healing.
	 *
	 * @return true If player has permission node "bending.water.healing"
	 */
	public boolean canWaterHeal() {
		return super.canWaterHeal() && !AbstractSkill.isLocked("HealingHands", player);
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
	}

	protected static BendingPlayer convertToOnline(@NotNull OfflineBendingPlayer offlineBendingPlayer) {
		Player player = Bukkit.getPlayer(offlineBendingPlayer.getUUID());
		if (player == null) {
			return null;
		}
		BendingPlayer bendingPlayer = new BendingPlayer(player);
		bendingPlayer.abilities = offlineBendingPlayer.abilities;
		bendingPlayer.elements.addAll(offlineBendingPlayer.elements);
		bendingPlayer.subelements.addAll(offlineBendingPlayer.subelements);
		bendingPlayer.toggledElements.addAll(offlineBendingPlayer.toggledElements);
		bendingPlayer.toggledPassives.addAll(offlineBendingPlayer.toggledPassives);
		bendingPlayer.toggled = offlineBendingPlayer.toggled;
		bendingPlayer.allPassivesToggled = offlineBendingPlayer.allPassivesToggled;
		bendingPlayer.permaRemoved = offlineBendingPlayer.permaRemoved;
		bendingPlayer.cooldowns.putAll(offlineBendingPlayer.cooldowns);
		bendingPlayer.loading = false;

		if (offlineBendingPlayer.uncache != null) {
			offlineBendingPlayer.uncache.cancel();
		}

		PLAYERS.put(player.getUniqueId(), bendingPlayer);
		ONLINE_PLAYERS.put(player.getUniqueId(), bendingPlayer);

		return bendingPlayer;
	}

	protected static OfflineBendingPlayer convertToOffline(@NotNull BendingPlayer bendingPlayer) {
		if (bendingPlayer.getPlayer() != null && bendingPlayer.getPlayer().isOnline()) return bendingPlayer;

		OfflineBendingPlayer offlineBendingPlayer = new OfflineBendingPlayer(bendingPlayer.getPlayer());
		offlineBendingPlayer.abilities = bendingPlayer.abilities;
		offlineBendingPlayer.elements.addAll(bendingPlayer.elements);
		offlineBendingPlayer.subelements.addAll(bendingPlayer.subelements);
		offlineBendingPlayer.toggledElements.addAll(bendingPlayer.toggledElements);
		offlineBendingPlayer.toggledPassives.addAll(bendingPlayer.toggledPassives);
		offlineBendingPlayer.toggled = bendingPlayer.toggled;
		offlineBendingPlayer.allPassivesToggled = bendingPlayer.allPassivesToggled;
		offlineBendingPlayer.permaRemoved = bendingPlayer.permaRemoved;
		offlineBendingPlayer.cooldowns.putAll(bendingPlayer.cooldowns);
		offlineBendingPlayer.loading = false;
		offlineBendingPlayer.lastAccessed = System.currentTimeMillis();

		if (bendingPlayer.getPlayer() == null || !bendingPlayer.getPlayer().isOnline()) ONLINE_PLAYERS.remove(bendingPlayer.getUUID());
		PLAYERS.put(bendingPlayer.getUUID(), offlineBendingPlayer);

		return offlineBendingPlayer;
	}

	/**
	 * Uncaches this instance of an Offline BendingPlayer.
	 */
	public void uncache() {
		if (this.player.isOnline() || this instanceof BendingPlayer) return;

		long remaining = (this.lastAccessed + this.uncacheTime) - System.currentTimeMillis();

		if (remaining >= 500) { //If there is at least half a second to go, delay the uncache
			if (this.uncache != null) this.uncache.cancel(); //Cancel existing task
			this.uncache = Bukkit.getScheduler().runTaskLater(ProjectKorra.plugin, this::uncache, remaining / 50);
			return;
		}

		PLAYERS.remove(this.player.getUniqueId());
		ONLINE_PLAYERS.remove(this.player.getUniqueId());
	}

	/**
	 * Uncaches this instance of an offline BendingPlayer
	 * @param time The amount of milliseconds to wait before uncaching
	 */
	public void uncacheAfter(long time) {
		this.uncacheTime = time;
		this.lastAccessed = System.currentTimeMillis();
		uncache();
	}
}
