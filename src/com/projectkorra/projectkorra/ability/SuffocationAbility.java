package com.projectkorra.projectkorra.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;

public abstract class SuffocationAbility extends AirAbility implements SubAbility {
	public SuffocationAbility(LivingEntity caster) {
		super(caster);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return AirAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.SUFFOCATION;
	}
}
