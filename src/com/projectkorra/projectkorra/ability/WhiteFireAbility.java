package com.projectkorra.projectkorra.ability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;

import com.projectkorra.projectkorra.Element;

public abstract class WhiteFireAbility extends FireAbility implements SubAbility{

	private static final Map<LivingEntity, Long> burntOutTimes = new HashMap<>();
	private static long debuffedDuration;
	private double charge;
	private final List<String> validAbilities;

	public WhiteFireAbility(final LivingEntity caster) {
		super(caster);
		validAbilities = getConfig().getStringList("Properties.Fire.WhiteFire.ValidChargeAbilities");
		debuffedDuration = getConfig().getLong("Properties.Fire.WhiteFire.DebuffDuration");
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return FireAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.WHITE_FIRE;
	}

	public void increaseCharge(Ability ability) {if (validAbilities.contains(ability.getName())) {charge++;}}

	public void decreaseCharge(Double value) {charge -= value;}

	public Double getCharge() {return charge;}

	public void setCharge(Double value) {charge = value;}

	public void toggleBurnOut(LivingEntity caster) {
		if (burntOutTimes.containsKey(caster)) {
			burntOutTimes.remove(caster);
			return;
		}
		burntOutTimes.put(caster, System.currentTimeMillis());
	}

	private static void checkRemove(LivingEntity caster) {
		if (burntOutTimes.get(caster) + debuffedDuration < System.currentTimeMillis()) {
			burntOutTimes.remove(caster);
		}
	}

	public static double getDamageFactor(LivingEntity caster, Boolean burnt) {
		if (burnt) {
			checkRemove(caster);
			return getConfig().getDouble("Properties.Fire.WhiteFire.BurnOutDamageFactor");
		}
		return getConfig().getDouble("Properties.Fire.WhiteFire.DamageFactor");
	}

	public static double getCooldownFactor(LivingEntity caster, Boolean burnt) {
		if (burnt) {
			checkRemove(caster);
			return getConfig().getDouble("Properties.Fire.WhiteFire.BurnOutCooldownFactor");
		}
		return getConfig().getDouble("Properties.Fire.WhiteFire.CooldownFactor");}

	public static double getRangeFactor(LivingEntity caster, Boolean burnt) {
		if (burnt) {
			checkRemove(caster);
			return getConfig().getDouble("Properties.Fire.WhiteFire.BurnOutRangeFactor");
		}
		return getConfig().getDouble("Properties.Fire.WhiteFire.RangeFactor");}

	public static double getChargeFactor(LivingEntity caster, Boolean burnt) {
		if (burnt) {
			checkRemove(caster);
			return getConfig().getDouble("Properties.Fire.WhiteFire.BurnOutChargeFactor");
		}
		return getConfig().getDouble("Properties.Fire.WhiteFire.ChargeFactor");}

	public static Map<LivingEntity, Long> getBurntOutPlayers() {return burntOutTimes;}
}