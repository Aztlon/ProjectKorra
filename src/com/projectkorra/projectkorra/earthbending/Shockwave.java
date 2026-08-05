package com.projectkorra.projectkorra.earthbending;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.util.ParticleEffect;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Shockwave extends EarthAbility {

	private boolean charged;
	@Attribute(Attribute.CHARGE_DURATION)
	private long chargeTime;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private double angle;
	private double threshold;
	@Attribute(Attribute.RANGE)
	private double range;
	
	public Shockwave(final LivingEntity caster) {
		this(caster, true);
	}

	public Shockwave(final LivingEntity caster, final boolean fall) {
		super(caster);

		this.angle = Math.toRadians(getConfig().getDouble("Abilities.Earth.Shockwave.Angle"));
		this.cooldown = getConfig().getLong("Abilities.Earth.Shockwave.Cooldown");
		this.chargeTime = getConfig().getLong("Abilities.Earth.Shockwave.ChargeTime");
		this.threshold = getConfig().getDouble("Abilities.Earth.Shockwave.FallThreshold");
		this.range = getConfig().getDouble("Abilities.Earth.Shockwave.Range");

		if (this.bender.isAvatarState()) {
			this.range = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.Shockwave.Range");
			this.cooldown = getConfig().getLong("Abilities.Avatar.AvatarState.Earth.Shockwave.Cooldown");
			this.chargeTime = getConfig().getLong("Abilities.Avatar.AvatarState.Earth.Shockwave.ChargeTime");
		}

		if (!this.bender.canBend(this) || hasAbility(caster, Shockwave.class)) {
			return;
		}

		if (fall) {
			this.fallShockwave();
			return;
		}

		this.start();
	}

	public void fallShockwave() {
		if (!this.bender.canBendIgnoreCooldowns(this)) {
			return;
		} else if (this.caster.getFallDistance() < this.threshold || !this.isEarthbendable(this.caster.getLocation().clone().subtract(0, 1, 0).getBlock())) {
			return;
		} else if (this.bender.isOnCooldown("Shockwave")) {
			return;
		}

		this.areaShockwave();
		this.bender.addCooldown(this);
		this.remove();
	}

	@Override
	public void progress() {
		if (!this.bender.canBendIgnoreCooldowns(this)) {
			this.remove();
			return;
		}

		if (System.currentTimeMillis() > this.getStartTime() + this.chargeTime && !this.charged) {
			this.charged = true;
		}

		if (!this.bender.isSneaking()) {
			if (this.charged) {
				this.areaShockwave();
            }
            this.remove();
        } else if (this.charged) {
			final Location location = this.caster.getEyeLocation().add(this.caster.getEyeLocation().getDirection());
			ParticleEffect.SMOKE_NORMAL.display(location, 1);
		}
	}

	public static void progressAll() {
		Ripple.progressAllCleanup();
	}

	public void areaShockwave() {
		final double dtheta = 360.0 / (2 * Math.PI * this.range) - 1;
		for (double theta = 0; theta < 360; theta += dtheta) {
			final double rtheta = Math.toRadians(theta);
			final Vector vector = new Vector(Math.cos(rtheta), 0, Math.sin(rtheta));
			new Ripple(this.caster, vector.normalize());
		}
		this.bender.addCooldown(this);
	}

	public static void coneShockwave(final Player player) {
		final Shockwave shockWave = getAbility(player, Shockwave.class);
		if (shockWave != null) {
			if (shockWave.charged) {
				final double dtheta = 360.0 / (2 * Math.PI * shockWave.range) - 1;

				for (double theta = 0; theta < 360; theta += dtheta) {
					final double rtheta = Math.toRadians(theta);
					final Vector vector = new Vector(Math.cos(rtheta), 0, Math.sin(rtheta));
					if (vector.angle(player.getEyeLocation().getDirection()) < shockWave.angle) {
						new Ripple(player, vector.normalize());
					}
				}
				shockWave.bender.addCooldown(shockWave);
				shockWave.remove();
			}
		}
	}

	@Override
	public String getName() {
		return "Shockwave";
	}

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
