package com.projectkorra.projectkorra;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.ability.AvatarAbility;
import com.projectkorra.projectkorra.ability.ChiAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.avatar.AvatarState;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.command.CooldownCommand;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.earthbending.metal.MetalClips;
import com.projectkorra.projectkorra.event.PlayerBindChangeEvent;
import com.projectkorra.projectkorra.event.PlayerStanceChangeEvent;
import com.projectkorra.projectkorra.hooks.CanBendHook;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.storage.internal.InternalPlayerDataStore;
import com.projectkorra.projectkorra.util.ChatUtil;
import com.projectkorra.projectkorra.util.Cooldown;
import com.projectkorra.projectkorra.util.MovementHandler;
import com.projectkorra.projectkorra.util.logging.PkLang;
import com.projectkorra.projectkorra.persistence.external.BendingPlayerMutationOperation;
import com.projectkorra.projectkorra.persistence.external.CooldownPersistence;
import com.projectkorra.projectkorra.persistence.external.FlushReason;
import com.projectkorra.projectkorra.persistence.external.MutationResult;
import com.projectkorra.projectkorra.persistence.external.MutationSource;
import com.projectkorra.projectkorra.waterbending.blood.Bloodbending;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Bender {
	public static final Map<UUID, Bender> CACHE = new ConcurrentHashMap<>();
	protected static Map<JavaPlugin, CanBendHook> HOOKS = new HashMap<>();

	public static @Nullable Bender byId(UUID uuid) {
		return CACHE.get(uuid);
	}

	public static @Nullable Bender get(LivingEntity entity) {
		if (entity instanceof Player player && player.isOnline())
			return BendingPlayer.getBendingPlayer(player);
		return byId(entity.getUniqueId());
	}

	public static Bender of(LivingEntity entity) {
		if (entity instanceof Player player && player.isOnline())
			return BendingPlayer.getBendingPlayer(player);
		return CACHE.computeIfAbsent(entity.getUniqueId(), Bender::new);
	}

	protected UUID uuid;
	protected boolean permaRemoved;
	protected boolean toggled;
	protected boolean allPassivesToggled;
	@Setter protected boolean sneaking;
	protected final List<Element> elements = new ArrayList<>();
	protected final List<Element.SubElement> subelements = new ArrayList<>();
	protected HashMap<Integer, String> abilities = new HashMap<>();
	protected final Map<String, Cooldown> cooldowns = new HashMap<>();
	protected final Set<Element> toggledElements = new HashSet<>();
	protected final Set<Element> toggledPassives = new HashSet<>();
	@Setter protected ChiAbility stance;
	protected boolean tremorSense;
	protected boolean illumination;
	protected boolean chiBlocked;
	@Setter protected boolean queueAirBlastStop;
	protected long slowTime;

	public Bender(UUID uuid) {
		this.uuid = uuid;
		this.toggled = true;
		this.allPassivesToggled = true;
		this.tremorSense = true;
		this.illumination = true;
		this.chiBlocked = false;
	}

	public Optional<OfflineBendingPlayer> asOfflinePlayer() {
		return Optional.ofNullable(this instanceof OfflineBendingPlayer obp ? obp : null);
	}

	public Optional<BendingPlayer> asPlayer() {
		return Optional.ofNullable(this instanceof BendingPlayer bp ? bp : null);
	}

	public Optional<LivingEntity> asEntity() {
		return Optional.ofNullable(Bukkit.getEntity(uuid)).filter(e -> e instanceof LivingEntity).map(e -> (LivingEntity) e);
	}

	public Optional<Player> asBukkitPlayer() {
		return Optional.ofNullable(Bukkit.getPlayer(uuid));
	}

	public void setUUID(UUID uuid) {
		var old = this.uuid;
		if (old.equals(uuid)) return;
		CACHE.remove(old);
		this.uuid = uuid;
		CACHE.put(uuid, this);
	}

	public boolean isSneaking() {
		return asBukkitPlayer().map(Player::isSneaking).orElse(sneaking);
	}

	public boolean hasUnlocked(String abilityName) {
		return true;
	}

	public boolean canBend() {
		return !this.toggled && !this.permaRemoved;
	}

	public boolean canBend(final CoreAbility ability) {
		return this.canBend(ability, false, false);
	}

	public boolean canBendIgnoreBinds(final CoreAbility ability) {
		return this.canBend(ability, true, false);
	}

	public boolean canBendIgnoreBindsCooldowns(final CoreAbility ability) {
		return this.canBend(ability, true, true);
	}

	public boolean canBendIgnoreCooldowns(final CoreAbility ability) {
		return this.canBend(ability, false, true);
	}

	protected boolean canBend(CoreAbility ability, boolean ignoreBinds, boolean ignoreCooldowns) {
		if (ability == null) return false;
		var entity = asEntity().orElse(null);
		if (entity == null) return false; // all bending currently requires an entity
		boolean debug = false;
		if (debug)
			PkLang.info("canBend: entity=" + entity.getName() + "ability=" + ability.getName());

		// loop through all hooks and test them
		for (JavaPlugin plugin : HOOKS.keySet()) {
			var hook = HOOKS.get(plugin);
			if (!hook.canBend(this, ability, ignoreBinds, ignoreCooldowns).orElse(false))
				return false;
		}
		if (debug)
			PkLang.info("canBend: passed all hooks");

		if (entity.isDead() || !entity.isValid()) return false;
		if (debug)
			PkLang.info("canBend: entity is alive and valid");
		if (!canBind(ability)) return false;
		if (debug)
			PkLang.info("canBend: can bind ability");
		var disabledWorlds = ConfigManager.getConfig().getStringList("Properties.DisabledWorlds");
		if (disabledWorlds.contains(entity.getWorld().getName())) return false;
		if (debug)
			PkLang.info("canBend: world is not disabled");
		try {
			if (Optional.ofNullable(ability.getLocation()).map(l1 -> !ability.getCaster().getWorld().equals(l1.getWorld())).orElse(false)) return false;
		} catch (Exception e) {
			// ignore. ability.getLocation() may throw an exception due to legacy player-required abilities
		}
		if (debug)
			PkLang.info("canBend: ability location is valid");
		if (!ignoreBinds && !boundAbilityMatches(ability.getName())) return false;
		if (debug)
			PkLang.info("canBend: bound ability matches");
		if (!ignoreCooldowns && isOnCooldown(ability)) return false;
		if (debug)
			PkLang.info("canBend: not on cooldown");
		if (Commands.isToggledForAll || !isToggled() || !isElementToggled(ability.getElement())) return false;
		if (debug)
			PkLang.info("canBend: bending is toggled on");
		return !RegionProtection.isRegionProtected(entity, entity.getLocation(), ability);
	}

	public boolean canBind(CoreAbility ability) {
		if (ability == null) return false;
		if (!ability.isEnabled()) return false;
		if (!hasElement(ability.getElement()) && !(ability instanceof AvatarAbility avAbil && !avAbil.requireAvatar())) return false;
		if (ability.getElement() instanceof Element.SubElement sub) {
			if (sub instanceof Element.MultiSubElement multiSub) {
				if (Arrays.stream(multiSub.getParentElements()).noneMatch(this::hasElement)) return false;
			} else if (!hasElement(sub.getParentElement())) return false;
			return hasSubElement(sub);
		}
		return true;
	}

	public void bindAbility(final String ability, final int slot) {
		var player = Bukkit.getPlayer(uuid);
		if (player != null && MultiAbilityManager.playerAbilities.containsKey(player)) {
			ChatUtil.sendBrandingMessage(player, ChatColor.RED + ConfigManager.languageConfig.get().getString("Commands.Bind.CantEditBinds"));
			return;
		}

		CoreAbility coreAbil = CoreAbility.getAbility(ability);
		if (coreAbil == null) return;
		String name = coreAbil.getName();

		if (player != null) {
			PlayerBindChangeEvent event = new PlayerBindChangeEvent(player, name, slot, true, false);
			ProjectKorra.plugin.getServer().getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				return;
			}
		}

		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.SetBind(slot, name), MutationSource.legacy("bindAbility"))
					.thenAccept(result -> {
						if (player == null) return;
						if (result.accepted()) ChatUtil.sendBrandingMessage(player, coreAbil.getElement().getColor() + ConfigManager.languageConfig.get().getString("Commands.Bind.SuccessfullyBound").replace("{ability}", name).replace("{slot}", String.valueOf(slot)));
						else ChatUtil.sendBrandingMessage(player, ChatColor.RED + "The external player-data provider rejected the bind change.");
					});
			return;
		}
		setAbility(slot, name);

		if (player != null) ChatUtil.sendBrandingMessage(player, coreAbil.getElement().getColor() + ConfigManager.languageConfig.get().getString("Commands.Bind.SuccessfullyBound").replace("{ability}", name).replace("{slot}", String.valueOf(slot)));
	}

	public void clearAbility(final int slot) {
		this.setAbility(slot, null);
	}

	public void clearAbilities() {
		for (int i = 1; i <= 9; i++) {
			this.setAbility(i, null);
		}
	}

	public void setAbility(final int slot, final String ability) {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.SetBind(slot, ability), MutationSource.legacy("setAbility"));
			return;
		}
		this.abilities.put(slot, ability);
		this.saveAbility(ability, slot);
	}

	/**
	 * Sets the {@link Bender}'s abilities. This method also saves the
	 * abilities to the database.
	 *
	 * @param abilities The abilities to set/save
	 */
	public void setAbilities(@NotNull final HashMap<Integer, String> abilities) {
		if (this.abilities.equals(abilities)) return;
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.ReplaceBinds(abilities), MutationSource.legacy("setAbilities"));
			return;
		}

		this.abilities = abilities;

		InternalPlayerDataStore.writeAbilitiesLegacy(this.uuid, abilities);
	}

	/** Saves a snapshot of all ability slots and reports completion. */
	public CompletableFuture<Void> saveAbilitiesAsync() {
		if (ExternalPersistenceCoordinator.isExternalMode()) { ExternalPersistenceCoordinator.warnLegacySave(this, "saveAbilitiesAsync"); return ExternalPersistenceCoordinator.flush(this, FlushReason.LEGACY_SAVE).toCompletableFuture(); }
		return InternalPlayerDataStore.writeAbilities(this.uuid, new HashMap<>(this.abilities));
	}

	/**
	 * Save the bound ability in the slot to the database
	 * @param ability The ability to save
	 * @param slot The slot we are saving
	 */
	public void saveAbility(final String ability, final int slot) {
		// Temp code to block modifications of binds, Should be replaced when bind event is added.
		if (this instanceof BendingPlayer bp && MultiAbilityManager.playerAbilities.containsKey(bp.getPlayer())) {
			return;
		}

		if (ExternalPersistenceCoordinator.isExternalMode()) {
			ExternalPersistenceCoordinator.warnLegacySave(this, "saveAbility");
			ExternalPersistenceCoordinator.flush(this, FlushReason.LEGACY_SAVE);
			return;
		}
		InternalPlayerDataStore.writeAbilityLegacy(this.uuid, slot, this.abilities.get(slot));
	}

	/** Saves the supplied slot snapshot and reports completion. */
	public CompletableFuture<Void> saveAbilityAsync(final String ability, final int slot) {
		if (slot < 1 || slot > 9) return CompletableFuture.failedFuture(new IllegalArgumentException("slot must be between 1 and 9"));
		if (ExternalPersistenceCoordinator.isExternalMode()) { ExternalPersistenceCoordinator.warnLegacySave(this, "saveAbilityAsync"); return ExternalPersistenceCoordinator.flush(this, FlushReason.LEGACY_SAVE).toCompletableFuture(); }
		if (this instanceof BendingPlayer bp && MultiAbilityManager.playerAbilities.containsKey(bp.getPlayer())) return CompletableFuture.completedFuture(null);
		return InternalPlayerDataStore.writeAbility(this.uuid, slot, ability);
	}

	public String getBoundAbilityName() {
		return "";
	}

	public CoreAbility getBoundAbility() {
		return CoreAbility.getAbility(this.getBoundAbilityName());
	}

	public boolean boundAbilityMatches(final String abilityName) {
		return true;
	}

	public List<Element.SubElement> getSubElements() {
		return ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)
				? List.copyOf(this.subelements) : this.subelements;
	}

	public List<Element> getElements() {
		return ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)
				? List.copyOf(this.elements) : this.elements;
	}

	public HashMap<Integer, String> getAbilities() {
		return ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)
				? new HashMap<>(this.abilities) : this.abilities;
	}

	public Map<String, Cooldown> getCooldowns() {
		return ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)
				? Map.copyOf(this.cooldowns) : this.cooldowns;
	}

	public Set<Element> getToggledElements() {
		return ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)
				? Set.copyOf(this.toggledElements) : this.toggledElements;
	}

	public UUID getUUID() {
		return this.uuid;
	}

	/**
	 * Sets the {@link BendingPlayer}'s element. If the player had elements
	 * before they will be overwritten.
	 *
	 * @param element The element to set
	 */
	public void setElement(@NotNull final Element element) {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.ReplaceElements(List.of(element.getName())), MutationSource.legacy("setElement"));
			return;
		}
		this.elements.clear();
		this.elements.add(element);
	}

	public void addElement(final Element element) {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.AddElement(element.getName()), MutationSource.legacy("addElement"));
			return;
		}
		this.elements.add(element);
	}

	public void addSubElement(final Element.SubElement subelement) {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.AddSubelement(subelement.getName()), MutationSource.legacy("addSubElement"));
			return;
		}
		this.subelements.add(subelement);
	}

	public void removeElement(final Element element) {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.RemoveElement(element.getName()), MutationSource.legacy("removeElement"));
			return;
		}
		this.elements.remove(element);
	}

	public void removeSubElement(final Element.SubElement subelement) {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.RemoveSubelement(subelement.getName()), MutationSource.legacy("removeSubElement"));
			return;
		}
		this.subelements.remove(subelement);
	}

	public boolean hasElement(@NotNull final Element element) {
		if (element == Element.AVATAR) {
			return asBukkitPlayer().map(p -> p.hasPermission("bending.avatar")).orElse(false);
		} else if (!(element instanceof Element.SubElement)) {
			return this.elements.contains(element);
		} else {
			return this.hasSubElement((Element.SubElement) element);
		}
	}

	public boolean hasSubElement(@NotNull final Element.SubElement sub) {
		return this.subelements.contains(sub);
	}

	public boolean canUseSubElement(final Element.SubElement sub) {
		return this.subelements.contains(sub);
	}

	public boolean isElementToggled(final Element element) {
		return !this.toggledElements.contains(element);
	}

	public boolean isPassiveToggled(final Element element) {
		return !this.toggledPassives.contains(element);
	}

	public boolean isToggledPassives() {
		return this.allPassivesToggled;
	}

	public long getCooldown(final String ability) {
		if (this.cooldowns.containsKey(ability)) {
			return this.cooldowns.get(ability).getCooldown();
		}

		return -1;
	}

	public void removeCooldown(final String ability) {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			final Cooldown current = this.cooldowns.get(ability);
			if (current != null && current.isDatabase()) {
				this.requestMutationAsync(new BendingPlayerMutationOperation.RemovePersistentCooldown(ability), MutationSource.legacy("removeCooldown"));
				return;
			}
		}
		this.cooldowns.remove(ability);
	}

	public void removeCooldown(@NotNull final CoreAbility ability) {
		this.removeCooldown(ability.getName());
	}

	protected void removeOldCooldowns() {
		final List<String> expiredPersistent = new ArrayList<>();
		this.cooldowns.entrySet().removeIf(entry -> {
			final boolean expired = System.currentTimeMillis() >= entry.getValue().getCooldown();
			if (expired && entry.getValue().isDatabase()) expiredPersistent.add(entry.getKey());
			return expired;
		});
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			for (final String key : expiredPersistent) this.requestMutationAsync(new BendingPlayerMutationOperation.RemovePersistentCooldown(key), MutationSource.projectKorra("cooldown-expired"));
		}
	}

	public boolean isOnCooldown(@NotNull final Ability ability) {
		return this.isOnCooldown(ability.getName());
	}

	public boolean isOnCooldown(final String ability) {
		return this.cooldowns.containsKey(ability);
	}

	public void addCooldown(final Ability ability, final long cooldown, final boolean database) {
		this.addCooldown(ability.getName(), cooldown, database);
	}

	public void addCooldown(final Ability ability, final boolean database) {
		this.addCooldown(ability.getName(), ability.getCooldown(), database);
	}

	public void addCooldown(final Ability ability, final long cooldown) {
		this.addCooldown(ability, cooldown, false);
	}

	public void addCooldown(final Ability ability) {
		this.addCooldown(ability, false);
	}

	public void addCooldown(final String ability, final long cooldown) {
		this.addCooldown(ability, cooldown, false);
	}

	public void addCooldown(final String ability, final long cooldown, final boolean database) {
		if (cooldown <= 0) {
			return;
		}

		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			final CooldownPersistence persistence = ExternalPersistenceCoordinator.classifyCooldown(this, ability, database);
			if (persistence.isPersistent()) {
				this.requestMutationAsync(new BendingPlayerMutationOperation.SetPersistentCooldown(ability, cooldown + System.currentTimeMillis(), persistence), MutationSource.legacy("addCooldown"));
				return;
			}
		}
		this.cooldowns.put(ability, new Cooldown(cooldown + System.currentTimeMillis(), database && !ExternalPersistenceCoordinator.isExternalMode()));

		CooldownCommand.addCooldownType(ability);
	}

	/**
	 * Commits cooldowns to the database
	 */
	private void saveCooldownsForce() {
		if (ExternalPersistenceCoordinator.isExternalMode()) return;
		InternalPlayerDataStore.saveCooldowns(this.uuid, this.cooldowns);
	}

	public void saveCooldowns(boolean async) {
		if (ExternalPersistenceCoordinator.isExternalMode()) {
			ExternalPersistenceCoordinator.warnLegacySave(this, "saveCooldowns");
			ExternalPersistenceCoordinator.flush(this, FlushReason.LEGACY_SAVE);
			return;
		}
		if (async) Bukkit.getScheduler().runTaskAsynchronously(ProjectKorra.plugin, this::saveCooldownsForce);
		else this.saveCooldownsForce();
	}

	public void saveCooldowns() {
		this.saveCooldowns(true);
	}

	/** Saves cooldowns and reports when the database work has finished. */
	public CompletableFuture<Void> saveCooldownsAsync() {
		if (ExternalPersistenceCoordinator.isExternalMode()) { ExternalPersistenceCoordinator.warnLegacySave(this, "saveCooldownsAsync"); return ExternalPersistenceCoordinator.flush(this, FlushReason.LEGACY_SAVE).toCompletableFuture(); }
		final CompletableFuture<Void> future = new CompletableFuture<>();
		Bukkit.getScheduler().runTaskAsynchronously(ProjectKorra.plugin, () -> {
			try { this.saveCooldownsForce(); future.complete(null); }
			catch (final Throwable error) { future.completeExceptionally(error); }
		});
		return future;
	}

	/**
	 * Sets the permanent removed state of the {@link BendingPlayer}.
	 *
	 * @param permaRemoved If they should be permaremoved
	 */
	public void setPermaRemoved(final boolean permaRemoved) {
		if (ExternalPersistenceCoordinator.isExternalMode()) {
			PkLang.warning("Ignoring ProjectKorra permaremove mutation in external player-data mode; use the external authority's administrative tools");
			return;
		}
		this.permaRemoved = permaRemoved;
		InternalPlayerDataStore.setPermaRemoved(this.uuid, permaRemoved);
	}

	public void toggleBending() {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.SetBendingEnabled(!this.toggled), MutationSource.legacy("toggleBending"));
			return;
		}
		this.toggled = !this.toggled;
	}

	public void toggleAllPassives() {
		this.allPassivesToggled = !this.allPassivesToggled;
	}

	public void toggleElement(final Element element) {
		if (ExternalPersistenceCoordinator.isExternalMode() && !ExternalPersistenceCoordinator.isProjectionGuardActive(this.uuid)) {
			this.requestMutationAsync(new BendingPlayerMutationOperation.SetElementEnabled(element.getName(), !this.isElementToggled(element)), MutationSource.legacy("toggleElement"));
			return;
		}
		if (this.toggledElements.contains(element)) {
			this.toggledElements.remove(element);
		} else {
			this.toggledElements.add(element);
		}
	}

	public CompletionStage<MutationResult> requestMutationAsync(final BendingPlayerMutationOperation operation, final MutationSource source) {
		return ExternalPersistenceCoordinator.mutate(this, operation, source);
	}

	/** Replaces transient runtime bindings without persistence; intended for ProjectKorra multi-abilities and snapshot projection only. */
	public void replaceRuntimeAbilities(final Map<Integer, String> runtimeAbilities) {
		this.abilities.clear();
		this.abilities.putAll(runtimeAbilities);
	}

	public void setRuntimeCooldown(final String key, final long expiration) {
		if (expiration <= System.currentTimeMillis()) this.cooldowns.remove(key);
		else this.cooldowns.put(key, new Cooldown(expiration, false));
	}

	public void removeRuntimeCooldown(final String key) { this.cooldowns.remove(key); }
	public void clearRuntimeCooldowns() { this.cooldowns.entrySet().removeIf(entry -> !entry.getValue().isDatabase()); }

	public void togglePassive(final Element element) {
		if (this.toggledPassives.contains(element)) {
			this.toggledPassives.remove(element);
		} else {
			this.toggledPassives.add(element);
		}
	}

	public boolean canBloodbend() {
		return this.subelements.contains(Element.BLOOD);
	}

	public boolean canBloodbendAtAnytime() {
		return this.subelements.contains(Element.DAY_BLOOD);
	}

	public boolean canCombustionbend() {
		return this.subelements.contains(Element.COMBUSTION);
	}

	public boolean canIcebend() {
		return this.subelements.contains(Element.ICE);
	}

	public boolean canLavabend() {
		return this.subelements.contains(Element.LAVA);
	}

	public boolean canLightningbend() {
		return this.subelements.contains(Element.LIGHTNING);
	}

	public boolean canUseBlueFire() {
		return this.subelements.contains(Element.BLUE_FIRE);
	}

	public boolean canMetalbend() {
		return this.subelements.contains(Element.METAL);
	}

	public boolean canPlantbend() {
		return this.subelements.contains(Element.PLANT);
	}

	public boolean canSandbend() {
		return this.subelements.contains(Element.SAND);
	}

	public boolean canUseFlight() {
		return this.subelements.contains(Element.FLIGHT);
	}

	public boolean canUseSpiritualProjection() {
		return this.subelements.contains(Element.SPIRITUAL);
	}

	public boolean canUseSuffocation() {
		return this.subelements.contains(Element.SUFFOCATION);
	}

	public boolean canWaterHeal() {
		return this.subelements.contains(Element.HEALING);
	}

	/**
	 * Checks to see if a Bender is affected by BloodBending.
	 *
	 * @return true If {@link #isChiBlocked()} is true <br />
	 *         false If player is BloodBender and Bending is toggled on, or if
	 *         player is in AvatarState
	 */
	public boolean canBeBloodbent() {
		if (this.isAvatarState()) {
			return this.isChiBlocked();
		}

		return !this.canBendIgnoreBindsCooldowns(CoreAbility.getAbility("Bloodbending")) || !this.isToggled();
	}

	/**
	 * Checks to see if {@link BendingPlayer} can be slowed.
	 *
	 * @return true If player can be slowed
	 */
	public boolean canBeSlowed() {
		return (System.currentTimeMillis() > this.slowTime);
	}

	public boolean isAvatarState() {
		return asEntity().map(e -> CoreAbility.hasAbility(e, AvatarState.class)).orElse(false);
	}

	public boolean isBloodbent() {
		return asEntity().map(Bloodbending::isBloodbent).orElse(false);
	}

	public boolean isControlledByMetalClips() {
		return asEntity().map(MetalClips::isControlled).orElse(false);
	}

	public boolean isParalyzed() {
		return asEntity().map(MovementHandler::isStopped).orElse(false);
	}

	/**
	 * Sets the {@link BendingPlayer}'s chi blocked to false.
	 */
	public void unblockChi() {
		this.chiBlocked = false;
	}

	/**
	 * Sets chiBlocked to true.
	 */
	public void blockChi() {
		this.chiBlocked = true;
	}

	/**
	 * Checks if the {@link BendingPlayer} is tremor sensing.
	 *
	 * @return true if player is tremor sensing
	 */
	public boolean isTremorSensing() {
		return this.tremorSense;
	}

	/**
	 * Checks if the {@link BendingPlayer} is using illumination.
	 *
	 * @return true if player is using illumination
	 */
	public boolean isIlluminating() {
		return this.illumination;
	}

	/**
	 * Toggles the {@link BendingPlayer}'s tremor sensing.
	 */
	public void toggleTremorSense() {
		this.tremorSense = !this.tremorSense;
	}

	/**
	 * Toggles the {@link BendingPlayer}'s illumination.
	 */
	public void toggleIllumination() {
		this.illumination = !this.illumination;
	}

	/**
	 * Slow the {@link BendingPlayer} for a certain amount of time.
	 *
	 * @param cooldown The amount of time to slow.
	 */
	public void slow(final long cooldown) {
		this.slowTime = System.currentTimeMillis() + cooldown;
	}
}
