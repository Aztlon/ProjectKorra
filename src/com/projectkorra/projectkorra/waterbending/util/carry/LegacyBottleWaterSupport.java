package com.projectkorra.projectkorra.waterbending.util.carry;

import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

final class LegacyBottleWaterSupport {

	private LegacyBottleWaterSupport() {
	}

	static int countWaterBottles(final PlayerInventory inventory) {
		int total = 0;
		for (int i = 0; i < inventory.getSize(); i++) {
			final ItemStack item = inventory.getItem(i);
			if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) {
				continue;
			}

			final PotionMeta meta = (PotionMeta) item.getItemMeta();
			if (meta.hasBasePotionType() && meta.getBasePotionType() == PotionType.WATER) {
				total += item.getAmount();
			}
		}
		return total;
	}

	static int countEmptyBottles(final PlayerInventory inventory) {
		return inventory.all(Material.GLASS_BOTTLE).values().stream().mapToInt(ItemStack::getAmount).sum();
	}

	static boolean consumeWaterBottles(final Player player, int amount) {
		if (amount <= 0) {
			return true;
		}
		final PlayerInventory inventory = player.getInventory();
		if (countWaterBottles(inventory) < amount) {
			return false;
		}

		for (int i = 0; i < inventory.getSize() && amount > 0; i++) {
			final ItemStack item = inventory.getItem(i);
			if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) {
				continue;
			}

			final PotionMeta meta = (PotionMeta) item.getItemMeta();
			if (!meta.hasBasePotionType() || meta.getBasePotionType() != PotionType.WATER) {
				continue;
			}

			final int take = Math.min(item.getAmount(), amount);
			if (item.getAmount() == take) {
				inventory.setItem(i, null);
			} else {
				item.setAmount(item.getAmount() - take);
				inventory.setItem(i, item);
			}

			amount -= take;
			addOrDrop(player, new ItemStack(Material.GLASS_BOTTLE, take));
		}

		return amount == 0;
	}

	static boolean fillEmptyBottles(final Player player, int amount) {
		if (amount <= 0) {
			return true;
		}
		final PlayerInventory inventory = player.getInventory();
		if (countEmptyBottles(inventory) < amount) {
			return false;
		}

		for (int i = 0; i < inventory.getSize() && amount > 0; i++) {
			final ItemStack item = inventory.getItem(i);
			if (item == null || item.getType() != Material.GLASS_BOTTLE) {
				continue;
			}

			final int take = Math.min(item.getAmount(), amount);
			if (item.getAmount() == take) {
				inventory.setItem(i, null);
			} else {
				item.setAmount(item.getAmount() - take);
				inventory.setItem(i, item);
			}

			amount -= take;
			addOrDrop(player, waterBottleStack(take));
		}

		return amount == 0;
	}

	private static ItemStack waterBottleStack(final int amount) {
		final ItemStack water = new ItemStack(Material.POTION, amount);
		final PotionMeta meta = (PotionMeta) water.getItemMeta();
		meta.setBasePotionType(PotionType.WATER);
		water.setItemMeta(meta);
		return water;
	}

	private static void addOrDrop(final Player player, final ItemStack itemStack) {
		final HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);
		for (final ItemStack left : leftover.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), left);
		}
	}
}
