package com.projectkorra.projectkorra.phasing;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.configuration.ConfigManager;

public final class PhasedBlockVisibilityManager {

	private static final Map<BlockKey, OverlayEntry> OVERLAYS = new ConcurrentHashMap<>();

	private static volatile boolean enabled;
	private static volatile int refreshIntervalTicks = 10;
	private static volatile BukkitTask refreshTask;

	private PhasedBlockVisibilityManager() {
	}

	public static void reloadFromConfig() {
		final FileConfiguration config = ConfigManager.getConfig();
		enabled = config.getBoolean("Properties.PhasedIntegration.Blocks.UseViewerOverlays", false);
		refreshIntervalTicks = Math.max(1, config.getInt("Properties.PhasedIntegration.Blocks.RefreshIntervalTicks", 10));

		if (enabled) {
			startRefreshTask();
		} else {
			shutdown();
		}
	}

	public static void shutdown() {
		if (refreshTask != null) {
			refreshTask.cancel();
			refreshTask = null;
		}
		clearAll();
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static boolean shouldUseViewerOverlay(@Nullable final Ability ability, @Nullable final Location location) {
		return shouldUseViewerOverlay(PhasedBlockSource.fromAbility(ability), location);
	}

	public static boolean shouldUseViewerOverlay(@Nullable final PhasedBlockSource source, @Nullable final Location location) {
		if (!enabled || source == null || source.isEmpty() || location == null || location.getWorld() == null) {
			return false;
		}

		final GateRequest request = source.request(GateStage.BLOCK, location, null);
		return !PhasedIntegrationManager.shouldViewerObserve(request);
	}

	public static void applyOverlay(final Location location, final BlockData blockData, @Nullable final Ability ability) {
		applyOverlay(location, blockData, PhasedBlockSource.fromAbility(ability));
	}

	public static void applyOverlay(final Location location, final BlockData blockData, @Nullable final PhasedBlockSource source) {
		if (!enabled || location == null || location.getWorld() == null || blockData == null) {
			return;
		}

		final BlockKey key = BlockKey.of(location);
		OVERLAYS.put(key, new OverlayEntry(location, blockData, source));
		refreshOverlay(key);
	}

	public static void clearOverlay(final Location location) {
		if (location == null || location.getWorld() == null) {
			return;
		}

		final BlockKey key = BlockKey.of(location);
		if (OVERLAYS.remove(key) == null) {
			return;
		}
		sendRealBlockToWorld(location);
	}

	public static void clearAll() {
		if (OVERLAYS.isEmpty()) {
			return;
		}

		for (final OverlayEntry entry : OVERLAYS.values()) {
			sendRealBlockToWorld(entry.location);
		}
		OVERLAYS.clear();
	}

	public static void sendScopedBlockChange(@Nullable final Ability ability, final Player viewer, final Location location, final BlockData allowedData) {
		sendScopedBlockChange(PhasedBlockSource.fromAbility(ability), viewer, location, allowedData);
	}

	public static void sendScopedBlockChange(@Nullable final PhasedBlockSource source, final Player viewer, final Location location, final BlockData allowedData) {
		if (viewer == null || location == null || location.getWorld() == null || allowedData == null) {
			return;
		}

		if (!enabled) {
			viewer.sendBlockChange(location, allowedData);
			return;
		}

		final GateRequest request = source == null ? PhasedBlockSource.none().request(GateStage.BLOCK, location, viewer.getUniqueId()) : source.request(GateStage.BLOCK, location, viewer.getUniqueId());
		if (PhasedIntegrationManager.shouldViewerObserve(request)) {
			viewer.sendBlockChange(location, allowedData);
		} else {
			viewer.sendBlockChange(location, location.getBlock().getBlockData());
		}
	}

	private static void refreshOverlay(final BlockKey key) {
		if (!enabled) {
			return;
		}

		final OverlayEntry entry = OVERLAYS.get(key);
		if (entry == null) {
			return;
		}

		final World world = entry.location.getWorld();
		if (world == null) {
			OVERLAYS.remove(key);
			return;
		}

		final Block block = entry.location.getBlock();
		final BlockData realData = block.getBlockData();
		for (final Player viewer : world.getPlayers()) {
			final GateRequest request = entry.source.request(GateStage.BLOCK, entry.location, viewer.getUniqueId());
			if (PhasedIntegrationManager.shouldViewerObserve(request)) {
				viewer.sendBlockChange(entry.location, entry.blockData);
			} else {
				viewer.sendBlockChange(entry.location, realData);
			}
		}
	}

	private static void refreshAll() {
		for (final BlockKey key : OVERLAYS.keySet()) {
			refreshOverlay(key);
		}
	}

	private static void startRefreshTask() {
		if (refreshTask != null) {
			refreshTask.cancel();
		}

		refreshTask = Bukkit.getScheduler().runTaskTimer(ProjectKorra.plugin, PhasedBlockVisibilityManager::refreshAll, 1L, refreshIntervalTicks);
	}

	private static void sendRealBlockToWorld(final Location location) {
		if (location == null || location.getWorld() == null) {
			return;
		}

		final BlockData realData = location.getBlock().getBlockData();
		for (final Player viewer : location.getWorld().getPlayers()) {
			viewer.sendBlockChange(location, realData);
		}
	}

	private static final class OverlayEntry {
		private final Location location;
		private final BlockData blockData;
		private final PhasedBlockSource source;

		private OverlayEntry(final Location location, final BlockData blockData, @Nullable final PhasedBlockSource source) {
			this.location = location.clone();
			this.blockData = blockData.clone();
			this.source = source == null ? PhasedBlockSource.none() : source;
		}
	}

	private static final class BlockKey {
		private final UUID worldId;
		private final int x;
		private final int y;
		private final int z;

		private BlockKey(final UUID worldId, final int x, final int y, final int z) {
			this.worldId = worldId;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		private static BlockKey of(final Location location) {
			return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
		}

		@Override
		public boolean equals(final Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof BlockKey)) {
				return false;
			}
			final BlockKey blockKey = (BlockKey) object;
			return x == blockKey.x && y == blockKey.y && z == blockKey.z && Objects.equals(worldId, blockKey.worldId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(worldId, x, y, z);
		}
	}
}
