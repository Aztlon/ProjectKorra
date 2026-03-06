package com.projectkorra.projectkorra.firebending;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.BlueFireAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.avatar.AvatarState;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FireBlastCharged extends FireAbility {

	private static final Map<Entity, FireBlastCharged> EXPLOSIONS = new ConcurrentHashMap<>();

	private boolean charged;
	private boolean launched;
	private boolean canDamageBlocks;
	private boolean dissipate;
	private long time;
	@Attribute(Attribute.CHARGE_DURATION)
	private long chargeTime;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private long interval;
	@Attribute("Min" + Attribute.DAMAGE)
	private double minDamage;
	@Attribute("Max" + Attribute.DAMAGE)
	private double maxDamage;
	@Attribute(Attribute.RANGE)
	private double range;
	private double collisionRadius;
	@Attribute(Attribute.DAMAGE + Attribute.RANGE)
	private double damageRadius;
	@Attribute("Explosion" + Attribute.RANGE)
	private double explosionRadius;
	private double innerRadius;
	@Attribute(Attribute.FIRE_TICK)
	private double fireTicks;
	private TNTPrimed explosion;
	private Location origin;
	private Location location;
	private Vector direction;

	public FireBlastCharged(final LivingEntity caster) {
		super(caster);

		if (!bender.hasUnlocked("FireBlastCharged")) return;

		if (!this.bender.canBendIgnoreBinds(this) || !this.bender.getBoundAbilityName().equals("FireBlast")) {
			return;
		}

		this.charged = false;
		this.launched = false;
		this.canDamageBlocks = getConfig().getBoolean("Abilities.Fire.FireBlast.Charged.DamageBlocks");
		this.dissipate = getConfig().getBoolean("Abilities.Fire.FireBlast.Dissipate");
		this.chargeTime = applyModifiersChargeTime(getConfig().getLong("Abilities.Fire.FireBlast.Charged.ChargeTime"));
		this.cooldown = applyModifiersCooldown(getConfig().getLong("Abilities.Fire.FireBlast.Charged.Cooldown"));
		this.time = System.currentTimeMillis();
		this.interval = 25;
		this.collisionRadius = applyModifiers(getConfig().getDouble("Abilities.Fire.FireBlast.Charged.CollisionRadius"));
		this.minDamage = applyModifiersDamage(getConfig().getDouble("Abilities.Fire.FireBlast.Charged.MinimumDamage"));
		this.maxDamage = applyModifiersDamage(getConfig().getDouble("Abilities.Fire.FireBlast.Charged.MaximumDamage"));
		this.range = applyModifiersRange(getConfig().getDouble("Abilities.Fire.FireBlast.Charged.Range"));
		this.damageRadius = applyModifiers(getConfig().getDouble("Abilities.Fire.FireBlast.Charged.DamageRadius"));
		this.explosionRadius = applyModifiers(getConfig().getDouble("Abilities.Fire.FireBlast.Charged.ExplosionRadius"));
		this.fireTicks = applyModifiers(getConfig().getDouble("Abilities.Fire.FireBlast.Charged.FireTicks"));
		this.innerRadius = this.damageRadius / 2;


		//this.applyModifiers();

		if (!caster.getEyeLocation().getBlock().isLiquid()) {
			this.start();
		}
	}

	@Deprecated
	private void applyModifiers() {
		long chargeTimeMod = 0;
		double minDamageMod = 0;
		double maxDamageMod = 0;
		double rangeMod = 0;

		if (isDay(caster.getWorld())) {
			chargeTimeMod = (long) (this.chargeTime / getDayFactor() - this.chargeTime);
			minDamageMod = this.getDayFactor(this.minDamage) - this.minDamage;
			maxDamageMod = this.getDayFactor(this.maxDamage) - this.maxDamage;
			rangeMod = this.getDayFactor(this.range) - this.range;
		}

		chargeTimeMod = (long) (bender.canUseSubElement(SubElement.BLUE_FIRE) ? (chargeTime / BlueFireAbility.getCooldownFactor() - chargeTime) + chargeTimeMod : chargeTimeMod);
		minDamageMod = (bender.canUseSubElement(SubElement.BLUE_FIRE) ? (BlueFireAbility.getDamageFactor() * minDamage - minDamage) + minDamageMod : minDamageMod);
		maxDamageMod = (bender.canUseSubElement(SubElement.BLUE_FIRE) ? (BlueFireAbility.getDamageFactor() * maxDamage - maxDamage) + maxDamageMod : maxDamageMod);
		rangeMod =  (bender.canUseSubElement(SubElement.BLUE_FIRE) ? (BlueFireAbility.getRangeFactor() * range - range) + rangeMod : rangeMod);

		if (this.bender.isAvatarState()) {
			this.chargeTime = getConfig().getLong("Abilities.Avatar.AvatarState.Fire.FireBlast.Charged.ChargeTime");
			this.minDamage = getConfig().getDouble("Abilities.Avatar.AvatarState.Fire.FireBlast.Charged.MinimumDamage");
			this.maxDamage = getConfig().getDouble("Abilities.Avatar.AvatarState.Fire.FireBlast.Charged.MaximumDamage");
		}

		this.chargeTime += chargeTimeMod;
		this.minDamage += minDamageMod;
		this.maxDamage += maxDamageMod;
		this.range += rangeMod;
	}

	public static boolean annihilateBlasts(final Location location, final double radius, final LivingEntity source) {
		boolean broke = false;
		for (final FireBlastCharged chargedBlast : getAbilities(FireBlastCharged.class)) {
			if (!chargedBlast.launched) {
				continue;
			}

			final Location fireBlastLocation = chargedBlast.location;
			if (location.getWorld().equals(fireBlastLocation.getWorld()) && !source.equals(chargedBlast.caster)) {
				if (location.distanceSquared(fireBlastLocation) <= radius * radius) {
					chargedBlast.explode();
					broke = true;
				}
			}
		}
		return broke;
	}

	public static FireBlastCharged getFireball(final Entity entity) {
		return entity != null ? EXPLOSIONS.get(entity) : null;
	}

	public static boolean isCharging(final LivingEntity caster) {
		for (final FireBlastCharged chargedBlast : getAbilities(caster, FireBlastCharged.class)) {
			if (!chargedBlast.launched) {
				return true;
			}
		}
		return false;
	}

	public static void removeFireballsAroundPoint(final Location location, final double radius) {
		for (final FireBlastCharged fireball : getAbilities(FireBlastCharged.class)) {
			if (!fireball.launched) {
				continue;
			}
			final Location fireblastlocation = fireball.location;
			if (location.getWorld().equals(fireblastlocation.getWorld())) {
				if (location.distanceSquared(fireblastlocation) <= radius * radius) {
					fireball.remove();
				}
			}
		}
	}

	public void dealDamage(final Entity entity) {
		if (this.explosion == null) {
			return;
		}

		double distance = 0;
		if (entity.getWorld().equals(this.explosion.getWorld())) {
			distance = entity.getLocation().distance(this.explosion.getLocation());
		}
		if (distance > this.damageRadius) {
			return;
		} else if (distance < this.innerRadius) {
			DamageHandler.damageEntity(entity, this.maxDamage, this);
			return;
		}

		final double slope = -(this.maxDamage * .5) / (this.damageRadius - this.innerRadius);
		double damage = slope * (distance - this.innerRadius) + this.maxDamage;
		if (damage < this.minDamage) {
			damage = this.minDamage;
		}

		DamageHandler.damageEntity(entity, damage, this);
		AirAbility.breakBreathbendingHold(entity);
	}

	public void explode() {
		boolean explode = true;
		for (final Block block : GeneralMethods.getBlocksAroundPoint(this.location, 3)) {
			if (RegionProtection.isRegionProtected(this, block.getLocation())) {
				explode = false;
				break;
			}
		}

		if (explode) {
			if (this.canDamageBlocks && this.explosionRadius > 0 && canFireGrief()) {
				this.explosion = this.caster.getWorld().spawn(this.location, TNTPrimed.class);
				this.explosion.setFuseTicks(0);
				double yield = this.explosionRadius;

				if (!this.bender.isAvatarState()) {
					yield = getDayFactor(yield, this.caster.getWorld());
				} else {
					yield = AvatarState.getValue(yield);
				}

				this.explosion.setYield((float) yield);
				EXPLOSIONS.put(this.explosion, this);
			} else {
				final List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(this.location, this.damageRadius);
				for (final Entity entity : entities) {
					if (entity instanceof LivingEntity) {
						final double slope = -(this.maxDamage * .5) / (this.damageRadius - this.innerRadius);
						double damage = 0;
						if (entity.getWorld().equals(this.location.getWorld())) {
							damage = slope * (entity.getLocation().distance(this.location) - this.innerRadius) + this.maxDamage;
						}
						if (damage < this.minDamage) {
							damage = this.minDamage;
						}

						DamageHandler.damageEntity(entity, damage, this);
					}
				}
				this.location.getWorld().playSound(this.location, Sound.ENTITY_GENERIC_EXPLODE, 5, 1);
				ParticleEffect.EXPLOSION_HUGE.display(this.location, 1, 0, 0, 0);
			}
		}

		this.ignite(this.location);
		this.remove();
	}

	@Override
	public boolean isExplosiveAbility() {
		return isCanDamageBlocks();
	}

	private void executeFireball() {
		for (final Block block : GeneralMethods.getBlocksAroundPoint(this.location, this.collisionRadius)) {
			playFirebendingParticles(block.getLocation(), 5, 0.5, 0.5, 0.5);
			if ((new Random()).nextInt(4) == 0) {
				playFirebendingSound(this.location);
			}

		}

		boolean exploded = false;
		for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(this.location, this.collisionRadius)) {
			if (entity.getEntityId() == this.caster.getEntityId() || RegionProtection.isRegionProtected(this, entity.getLocation())) {
				continue;
			}
			entity.setFireTicks((int) (this.fireTicks * 20));
			if (entity instanceof LivingEntity) {
				if (!exploded) {
					this.explode();
					exploded = true;
				}
				this.dealDamage(entity);
			}
		}
	}

	private void ignite(final Location location) {
		for (final Block block : GeneralMethods.getBlocksAroundPoint(location, this.collisionRadius)) {
			if (isIgnitable(block)) {
				createTempFire(block.getLocation());
			}
		}
	}

	@Override
	public void progress() {
		if (!this.bender.canBendIgnoreBinds(this) && !this.launched) {
			this.remove();
			return;
		} else if (!this.bender.canBendIgnoreCooldowns(CoreAbility.getAbility("FireBlast")) && !this.launched) {
			this.remove();
			return;
		} else if (!this.bender.isSneaking() && !this.charged) {
			this.remove();
			return;
		}

		if (System.currentTimeMillis() > this.getStartTime() + this.chargeTime) {
			this.charged = true;
		}
		if (!this.bender.isSneaking() && !this.launched) {
			this.launched = true;
			this.location = this.caster.getEyeLocation();
			this.origin = this.location.clone();
			this.direction = this.location.getDirection().normalize().multiply(this.collisionRadius);
		}

		if (System.currentTimeMillis() > this.time + this.interval) {
			if (this.launched) {
				if (RegionProtection.isRegionProtected(this, this.location)) {
					this.remove();
					return;
				}
			}

			this.time = System.currentTimeMillis();

			if (!this.launched && !this.charged) {
				return;
			} else if (!this.launched) {
				playFirebendingParticles(this.caster.getEyeLocation().clone().add(this.caster.getEyeLocation().getDirection().clone()), 3, .001, .001, .001);
				return;
			}

			if (GeneralMethods.checkDiagonalWall(this.location, this.direction)) {
				this.explode();
				return;
			}
			this.location = this.location.clone().add(this.direction);
			if (this.location.distanceSquared(this.origin) > this.range * this.range) {
				this.remove();
				return;
			}

			if (GeneralMethods.isSolid(this.location.getBlock())) {
				this.explode();
				return;
			} else if (this.location.getBlock().isLiquid()) {
				this.remove();
				return;
			}
			this.executeFireball();
		}
	}

	@Override
	public void remove() {
		super.remove();
		if (this.charged) {
			this.bender.addCooldown(this);
		}
	}

	@Override
	public String getName() {
		return "FireBlastCharged";
	}

	@Override
	public Location getLocation() {
		return this.location != null ? this.location : this.origin;
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
	public boolean isCollidable() {
		return this.launched;
	}

	@Override
	public double getCollisionRadius() {
		return this.collisionRadius;
	}

	public static Map<Entity, FireBlastCharged> getExplosions() {
		return EXPLOSIONS;
	}

}