package com.projectkorra.projectkorra.firebending;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.attribute.Attribute;

public class Blaze extends FireAbility {

	@Attribute("Arc")
	private int arc;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.SPEED)
	private double speed;

	public Blaze(final LivingEntity caster) {
		super(caster);

		this.speed = 2;
		this.cooldown = applyModifiersCooldown(getConfig().getLong("Abilities.Fire.Blaze.Cooldown"));
		this.arc = (int) applyModifiers(getConfig().getInt("Abilities.Fire.Blaze.Arc"));
		this.range = applyModifiersRange(getConfig().getDouble("Abilities.Fire.Blaze.Range"));

		if (!this.bender.canBend(this) || this.bender.isOnCooldown("BlazeArc")) {
			return;
		}

		//this.range = this.getDayFactor(this.range);
		//this.range = AvatarState.getValue(this.range, caster);
		//this.arc = (int) this.getDayFactor(this.arc);
		final Location location = caster.getLocation();

		for (int i = -this.arc; i <= this.arc; i += this.speed) {
			final double angle = Math.toRadians(i);
			final Vector direction = caster.getEyeLocation().getDirection().clone();
			double x, z, vx, vz;

			x = direction.getX();
			z = direction.getZ();

			vx = x * Math.cos(angle) - z * Math.sin(angle);
			vz = x * Math.sin(angle) + z * Math.cos(angle);

			direction.setX(vx);
			direction.setZ(vz);

			new BlazeArc(caster, location, direction, this.range);
		}

		this.start();
		this.bender.addCooldown("BlazeArc", this.cooldown);
		this.remove();
	}

	@Override
	public String getName() {
		return "Blaze";
	}

	@Override
	public void progress() {}

	@Override
	public Location getLocation() {
		return this.caster != null ? this.caster.getLocation() : null;
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

}
