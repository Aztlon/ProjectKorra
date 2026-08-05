package com.projectkorra.projectkorra.util.particles;

import org.bukkit.Location;
import org.bukkit.Particle;

public final class ParticleSpawnRequest {

	private final Particle particle;
	private final Location location;
	private final int amount;
	private final double offsetX;
	private final double offsetY;
	private final double offsetZ;
	private final double extra;
	private final Object data;

	public ParticleSpawnRequest(final Particle particle, final Location location, final int amount, final double offsetX,
			final double offsetY, final double offsetZ, final double extra, final Object data) {
		this.particle = particle;
		this.location = location;
		this.amount = amount;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		this.extra = extra;
		this.data = data;
	}

	public Particle getParticle() {
		return this.particle;
	}

	public Location getLocation() {
		return this.location;
	}

	public int getAmount() {
		return this.amount;
	}

	public double getOffsetX() {
		return this.offsetX;
	}

	public double getOffsetY() {
		return this.offsetY;
	}

	public double getOffsetZ() {
		return this.offsetZ;
	}

	public double getExtra() {
		return this.extra;
	}

	public Object getData() {
		return this.data;
	}
}
