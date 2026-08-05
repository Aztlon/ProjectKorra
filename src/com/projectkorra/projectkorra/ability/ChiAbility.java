package com.projectkorra.projectkorra.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;

public abstract class ChiAbility extends NonAbility implements SubAbility {

	public ChiAbility(final LivingEntity caster) {
		super(caster);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return NonAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.CHI;
	}

}