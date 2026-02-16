package com.projectkorra.projectkorra.waterbending.util;

import com.projectkorra.projectkorra.ability.WaterAbility;

public interface IWaterAbilityTransformer {
	WaterAbility apply(WaterAbility ability, String targetAbilityName);
}
