package com.projectkorra.projectkorra.ability;

import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;

public abstract class WarriorAbility extends NonAbility implements SubAbility {

	public WarriorAbility(final Player player) {
		super(player);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return NonAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.WARRIOR;
	}

}
