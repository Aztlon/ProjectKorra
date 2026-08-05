package com.projectkorra.projectkorra.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.Element;

public abstract class CombustionAbility extends FireAbility implements SubAbility {

	private static final Map<Block, Integer> hitBlocks = new HashMap<>();
	private static final Map<Block, Long> timedHits = new HashMap<>();
	private static final int obsidianBreakHits = getConfig().getInt("Properties.Fire.Combustion.ObsidianBreakHits");
	private static final long forgetTime = getConfig().getInt("Properties.Fire.Combustion.HitForgetTime");

	public CombustionAbility(final LivingEntity caster) {
		super(caster);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return FireAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.COMBUSTION;
	}

	@Override
	public boolean isExplosiveAbility() {
		return true;
	}

	//Overriding these methods to make sure Combustion abilities don't get buffed by blue fire
	@Override
	public double applyModifiersDamage(double value) {
		return GeneralMethods.applyModifiers(value, getDayFactor(1.0));
	}

	@Override
	public double applyModifiersRange(double value) {
		return GeneralMethods.applyModifiers(value, getDayFactor(1.0));
	}

	@Override
	public long applyModifiersCooldown(long value) {
		return (long) GeneralMethods.applyInverseModifiers(value, getDayFactor(1.0));
	}

	public List<Block> applyNewBlocks(List<Block> newList) {
		ArrayList<Block> blocksBroken = new ArrayList<>();
		newList.forEach(b -> {
			if (hitBlocks.containsKey(b)) {
				if (timedHits.containsKey(b)) {
					if (timedHits.get(b) + forgetTime < System.currentTimeMillis()){
						hitBlocks.remove(b);
						timedHits.remove(b);
					}
				}
				int newStage = hitBlocks.get(b)+1;
				hitBlocks.replace(b, newStage);
				if (newStage >= obsidianBreakHits) {
					hitBlocks.remove(b);
					blocksBroken.add(b);
				}
			} else {
				hitBlocks.put(b, 1);
				timedHits.put(b, System.currentTimeMillis());
			}
		});
		return blocksBroken;
	}
}
