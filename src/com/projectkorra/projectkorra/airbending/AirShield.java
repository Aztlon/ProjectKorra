package com.projectkorra.projectkorra.airbending;

import java.util.HashMap;
import java.util.Random;
import java.util.Set;

import com.projectkorra.projectkorra.region.RegionProtection;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.avatar.AvatarState;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.event.AbilityExecutionEvidence;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AirShield extends AirAbility {

	private boolean isToggledByAvatarState;
	@Attribute("Max" + Attribute.RADIUS)
	private double maxRadius;
	@Attribute("Initial" + Attribute.RADIUS)
	private double initialRadius;
	private double radius;
	@Attribute(Attribute.SPEED)
	private double speed;
	private int streams;
	private int particles;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	private Random random;
	private HashMap<Integer, Integer> angles;
	private boolean dynamicCooldown;

	public AirShield(final LivingEntity caster) {
		super(caster);

		this.maxRadius = getConfig().getDouble("Abilities.Air.AirShield.MaxRadius");
		this.initialRadius = getConfig().getDouble("Abilities.Air.AirShield.InitialRadius");
		this.isToggledByAvatarState = getConfig().getBoolean("Abilities.Avatar.AvatarState.Air.AirShield.IsAvatarStateToggle");
		this.radius = this.initialRadius;
		this.cooldown = getConfig().getLong("Abilities.Air.AirShield.Cooldown");
		this.duration = getConfig().getLong("Abilities.Air.AirShield.Duration");
		this.speed = getConfig().getDouble("Abilities.Air.AirShield.Speed");
		this.streams = getConfig().getInt("Abilities.Air.AirShield.Streams");
		this.particles = getConfig().getInt("Abilities.Air.AirShield.Particles");
		this.dynamicCooldown = getConfig().getBoolean("Abilities.Air.AirShield.DynamicCooldown"); //any unused duration from shield is removed from the cooldown
		if (this.duration == 0) {
			this.dynamicCooldown = false;
		}
		this.random = new Random();
		this.angles = new HashMap<>();

		if (this.bender.isAvatarState() && hasAbility(caster, AirShield.class) && this.isToggledByAvatarState) {
			getAbility(caster, AirShield.class).remove();
			return;
		}

		int angle = 0;
		final int di = (int) (this.maxRadius * 2 / this.streams);
		for (int i = -(int) this.maxRadius + di; i < (int) this.maxRadius; i += di) {
			this.angles.put(i, angle);
			angle += 90;
			if (angle == 360) {
				angle = 0;
			}
		}

		this.start();
	}

	/**
	 * This method was used for the old collision detection system. Please see
	 * {@link Collision} for the new system.
	 */
	@Deprecated
	public static boolean isWithinShield(final Location loc) {
		for (final AirShield ashield : getAbilities(AirShield.class)) {
			if (!ashield.caster.getWorld().equals(loc.getWorld())) {
				return false;
			} else if (ashield.caster.getLocation().distanceSquared(loc) <= ashield.radius * ashield.radius) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void progress() {
		// AvatarState can use AirShield even when AirShield is not in the bound slot.
		if (this.caster.getEyeLocation().getBlock().isLiquid()) {
			this.remove();
			return;
		} else if (!this.bender.isAvatarState() || !this.isToggledByAvatarState) {
			if (!this.bender.isSneaking() || !this.bender.canBend(this)) {
				if (this.dynamicCooldown) {
					long reducedCooldown = this.cooldown - (this.duration - (System.currentTimeMillis() - this.getStartTime()));
					if (reducedCooldown < 0L) {
						reducedCooldown = 0L;
					}
					this.bender.addCooldown(this, reducedCooldown);
				} else {
					this.bender.addCooldown(this);
				}
				this.remove();
				return;
			} else if (this.duration != 0) {
				if (this.getStartTime() + this.duration <= System.currentTimeMillis()) {
					this.bender.addCooldown(this);
					this.remove();
					return;
				}
			}

		} else if (!this.bender.canBendIgnoreBinds(this)) {
			this.remove();
			return;
		}
		this.rotateShield();
	}

	private void rotateShield() {
		final Location origin = this.caster.getLocation();
		for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(origin, this.radius)) {
			if (RegionProtection.isRegionProtected(this.caster, entity.getLocation(), "AirShield")) {
				continue;
			}
			if (origin.distanceSquared(entity.getLocation()) > 4) {
				double x, z, vx, vz, mag;
				double angle = 50;
				angle = Math.toRadians(angle);

				x = entity.getLocation().getX() - origin.getX();
				z = entity.getLocation().getZ() - origin.getZ();

				mag = Math.sqrt(x * x + z * z);

				vx = (x * Math.cos(angle) - z * Math.sin(angle)) / mag;
				vz = (x * Math.sin(angle) + z * Math.cos(angle)) / mag;

				final Vector velocity = entity.getVelocity().clone();
				if (this.bender.isAvatarState()) {
					velocity.setX(AvatarState.getValue(vx));
					velocity.setZ(AvatarState.getValue(vz));
				} else {
					velocity.setX(vx);
					velocity.setZ(vz);
				}

				if (entity instanceof Player) {
					if (Commands.invincible.contains(entity.getName())) {
						continue;
					}
				}

				velocity.multiply(0.5);
				final boolean velocityApplied = GeneralMethods.trySetVelocity(this, entity, velocity);
				final double appliedMagnitude = velocity.length();
				if ((entity instanceof LivingEntity || entity instanceof Projectile)
						&& velocityApplied && Double.isFinite(appliedMagnitude) && appliedMagnitude > 0D) {
					AbilityExecutionEvidence.publishEntity(this, AbilityExecutionEvidence.AIR_SHIELD_DEFLECTED,
							entity, appliedMagnitude);
				}
				entity.setFallDistance(0);
			}
		}

		for (final Block testblock : GeneralMethods.getBlocksAroundPoint(this.caster.getLocation(), this.radius)) {
			if (FireAbility.isFire(testblock.getType())) {
				testblock.setType(Material.AIR);
				testblock.getWorld().playEffect(testblock.getLocation(), Effect.EXTINGUISH, 0);
			}
		}

		final Set<Integer> keys = this.angles.keySet();
		for (final int i : keys) {
			double x, y, z;
			final double factor = this.radius / this.maxRadius;
			double angle = this.angles.get(i);
			angle = Math.toRadians(angle);
			y = origin.getY() + factor * i;
			final double f = Math.sqrt(1 - factor * factor * (i / this.radius) * (i / this.radius));

			x = origin.getX() + this.radius * Math.cos(angle) * f;
			z = origin.getZ() + this.radius * Math.sin(angle) * f;

			final Location effect = new Location(origin.getWorld(), x, y, z);
			if (!RegionProtection.isRegionProtected(this, effect)) {
				playAirbendingParticles(effect, this.particles);
				if (this.random.nextInt(4) == 0) {
					playAirbendingSound(effect);
				}
			}

			this.angles.put(i, this.angles.get(i) + (int) (this.speed));
		}

		if (this.radius < this.maxRadius) {
			this.radius += .3;
		}
		if (this.radius > this.maxRadius) {
			this.radius = this.maxRadius;
		}
	}

	@Override
	public String getName() {
		return "AirShield";
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

	@Override
	public double getCollisionRadius() {
		return this.getRadius();
	}
}
