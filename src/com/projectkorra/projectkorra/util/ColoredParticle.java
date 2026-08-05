package com.projectkorra.projectkorra.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle.DustOptions;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.ability.Ability;

public class ColoredParticle {

	private final DustOptions dust;

	public ColoredParticle(final Color color, final float size) {
		this.dust = new DustOptions(color, size);
	}

	public DustOptions getDustOptions() {
		return this.dust;
	}

	public void display(final Location loc, final int amount, final double offsetX, final double offsetY, final double offsetZ) {
		display(null, loc, amount, offsetX, offsetY, offsetZ);
	}

	public void display(@Nullable final Ability ability, final Location loc, final int amount, final double offsetX, final double offsetY, final double offsetZ) {
		ParticleEffect.REDSTONE.display(ability, loc, amount, offsetX, offsetY, offsetZ, this.dust);
	}
}
