package com.projectkorra.projectkorra.airbending;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.legacy.AirAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.legacy.ElementalAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.configuration.configs.abilities.air.AirSwipeConfig;
import com.projectkorra.projectkorra.earthbending.lava.LavaFlow;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;

public class AirSwipe extends AirAbility<AirSwipeConfig> {

	// Limiting the entities reduces the risk of crashing.
	private static final int MAX_AFFECTABLE_ENTITIES = 10;

	private boolean charging;
	@Attribute("Arc")
	private int arc;
	private int particles;
	@Attribute("ArcIncrement")
	private int arcIncrement;
	@Attribute(Attribute.CHARGE_DURATION)
	private long maxChargeTime;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute(Attribute.KNOCKBACK)
	private double pushFactor;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.RADIUS)
	private double radius;
	private double maxChargeFactor;
	private Location origin;
	private Random random;
	private Map<Vector, Location> elements;
	private ArrayList<Entity> affectedEntities;

	public AirSwipe(final AirSwipeConfig config, final Player player) {
		this(config, player, false);
	}

	public AirSwipe(final AirSwipeConfig config, final Player player, final boolean charging) {
		super(config, player);

		if (CoreAbility.hasAbility(player, AirSwipe.class)) {
			for (final AirSwipe ability : CoreAbility.getAbilities(player, AirSwipe.class)) {
				if (ability.charging) {
					ability.launch();
					ability.charging = false;
					return;
				}
			}
		}

		this.charging = charging;
		this.origin = GeneralMethods.getMainHandLocation(player);
		this.particles = config.AnimationParticleAmount;
		this.arc = config.Arc;
		this.arcIncrement = config.StepSize;
		this.maxChargeTime = config.MaxChargeTime;
		this.cooldown = config.Cooldown;
		this.damage = config.Damage;
		this.pushFactor = config.PushFactor;
		this.speed = config.Speed * (ProjectKorra.time_step / 1000.0);
		this.range = config.Range;
		this.radius = config.Radius;
		this.maxChargeFactor = config.MaxChargeFactor;
		this.random = new Random();
		this.elements = new ConcurrentHashMap<>();
		this.affectedEntities = new ArrayList<>();

		if (this.bPlayer.isOnCooldown(this) || player.getEyeLocation().getBlock().isLiquid()) {
			this.remove();
			return;
		}

		if (!this.bPlayer.canBend(this)) {
			this.remove();
			return;
		}

		if (!charging) {
			this.launch();
		}

		if (this.bPlayer.isAvatarState()) {
			this.cooldown = config.AvatarState_Cooldown;
			this.damage = config.AvatarState_Damage;
			this.pushFactor = config.AvatarState_PushFactor;
			this.range = config.AvatarState_Range;
			this.radius = config.AvatarState_Radius;
		}

		this.start();
	}

	/**
	 * This method was used for the old collision detection system. Please see
	 * {@link Collision} for the new system.
	 */
	@Deprecated
	public static boolean removeSwipesAroundPoint(final Location loc, final double radius) {
		boolean removed = false;
		for (final AirSwipe aswipe : getAbilities(AirSwipe.class)) {
			for (final Vector vec : aswipe.elements.keySet()) {
				final Location vectorLoc = aswipe.elements.get(vec);
				if (vectorLoc != null && vectorLoc.getWorld().equals(loc.getWorld())) {
					if (vectorLoc.distanceSquared(loc) <= radius * radius) {
						aswipe.remove();
						removed = true;
					}
				}
			}
		}
		return removed;
	}

	private void advanceSwipe() {
		this.affectedEntities.clear();
		for (final Vector direction : this.elements.keySet()) {
			Location location = this.elements.get(direction);
			if (direction != null && location != null) {
				location = location.clone().add(direction.clone().multiply(this.speed));
				this.elements.put(direction, location);

				if (location.distanceSquared(this.origin) > this.range * this.range || GeneralMethods.isRegionProtectedFromBuild(this, location)) {
					this.elements.clear();
				} else {
					final Block block = location.getBlock();
					if (!ElementalAbility.isTransparent(this.player, block)) {
						this.remove();
						return;
					}

					for (final Block testblock : GeneralMethods.getBlocksAroundPoint(location, this.radius)) {
						if (testblock.getType() == Material.FIRE) {
							testblock.setType(Material.AIR);
						}
					}

					if (!isAir(block.getType())) {
						if (block.getType().equals(Material.SNOW)) {
							continue;
						} else if (isPlant(block.getType())) {
							block.breakNaturally();
						} else {
							this.elements.remove(direction);
						}
						if (isLava(block)) {
							if (LavaFlow.isLavaFlowBlock(block)) {
								LavaFlow.removeBlock(block); // TODO: Make more generic for future lava generating moves.
							} else if (block.getBlockData() instanceof Levelled && ((Levelled) block.getBlockData()).getLevel() == 0) {
								new TempBlock(block, Material.OBSIDIAN);
							} else {
								new TempBlock(block, Material.COBBLESTONE);
							}
						}
					} else {
						playAirbendingParticles(location, this.particles, 0.2F, 0.2F, 0);
						if (this.random.nextInt(4) == 0) {
							playAirbendingSound(location);
						}
						this.affectPeople(location, direction);
					}
				}
			}
		}
		if (this.elements.isEmpty()) {
			this.remove();
		}
	}

	private void affectPeople(final Location location, final Vector direction) {
		final List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(location, this.radius);
		final Vector fDirection = direction.clone();

		for (int i = 0; i < entities.size(); i++) {
			final Entity entity = entities.get(i);
			final AirSwipe abil = this;
			new BukkitRunnable() {
				@Override
				public void run() {
					if (GeneralMethods.isRegionProtectedFromBuild(AirSwipe.this, entity.getLocation())) {
						return;
					}
					if (entity.getEntityId() != AirSwipe.this.player.getEntityId() && entity instanceof LivingEntity) {
						if (entity instanceof Player) {
							if (Commands.invincible.contains(((Player) entity).getName())) {
								return;
							}
						}
						if (entities.size() < MAX_AFFECTABLE_ENTITIES) {

							GeneralMethods.setVelocity(entity, fDirection.multiply(AirSwipe.this.pushFactor));

						}
						if (!AirSwipe.this.affectedEntities.contains(entity)) {
							if (AirSwipe.this.damage != 0) {
								DamageHandler.damageEntity(entity, AirSwipe.this.damage, abil);
							}
							AirSwipe.this.affectedEntities.add(entity);
						}
						breakBreathbendingHold(entity);
						AirSwipe.this.elements.remove(direction);
					} else if (entity.getEntityId() != AirSwipe.this.player.getEntityId() && !(entity instanceof LivingEntity)) {

						GeneralMethods.setVelocity(entity, fDirection.multiply(AirSwipe.this.pushFactor));

					}
				}
			}.runTaskLater(ProjectKorra.plugin, i / MAX_AFFECTABLE_ENTITIES);
		}
	}

	private void launch() {
		this.bPlayer.addCooldown("AirSwipe", this.cooldown);
		this.origin = this.player.getEyeLocation();
		for (double i = -this.arc; i <= this.arc; i += this.arcIncrement) {
			final double angle = Math.toRadians(i);
			final Vector direction = this.player.getEyeLocation().getDirection().clone();

			double x, z, vx, vz;
			x = direction.getX();
			z = direction.getZ();

			vx = x * Math.cos(angle) - z * Math.sin(angle);
			vz = x * Math.sin(angle) + z * Math.cos(angle);

			direction.setX(vx);
			direction.setZ(vz);

			this.elements.put(direction, this.origin);
		}
	}

	@Override
	public void progress() {
		if (!this.bPlayer.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		}

		if (this.player.isDead() || !this.player.isOnline()) {
			this.remove();
			return;
		}

		if (!this.charging) {
			if (this.elements.isEmpty()) {
				this.remove();
				return;
			}
			this.advanceSwipe();
		} else {
			if (!this.player.isSneaking()) {
				double factor = 1;
				if (System.currentTimeMillis() >= this.getStartTime() + this.maxChargeTime) {
					factor = this.maxChargeFactor;
				} else {
					factor = this.maxChargeFactor * (System.currentTimeMillis() - this.getStartTime()) / this.maxChargeTime;
				}

				this.charging = false;
				this.launch();
				factor = Math.max(1, factor);
				this.damage *= factor;
				this.pushFactor *= factor;
			} else if (System.currentTimeMillis() >= this.getStartTime() + this.maxChargeTime) {
				playAirbendingParticles(this.player.getEyeLocation(), this.particles);
			}
		}
	}

	@Override
	public String getName() {
		return "AirSwipe";
	}

	@Override
	public Location getLocation() {
		return this.elements.size() != 0 ? this.elements.values().iterator().next() : null;
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
		return this.origin != null;
	}

	@Override
	public double getCollisionRadius() {
		return this.getRadius();
	}

	@Override
	public List<Location> getLocations() {
		final ArrayList<Location> locations = new ArrayList<>();
		for (final Location swipeLoc : this.elements.values()) {
			locations.add(swipeLoc);
		}
		return locations;
	}

	public Location getOrigin() {
		return this.origin;
	}

	public void setOrigin(final Location origin) {
		this.origin = origin;
	}

	public boolean isCharging() {
		return this.charging;
	}

	public void setCharging(final boolean charging) {
		this.charging = charging;
	}

	public int getArc() {
		return this.arc;
	}

	public void setArc(final int arc) {
		this.arc = arc;
	}

	public int getParticles() {
		return this.particles;
	}

	public void setParticles(final int particles) {
		this.particles = particles;
	}

	public static int getMaxAffectableEntities() {
		return MAX_AFFECTABLE_ENTITIES;
	}

	public long getMaxChargeTime() {
		return this.maxChargeTime;
	}

	public void setMaxChargeTime(final long maxChargeTime) {
		this.maxChargeTime = maxChargeTime;
	}

	public double getDamage() {
		return this.damage;
	}

	public void setDamage(final double damage) {
		this.damage = damage;
	}

	public double getPushFactor() {
		return this.pushFactor;
	}

	public void setPushFactor(final double pushFactor) {
		this.pushFactor = pushFactor;
	}

	public double getSpeed() {
		return this.speed;
	}

	public void setSpeed(final double speed) {
		this.speed = speed;
	}

	public double getRange() {
		return this.range;
	}

	public void setRange(final double range) {
		this.range = range;
	}

	public double getRadius() {
		return this.radius;
	}

	public void setRadius(final double radius) {
		this.radius = radius;
	}

	public double getMaxChargeFactor() {
		return this.maxChargeFactor;
	}

	public void setMaxChargeFactor(final double maxChargeFactor) {
		this.maxChargeFactor = maxChargeFactor;
	}

	public Map<Vector, Location> getElements() {
		return this.elements;
	}

	public ArrayList<Entity> getAffectedEntities() {
		return this.affectedEntities;
	}

	public void setCooldown(final long cooldown) {
		this.cooldown = cooldown;
	}

	public int getArcIncrement() {
		return this.arcIncrement;
	}

	public void setArcIncrement(final int arcIncrement) {
		this.arcIncrement = arcIncrement;
	}
	
	@Override
	public Class<AirSwipeConfig> getConfigType() {
		return AirSwipeConfig.class;
	}

}
