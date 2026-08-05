package com.projectkorra.projectkorra.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;

public abstract class BloodAbility extends WaterAbility implements SubAbility {

	public BloodAbility(final LivingEntity caster) {
		super(caster);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return WaterAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.BLOOD;
	}

}
