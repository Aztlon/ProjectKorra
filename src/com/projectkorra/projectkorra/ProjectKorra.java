package com.projectkorra.projectkorra;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.util.CollisionInitializer;
import com.projectkorra.projectkorra.ability.util.CollisionManager;
import com.projectkorra.projectkorra.ability.util.ComboManager;
import com.projectkorra.projectkorra.ability.util.MultiAbilityManager;
import com.projectkorra.projectkorra.airbending.util.AirbendingManager;
import com.projectkorra.projectkorra.board.BendingBoardManager;
import com.projectkorra.projectkorra.chiblocking.util.ChiblockingManager;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.earthbending.util.EarthbendingManager;
import com.projectkorra.projectkorra.firebending.util.FirebendingManager;
import com.projectkorra.projectkorra.hooks.PlaceholderAPIHook;
import com.projectkorra.projectkorra.hooks.WorldGuardFlag;
import com.projectkorra.projectkorra.object.Preset;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.storage.DBConnection;
import com.projectkorra.projectkorra.phasing.PhasedBlockVisibilityManager;
import com.projectkorra.projectkorra.phasing.PhasedIntegrationManager;
import com.projectkorra.projectkorra.persistence.external.ExternalBendingPlayerPersistence;
import com.projectkorra.projectkorra.persistence.external.FlushReason;
import com.projectkorra.projectkorra.persistence.external.PlayerDataMode;
import com.projectkorra.projectkorra.util.Metrics;
import com.projectkorra.projectkorra.util.RevertChecker;
import com.projectkorra.projectkorra.util.StatisticsManager;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.util.Updater;
import com.projectkorra.projectkorra.util.logging.PkLang;
import com.projectkorra.projectkorra.util.particles.ParticleCompatibilityService;
import com.projectkorra.projectkorra.waterbending.util.WaterbendingManager;

public class ProjectKorra extends JavaPlugin {

	public static ProjectKorra plugin;
	public static Logger log;
	public static CollisionManager collisionManager;
	public static CollisionInitializer collisionInitializer;
	public static long time_step = 1;
	public Updater updater;
	BukkitTask revertChecker;
//	private static TimingManager timingManager;
	private static PlaceholderAPIHook papiHook;

	@Override
	public void onEnable() {
		plugin = this;
		ProjectKorra.log = this.getLogger();
		new PkLang(this);

//		timingManager = TimingManager.of(this);

		new ConfigManager();
		ExternalBendingPlayerPersistence.bootstrap(PlayerDataMode.parse(ConfigManager.getConfig().getString("Storage.PlayerDataMode")));
		PhasedIntegrationManager.reloadFromConfig();
		PhasedBlockVisibilityManager.reloadFromConfig();
		ParticleCompatibilityService.reload(this);
		new GeneralMethods(this);
		final boolean checkUpdateOnStartup = ConfigManager.getConfig().getBoolean("Properties.UpdateChecker");
		this.updater = new Updater(this, "https://projectkorra.com/forum/resources/projectkorra-core.1/", checkUpdateOnStartup);
		new Commands(this);
		new MultiAbilityManager();
		new ComboManager();
		new RegionProtection();
		collisionManager = new CollisionManager();
		collisionInitializer = new CollisionInitializer(collisionManager);
		CoreAbility.registerAbilities();
		collisionInitializer.initializeDefaultCollisions();
		collisionManager.startCollisionDetection();

		Preset.loadExternalPresets();

		DBConnection.init();
		if (!DBConnection.isOpen()) {
			return;
		}

		Manager.startup();
		BendingBoardManager.setup();

		this.getServer().getPluginManager().registerEvents(new PKListener(this), this);
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new BendingManager(), 0, 1);
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new AirbendingManager(this), 0, 1);
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new WaterbendingManager(this), 0, 1);
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new EarthbendingManager(this), 0, 1);
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new FirebendingManager(this), 0, 1);
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new ChiblockingManager(this), 0, 1);
		this.revertChecker = this.getServer().getScheduler().runTaskTimerAsynchronously(this, new RevertChecker(this), 0, 200);

		TempBlock.startReversion();

		final Runnable loadOnlinePlayers = () -> {
			ExternalBendingPlayerPersistence.closeRegistration();
			for (final Player player : Bukkit.getOnlinePlayers()) {
				PKListener.getJumpStatistics().put(player, player.getStatistic(Statistic.JUMP));
				OfflineBendingPlayer.loadAsync(player.getUniqueId(), true);
				Manager.getManager(StatisticsManager.class).load(player.getUniqueId());
			}
		};
		if (ExternalBendingPlayerPersistence.isExternalMode()) Bukkit.getScheduler().runTask(this, loadOnlinePlayers);
		else loadOnlinePlayers.run();

		final Metrics metrics = new Metrics(this);
		metrics.addCustomChart(new Metrics.AdvancedPie("Elements") {

			@Override
			public HashMap<String, Integer> getValues(final HashMap<String, Integer> valueMap) {
				for (final Element element : Element.getMainElements()) {
					valueMap.put(element.getName(), this.getPlayersWithElement(element));
				}

				return valueMap;
			}

			private int getPlayersWithElement(final Element element) {
				int counter = 0;
				for (final Player player : Bukkit.getOnlinePlayers()) {
					final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
					if (bPlayer != null && bPlayer.hasElement(element)) {
						counter++;
					}
				}

				return counter;
			}
		});

		final double cacheTime = ConfigManager.getConfig().getDouble("Properties.RegionProtection.CacheBlockTime");

		RegionProtection.startCleanCacheTask(cacheTime);

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			papiHook = new PlaceholderAPIHook(this);
			papiHook.register();
		}
	}

	@Override
	public void onDisable() {
		ParticleCompatibilityService.shutdown();
		PhasedBlockVisibilityManager.shutdown();
		this.revertChecker.cancel();
		GeneralMethods.stopBending();
		final List<CompletableFuture<Void>> externalFlushes = new ArrayList<>();
		for (final Player player : this.getServer().getOnlinePlayers()) {
			if (isStatisticsEnabled()) {
				Manager.getManager(StatisticsManager.class).save(player.getUniqueId(), false);
			}
			final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
			if (bPlayer != null && ExternalBendingPlayerPersistence.isExternalMode()) {
				externalFlushes.add(ExternalPersistenceCoordinator.flush(bPlayer, FlushReason.PLUGIN_SHUTDOWN).toCompletableFuture());
			} else if (bPlayer != null && isDatabaseCooldownsEnabled()) {
				bPlayer.saveCooldowns(false);
			}
		}
		if (!externalFlushes.isEmpty()) {
			try {
				CompletableFuture.allOf(externalFlushes.toArray(CompletableFuture[]::new))
						.get(Math.max(1L, ConfigManager.getConfig().getLong("Storage.External.FlushTimeoutMillis", 5000L)), TimeUnit.MILLISECONDS);
			} catch (final Exception error) {
				this.getLogger().warning("External bending-player flush did not complete during shutdown: " + error.getMessage());
			}
		}
		Manager.shutdown();
		if (DBConnection.isOpen()) {
			DBConnection.sql.close();
		}

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			papiHook.unregister();
		}
	}

	@Override
	public void onLoad() {
		if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
			WorldGuardFlag.registerBendingWorldGuardFlag();
		}
	}

	public static CollisionManager getCollisionManager() {
		return collisionManager;
	}

	public static void setCollisionManager(final CollisionManager collisionManager) {
		ProjectKorra.collisionManager = collisionManager;
	}

	public static CollisionInitializer getCollisionInitializer() {
		return collisionInitializer;
	}

	public static void setCollisionInitializer(final CollisionInitializer collisionInitializer) {
		ProjectKorra.collisionInitializer = collisionInitializer;
	}

	public static boolean isStatisticsEnabled() {
		return ConfigManager.getConfig().getBoolean("Properties.Statistics");
	}

	public static boolean isDatabaseCooldownsEnabled() {
		return ConfigManager.getConfig().getBoolean("Properties.DatabaseCooldowns");
	}

//	public static MCTiming timing(final String name) {
//		return timingManager.of(name);
//	}
}
