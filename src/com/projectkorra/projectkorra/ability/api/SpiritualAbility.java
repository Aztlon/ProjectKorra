package com.projectkorra.projectkorra.ability.api;

import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.configuration.configs.abilities.AbilityConfig;

public abstract class SpiritualAbility<C extends AbilityConfig> extends AirAbility<C> implements SubAbility {

	public SpiritualAbility(final C config, final Player player) {
		super(config, player);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return AirAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.SPIRITUAL;
	}

}