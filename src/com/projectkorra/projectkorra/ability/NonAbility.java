package com.projectkorra.projectkorra.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;

public abstract class NonAbility extends ElementalAbility {

	public NonAbility(LivingEntity caster) {
		super(caster);
	}

	@Override
	public boolean isIgniteAbility() {
		return false;
	}

	@Override
	public boolean isExplosiveAbility() {
		return false;
	}

	@Override
	public Element getElement() {
		return Element.NON;
	}

}