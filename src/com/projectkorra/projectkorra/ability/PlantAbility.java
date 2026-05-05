package com.projectkorra.projectkorra.ability;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.util.ParticleEffect;

public abstract class PlantAbility extends WaterAbility implements SubAbility {

	public PlantAbility(final LivingEntity caster) {
		super(caster);
	}

	@Override
	public Class<? extends Ability> getParentAbility() {
		return WaterAbility.class;
	}

	@Override
	public Element getElement() {
		return Element.PLANT;
	}

	// Because Plantbending deserves particles too!
	public void playPlantbendingParticles(final Location loc, final int amount, final double xOffset, final double yOffset, final double zOffset) {
		ParticleEffect.BLOCK.display(loc.clone().add(0.5, 0, 0.5), amount, xOffset, yOffset, zOffset, Material.OAK_LEAVES.createBlockData());
	}

}
