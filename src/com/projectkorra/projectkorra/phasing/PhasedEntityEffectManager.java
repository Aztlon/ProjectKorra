package com.projectkorra.projectkorra.phasing;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.ability.Ability;

public final class PhasedEntityEffectManager {

	private PhasedEntityEffectManager() {
	}

	@Nullable
	public static Ability resolveAbility(@Nullable final Ability ability) {
		return ability == null ? PhasedIntegrationManager.getCurrentAbilityContext() : ability;
	}

	public static boolean shouldAllowEffect(@Nullable final Ability ability, @Nullable final Entity target) {
		return shouldAllow(ability, target, GateStage.EFFECT);
	}

	public static boolean shouldAllowEffect(@Nullable final LivingEntity source, @Nullable final String abilityId, @Nullable final Entity target) {
		return shouldAllow(source, abilityId, target, GateStage.EFFECT);
	}

	public static boolean shouldAllow(@Nullable final Ability ability, @Nullable final Entity target, final GateStage stage) {
		if (target == null) {
			return true;
		}

		final Ability sourceAbility = resolveAbility(ability);
		return PhasedIntegrationManager.shouldAllow(
				PhasedIntegrationManager.requestFromAbility(sourceAbility, target.getUniqueId(), stage, target.getLocation(), null));
	}

	public static boolean shouldAllow(@Nullable final LivingEntity source, @Nullable final String abilityId,
			@Nullable final Entity target, final GateStage stage) {
		if (target == null) {
			return true;
		}

		return PhasedIntegrationManager.shouldAllow(
				PhasedIntegrationManager.requestFromSource(source, abilityId, target.getUniqueId(), stage, target.getLocation(), null));
	}

	public static boolean setFireTicks(@Nullable final Ability ability, final Entity entity, final int ticks) {
		if (!shouldAllowEffect(ability, entity)) {
			return false;
		}
		entity.setFireTicks(ticks);
		return true;
	}

	public static boolean setFireTicks(@Nullable final LivingEntity source, @Nullable final String abilityId, final Entity entity, final int ticks) {
		if (!shouldAllowEffect(source, abilityId, entity)) {
			return false;
		}
		entity.setFireTicks(ticks);
		return true;
	}

	public static boolean addPotionEffect(@Nullable final Ability ability, final LivingEntity entity, final PotionEffect effect) {
		return addPotionEffect(ability, entity, effect, false);
	}

	public static boolean addPotionEffect(@Nullable final Ability ability, final LivingEntity entity, final PotionEffect effect, final boolean force) {
		if (!shouldAllowEffect(ability, entity)) {
			return false;
		}
		entity.addPotionEffect(effect, force);
		return true;
	}

	public static boolean addPotionEffect(@Nullable final LivingEntity source, @Nullable final String abilityId,
			final LivingEntity entity, final PotionEffect effect) {
		return addPotionEffect(source, abilityId, entity, effect, false);
	}

	public static boolean addPotionEffect(@Nullable final LivingEntity source, @Nullable final String abilityId,
			final LivingEntity entity, final PotionEffect effect, final boolean force) {
		if (!shouldAllowEffect(source, abilityId, entity)) {
			return false;
		}
		entity.addPotionEffect(effect, force);
		return true;
	}

	public static boolean removePotionEffect(@Nullable final Ability ability, final LivingEntity entity, final PotionEffectType effectType) {
		if (!shouldAllowEffect(ability, entity)) {
			return false;
		}
		entity.removePotionEffect(effectType);
		return true;
	}

	public static boolean removePotionEffect(@Nullable final LivingEntity source, @Nullable final String abilityId,
			final LivingEntity entity, final PotionEffectType effectType) {
		if (!shouldAllowEffect(source, abilityId, entity)) {
			return false;
		}
		entity.removePotionEffect(effectType);
		return true;
	}
}
