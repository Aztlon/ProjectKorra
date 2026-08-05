package com.projectkorra.projectkorra.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.phasing.PhasedEntityEffectManager;
import com.projectkorra.projectkorra.phasing.PhasedIntegrationManager;

public class TempPotionEffect {

	private static Map<LivingEntity, TempPotionEffect> instances = new ConcurrentHashMap<LivingEntity, TempPotionEffect>();
	private static final long tick = 21;

	private int ID = Integer.MIN_VALUE;
	private final Map<Integer, PotionInfo> infos = new ConcurrentHashMap<Integer, PotionInfo>();
	private final LivingEntity entity;

	public TempPotionEffect(final LivingEntity entity, final PotionEffect effect) {
		this(entity, effect, System.currentTimeMillis(), PhasedIntegrationManager.getCurrentAbilityContext(), null, null);
	}

	public TempPotionEffect(final LivingEntity entity, final PotionEffect effect, final long starttime) {
		this(entity, effect, starttime, PhasedIntegrationManager.getCurrentAbilityContext(), null, null);
	}

	public TempPotionEffect(final LivingEntity entity, final PotionEffect effect, @Nullable final Ability ability) {
		this(entity, effect, System.currentTimeMillis(), ability, null, null);
	}

	public TempPotionEffect(final LivingEntity entity, final PotionEffect effect, final long starttime, @Nullable final Ability ability) {
		this(entity, effect, starttime, ability, null, null);
	}

	public TempPotionEffect(final LivingEntity entity, final PotionEffect effect, @Nullable final LivingEntity source, @Nullable final String abilityId) {
		this(entity, effect, System.currentTimeMillis(), null, source, abilityId);
	}

	public TempPotionEffect(final LivingEntity entity, final PotionEffect effect, final long starttime,
			@Nullable final LivingEntity source, @Nullable final String abilityId) {
		this(entity, effect, starttime, null, source, abilityId);
	}

	private TempPotionEffect(final LivingEntity entity, final PotionEffect effect, final long starttime,
			@Nullable final Ability ability, @Nullable final LivingEntity source, @Nullable final String abilityId) {
		this.entity = entity;
		final PotionInfo info = new PotionInfo(starttime, effect, ability, source, abilityId);
		if (instances.containsKey(entity)) {
			final TempPotionEffect instance = instances.get(entity);
			instance.infos.put(instance.ID++, info);
			instances.put(entity, instance);
		} else {
			this.infos.put(this.ID++, info);
			instances.put(entity, this);
		}
	}

	public static void progressAll() {
		for (final LivingEntity entity : instances.keySet()) {
			instances.get(entity).progress();
		}
	}

	private void addEffect(final PotionInfo info) {
		if (!this.shouldAllow(info)) {
			return;
		}

		final PotionEffect effect = info.getEffect();
		for (final PotionEffect peffect : this.entity.getActivePotionEffects()) {
			if (peffect.getType().equals(effect.getType())) {
				if (peffect.getAmplifier() > effect.getAmplifier()) {
					if (peffect.getDuration() > effect.getDuration()) {
						return;
					} else {
						final int dt = effect.getDuration() - peffect.getDuration();
						final PotionEffect neweffect = new PotionEffect(effect.getType(), dt, effect.getAmplifier());
						new TempPotionEffect(this.entity, neweffect, System.currentTimeMillis() + peffect.getDuration() * tick,
								info.getAbility(), info.getSource(), info.getAbilityId());
						return;
					}
				} else {
					if (peffect.getDuration() > effect.getDuration()) {
						this.entity.removePotionEffect(peffect.getType());
						this.entity.addPotionEffect(effect);
						final int dt = peffect.getDuration() - effect.getDuration();
						final PotionEffect neweffect = new PotionEffect(peffect.getType(), dt, peffect.getAmplifier());
						new TempPotionEffect(this.entity, neweffect, System.currentTimeMillis() + effect.getDuration() * tick,
								info.getAbility(), info.getSource(), info.getAbilityId());
						return;
					} else {
						this.entity.removePotionEffect(peffect.getType());
						this.entity.addPotionEffect(effect);
						return;
					}
				}
			}
		}
		this.entity.addPotionEffect(effect);
	}

	private boolean shouldAllow(final PotionInfo info) {
		if (info.getAbility() != null) {
			return PhasedEntityEffectManager.shouldAllowEffect(info.getAbility(), this.entity);
		}
		if (info.getSource() != null || info.getAbilityId() != null) {
			return PhasedEntityEffectManager.shouldAllowEffect(info.getSource(), info.getAbilityId(), this.entity);
		}
		return PhasedEntityEffectManager.shouldAllowEffect((Ability) null, this.entity);
	}

	private void progress() {
		for (final int id : this.infos.keySet()) {
			final PotionInfo info = this.infos.get(id);
			if (info.getTime() < System.currentTimeMillis()) {
				this.addEffect(info);
				this.infos.remove(id);
			}
		}
		if (this.infos.isEmpty() && instances.containsKey(this.entity)) {
			instances.remove(this.entity);
		}
	}

	private class PotionInfo {

		private final long starttime;
		private final PotionEffect effect;
		@Nullable
		private final Ability ability;
		@Nullable
		private final LivingEntity source;
		@Nullable
		private final String abilityId;

		public PotionInfo(final long starttime, final PotionEffect effect, @Nullable final Ability ability,
				@Nullable final LivingEntity source, @Nullable final String abilityId) {
			this.starttime = starttime;
			this.effect = effect;
			this.ability = ability;
			this.source = source;
			this.abilityId = abilityId;
		}

		public long getTime() {
			return this.starttime;
		}

		public PotionEffect getEffect() {
			return this.effect;
		}

		@Nullable
		public Ability getAbility() {
			return this.ability;
		}

		@Nullable
		public LivingEntity getSource() {
			return this.source;
		}

		@Nullable
		public String getAbilityId() {
			return this.abilityId;
		}

	}

}
