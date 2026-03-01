package com.projectkorra.projectkorra.chiblocking.passive;

import java.util.Comparator;

import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.projectkorra.projectkorra.Bender;
import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.ChiAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.airbending.Suffocate;
import com.projectkorra.projectkorra.chiblocking.AcrobatStance;
import com.projectkorra.projectkorra.chiblocking.QuickStrike;
import com.projectkorra.projectkorra.chiblocking.SwiftKick;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.ActionBar;

public class ChiPassive {
	public static boolean willChiBlock(final LivingEntity attacker, final LivingEntity target) {
		final Bender bender = Bender.get(target);
		if (bender == null) {
			return false;
		}

		final ChiAbility stance = bender.getStance();
		final QuickStrike quickStrike = CoreAbility.getAbility(target, QuickStrike.class);
		final SwiftKick swiftKick = CoreAbility.getAbility(target, SwiftKick.class);
		double newChance = getChance();

		if (stance instanceof AcrobatStance acro) {
			newChance += acro.getChiBlockBoost();
		}

		if (quickStrike != null) {
			newChance += quickStrike.getBlockChance();
		} else if (swiftKick != null) {
			newChance += swiftKick.getBlockChance();
		}

		if (Math.random() > newChance / 100.0) {
			return false;
		} else return !bender.isChiBlocked();
	}

	public static void blockChi(final LivingEntity target) {
		if (Suffocate.isChannelingSphere(target)) {
			Suffocate.remove(target);
		}

		final Bender bender = Bender.get(target);
		if (bender == null) {
			return;
		}

		bender.blockChi();
		target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 2, 0);

		final long start = System.currentTimeMillis();
		new BukkitRunnable() {
			@Override
			public void run() {
				if (target instanceof Player p)
					ActionBar.sendActionBar(Element.NON.getColor() + "* Chiblocked *", p);
				if (System.currentTimeMillis() >= start + getDuration()) {
					bender.unblockChi();
					this.cancel();
				}
			}
		}.runTaskTimer(ProjectKorra.plugin, 0, 1);
	}

	public static Entity findTarget(final LivingEntity caster) {
		// nearest entity within 5 blocks that is not the caster
		return GeneralMethods.getEntitiesAroundPoint(caster.getLocation(), 5, e -> e instanceof LivingEntity && e != caster).stream()
				.min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(caster.getLocation())))
				.orElse(null);
	}

	public static double getChance() {
		return ConfigManager.getConfig().getDouble("Abilities.Chi.Passive.BlockChi.Chance");
	}

	public static int getDuration() {
		return ConfigManager.getConfig().getInt("Abilities.Chi.Passive.BlockChi.Duration");
	}

	public static long getTicks() {
		return (getDuration() / 1000) * 20;
	}
}
