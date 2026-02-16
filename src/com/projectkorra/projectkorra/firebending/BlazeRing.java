package com.projectkorra.projectkorra.firebending;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.attribute.Attribute;

public class BlazeRing extends FireAbility {

	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private double angleIncrement;
	private Location location;

	public BlazeRing(final LivingEntity caster) {
		super(caster);

		this.range = applyModifiersRange(getConfig().getDouble("Abilities.Fire.Blaze.Ring.Range"));
		this.angleIncrement = getConfig().getDouble("Abilities.Fire.Blaze.Ring.Angle");
		this.cooldown = applyModifiersCooldown(getConfig().getLong("Abilities.Fire.Blaze.Ring.Cooldown"));
		this.location = caster.getLocation();

		if (this.bender.isAvatarState()) {
			this.range = getConfig().getInt("Abilities.Avatar.AvatarState.Fire.Blaze.Ring.Range");
		}
		if (!this.bender.canBend(this) || this.bender.isOnCooldown("BlazeRing")) {
			return;
		}

		this.start();
		if (!this.isRemoved()) {
			for (double degrees = 0; degrees < 360; degrees += this.angleIncrement) {
				final double angle = Math.toRadians(degrees);
				final Vector direction = caster.getEyeLocation().getDirection().clone();
				double x, z, vx, vz;

				x = direction.getX();
				z = direction.getZ();

				vx = x * Math.cos(angle) - z * Math.sin(angle);
				vz = x * Math.sin(angle) + z * Math.cos(angle);

				direction.setX(vx);
				direction.setZ(vz);

				new BlazeArc(caster, this.location, direction, this.range);
			}
			this.bender.addCooldown("BlazeRing", this.cooldown);
			this.remove();
		}
	}

	@Override
	public String getName() {
		return "Blaze";
	}

	@Override
	public void progress() {}

	@Override
	public Location getLocation() {
		return this.location;
	}

	@Override
	public long getCooldown() {
		return this.cooldown;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	public double getRange() {
		return this.range;
	}

	public void setRange(final int range) {
		this.range = range;
	}

	public double getAngleIncrement() {
		return this.angleIncrement;
	}

	public void setAngleIncrement(final double angleIncrement) {
		this.angleIncrement = angleIncrement;
	}

	public void setCooldown(final long cooldown) {
		this.cooldown = cooldown;
	}

	public void setLocation(final Location location) {
		this.location = location;
	}

}
