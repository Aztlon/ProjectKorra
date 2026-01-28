package com.projectkorra.projectkorra.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;

public abstract class DayBloodAbility extends BloodAbility implements SubAbility {
	public DayBloodAbility(LivingEntity caster) {
		super(caster);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return BloodAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.DAY_BLOOD;
	}
}
