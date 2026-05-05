package com.projectkorra.projectkorra.waterbending.util.carry;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.ability.CoreAbility;

public final class CarriedWaterManager {

	private static final Map<CoreAbility, Integer> CONSUMED = Collections.synchronizedMap(new WeakHashMap<>());
	private static volatile long lastLookup;
	private static volatile CarriedWaterService cachedService;

	private CarriedWaterManager() {
	}

	public static boolean hasCarriedWater(final Player player, final int amount, final String cause) {
		if (player == null || amount <= 0) {
			return false;
		}
		final CarriedWaterService service = getService();
		if (service != null) {
			return service.hasWaterskin(player);
		}
		return LegacyBottleWaterSupport.countWaterBottles(player.getInventory()) >= amount;
	}

	public static boolean canReceiveReturnedWater(final Player player, final int amount, final String cause) {
		if (player == null || amount <= 0) {
			return false;
		}
		final CarriedWaterService service = getService();
		if (service != null) {
			return service.hasWaterskin(player);
		}
		return LegacyBottleWaterSupport.countEmptyBottles(player.getInventory()) >= amount;
	}

	public static boolean consumeCarriedWater(final Player player, final int amount, final String cause) {
		if (player == null || amount <= 0) {
			return false;
		}
		final CarriedWaterService service = getService();
		if (service != null) {
			return service.consumeBest(player, amount, cause);
		}
		return LegacyBottleWaterSupport.consumeWaterBottles(player, amount);
	}

	public static boolean returnCarriedWater(final Player player, final int amount, final String cause) {
		if (player == null || amount <= 0) {
			return false;
		}
		final CarriedWaterService service = getService();
		if (service != null) {
			return service.returnWater(player, amount, cause);
		}
		return LegacyBottleWaterSupport.fillEmptyBottles(player, amount);
	}

	public static boolean refillCarriedWater(final Player player, final int amount, final String cause) {
		if (player == null || amount <= 0) {
			return false;
		}
		final CarriedWaterService service = getService();
		if (service != null) {
			return service.refillBest(player, amount, cause);
		}
		return LegacyBottleWaterSupport.fillEmptyBottles(player, amount);
	}

	public static boolean consumeForAbility(final CoreAbility ability, final int amount, final String cause) {
		if (ability == null || amount <= 0) {
			return false;
		}
		final Player player = ability.getPlayer();
		if (player == null || !consumeCarriedWater(player, amount, cause)) {
			return false;
		}
		CONSUMED.merge(ability, amount, Integer::sum);
		return true;
	}

	public static int getConsumedForAbility(final CoreAbility ability) {
		if (ability == null) {
			return 0;
		}
		return CONSUMED.getOrDefault(ability, 0);
	}

	public static boolean returnForAbility(final CoreAbility ability, final int amount, final String cause) {
		if (ability == null || amount <= 0) {
			return false;
		}
		final Player player = ability.getPlayer();
		if (player == null) {
			return false;
		}

		final int consumed = getConsumedForAbility(ability);
		final int toReturn = Math.min(amount, consumed);
		if (toReturn <= 0) {
			return false;
		}
		if (!returnCarriedWater(player, toReturn, cause)) {
			return false;
		}

		if (consumed == toReturn) {
			CONSUMED.remove(ability);
		} else {
			CONSUMED.put(ability, consumed - toReturn);
		}
		return true;
	}

	private static CarriedWaterService getService() {
		final long now = System.currentTimeMillis();
		if (cachedService != null && now - lastLookup < 2000L) {
			return cachedService;
		}
		lastLookup = now;
		cachedService = Bukkit.getServicesManager().load(CarriedWaterService.class);
		return cachedService;
	}
}
