package com.projectkorra.projectkorra.firebending.combo;

import com.projectkorra.projectkorra.Bender;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.region.RegionProtection;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.firebending.util.FireDamageTimer;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;

import lombok.Getter;
import lombok.Setter;

/***
 * Is only here for legacy purposes. All fire combos used to use a form of this
 * stream for all their progress methods. If someone else was reliant on that,
 * they can use this ability instead.
 */
@Getter
@Setter
public class FireComboStream extends BukkitRunnable {
	private int particleAmount;
	private boolean useNewParticles;
	private boolean air;
	private boolean cancelled;
	private boolean collides;
	private boolean singlePoint;
	private int density;
	private int checkCollisionDelay;
	private int checkCollisionCounter;
	private double xSpread, ySpread, zSpread;
	private double collisionRadius;
	private final double speed;
	private final double distance;
	private double damage;
	private double fireTicks;
	private double knockback;
	ParticleEffect particleEffect;
	private final LivingEntity caster;
	private final Bender bender;
	private final CoreAbility coreAbility;
	private final Vector direction;
	private final Location initialLocation;
	private final Location location;

	public FireComboStream(final LivingEntity caster, final CoreAbility coreAbility, final Vector direction, final Location location, final double distance, final double speed) {
		this.useNewParticles = false;
		this.particleAmount = 1;
		this.cancelled = false;
		this.collides = true;
		this.singlePoint = false;
		this.density = 1;
		this.checkCollisionDelay = 1;
		this.checkCollisionCounter = 0;
		this.collisionRadius = 2;
		this.caster = caster;
		this.bender = Bender.get(caster);
		this.particleEffect = FireAbility.FireParticle.byName(FireAbility.particleType(caster)).getEffect();
		this.coreAbility = coreAbility;
		this.direction = direction;
		this.speed = speed;
		this.initialLocation = location.clone();
		this.location = location.clone();
		this.distance = distance;
	}

	@Override
	public void run() {
		final Block block = this.location.getBlock();

		if (RegionProtection.isRegionProtected(this.caster, this.location, coreAbility)) {
			this.remove();
			return;
		}

		if (!ElementalAbility.isAir(block.getRelative(BlockFace.UP).getType()) && !ElementalAbility.isPlant(block)) {
			this.remove();
			return;
		}
		for (int i = 0; i < this.density; i++) {
			if (this.air) {
				final String color = AirAbility.particleColor(caster);
				GeneralMethods.displayColoredParticle(this.location, ParticleEffect.SPELL_MOB, color, this.particleAmount, 0, 0, 0);
			} else if (this.useNewParticles) {
				this.particleEffect.display(this.location, this.particleAmount, this.xSpread, this.ySpread, this.zSpread, 0.1);
			} else {
				this.location.getWorld().playEffect(this.location, Effect.MOBSPAWNER_FLAMES, 0, 15);
			}
		}

		if (GeneralMethods.checkDiagonalWall(this.location, this.direction)) {
			this.remove();
			return;
		}

		this.location.add(this.direction.normalize().multiply(this.speed));
		
		try {
			this.location.checkFinite();
		} catch (IllegalArgumentException e) {
			this.remove();
			return;
		}
		
		if (this.initialLocation.distanceSquared(this.location) > this.distance * this.distance || !Double.isFinite(this.collisionRadius)) {
			this.remove();
			return;
		} else if (this.collides && this.checkCollisionCounter % this.checkCollisionDelay == 0) {
			for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(this.location, this.collisionRadius)) {
				if (entity instanceof LivingEntity && !entity.equals(this.coreAbility.getPlayer()) && !entity.isDead()) {
					this.collision((LivingEntity) entity, this.direction, this.coreAbility);
				}
			}
		}

		this.checkCollisionCounter++;
		if (this.singlePoint) {
			this.remove();
		}
	}

	public void collision(final LivingEntity entity, final Vector direction, final CoreAbility coreAbility) {
		entity.getLocation().getWorld().playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_HURT, 0.3f, 0.3f);

		if (coreAbility.getName().equalsIgnoreCase("FireKick")) {
			final FireKick fireKick = CoreAbility.getAbility(this.caster, FireKick.class);

			if (!fireKick.getAffectedEntities().contains(entity)) {
				fireKick.getAffectedEntities().add(entity);
				DamageHandler.damageEntity(entity, this.damage, coreAbility);
			}
		} else if (coreAbility.getName().equalsIgnoreCase("FireSpin")) {
			final FireSpin fireSpin = CoreAbility.getAbility(this.caster, FireSpin.class);

			if (entity instanceof Player) {
				if (Commands.invincible.contains(((Player) entity).getName())) {
					return;
				}
			}
			if (!fireSpin.getAffectedEntities().contains(entity)) {
				fireSpin.getAffectedEntities().add(entity);
				final double newKnockback = this.bender.isAvatarState() ? this.knockback + 0.5 : this.knockback;
				DamageHandler.damageEntity(entity, this.damage, coreAbility);
				GeneralMethods.setVelocity(coreAbility, entity, direction.normalize().multiply(newKnockback));
			}
		} else if (coreAbility.getName().equalsIgnoreCase("JetBlaze")) {
			final JetBlaze jetBlaze = CoreAbility.getAbility(this.caster, JetBlaze.class);

			if (!jetBlaze.getAffectedEntities().contains(entity)) {
				jetBlaze.getAffectedEntities().add(entity);
				DamageHandler.damageEntity(entity, this.damage, coreAbility);
				entity.setFireTicks((int) (this.fireTicks * 20));
				new FireDamageTimer(entity, this.caster, coreAbility);
			}
		} else if (coreAbility.getName().equalsIgnoreCase("FireWheel")) {
			final FireWheel fireWheel = CoreAbility.getAbility(this.caster, FireWheel.class);

			if (!fireWheel.getAffectedEntities().contains(entity)) {
				fireWheel.getAffectedEntities().add(entity);
				DamageHandler.damageEntity(entity, this.damage, coreAbility);
				entity.setFireTicks((int) (this.fireTicks * 20));
				new FireDamageTimer(entity, this.caster, coreAbility);
				this.remove();
			}
		}
	}

	@Override
	public void cancel() {
		this.remove();
	}

	public Vector getDirection() {
		return this.direction.clone();
	}

	@Override
	public boolean isCancelled() {
		return this.cancelled;
	}

	public void remove() {
		super.cancel();
		this.cancelled = true;
	}

	public CoreAbility getAbility() {
		return this.coreAbility;
	}

	public void setSpread(final float spread) {
		this.xSpread = spread;
		this.ySpread = spread;
		this.zSpread = spread;
	}

	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
	}
}
