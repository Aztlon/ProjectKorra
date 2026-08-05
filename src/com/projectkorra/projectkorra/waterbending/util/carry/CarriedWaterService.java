package com.projectkorra.projectkorra.waterbending.util.carry;

import org.bukkit.entity.Player;

/**
 * Optional external service bridge for carried-water systems (e.g. waterskins).
 * Implementations are expected to be registered in Bukkit ServicesManager.
 */
public interface CarriedWaterService {

	boolean hasWaterskin(Player player);

	boolean consumeBest(Player player, int amount, String cause);

	boolean returnWater(Player player, int amount, String cause);

	boolean refillBest(Player player, int amount, String cause);
}
