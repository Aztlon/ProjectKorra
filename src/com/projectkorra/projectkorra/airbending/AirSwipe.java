package com.projectkorra.projectkorra.airbending;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.earthbending.lava.LavaFlow;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AirSwipe extends AirAbility {

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
	private Map<Vector, Location> streams;
	private ArrayList<Entity> affectedEntities;

	public AirSwipe(final LivingEntity caster) {
		this(caster, false);
	}

	public AirSwipe(final LivingEntity caster, final boolean charging) {
		super(caster);

		if (charging && !bender.hasUnlocked("AirSwipeCharged")) return;

		if (hasAbility(caster, AirSwipe.class)) {
			for (final AirSwipe ability : CoreAbility.getAbilities(caster, AirSwipe.class)) {
				if (ability.charging) {
					ability.launch();
					ability.charging = false;
					return;
				}
			}
		}

		this.charging = charging;
		this.origin = GeneralMethods.getMainHandLocation(caster);
		this.particles = getConfig().getInt("Abilities.Air.AirSwipe.Particles");
		this.arc = getConfig().getInt("Abilities.Air.AirSwipe.Arc");
		this.arcIncrement = getConfig().getInt("Abilities.Air.AirSwipe.StepSize");
		this.maxChargeTime = getConfig().getLong("Abilities.Air.AirSwipe.MaxChargeTime");
		this.cooldown = getConfig().getLong("Abilities.Air.AirSwipe.Cooldown");
		this.damage = getConfig().getDouble("Abilities.Air.AirSwipe.Damage");
		this.pushFactor = getConfig().getDouble("Abilities.Air.AirSwipe.Push");
		this.speed = getConfig().getDouble("Abilities.Air.AirSwipe.Speed") * (ProjectKorra.time_step / 1000.0);
		this.range = getConfig().getDouble("Abilities.Air.AirSwipe.Range");
		this.radius = getConfig().getDouble("Abilities.Air.AirSwipe.Radius");
		this.maxChargeFactor = getConfig().getDouble("Abilities.Air.AirSwipe.ChargeFactor");
		this.random = new Random();
		this.streams = new ConcurrentHashMap<>();
		this.affectedEntities = new ArrayList<>();

		if (caster.getEyeLocation().getBlock().isLiquid()) {
			this.remove();
			return;
		}

		if (!this.bender.canBend(this)) {
			this.remove();
			return;
		}

		if (this.bender.asPlayer().map(BendingPlayer::isAvatarState).orElse(false)) {
			this.cooldown = getConfig().getLong("Abilities.Avatar.AvatarState.Air.AirSwipe.Cooldown");
			this.damage = getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirSwipe.Damage");
			this.pushFactor = getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirSwipe.Push");
			this.range = getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirSwipe.Range");
			this.radius = getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirSwipe.Radius");
		}

		this.start();
		if (!isRemoved() && !charging) {
			this.launch();
		}
	}

	/**
	 * This method was used for the old collision detection system. Please see
	 * {@link Collision} for the new system.
	 */
	@Deprecated
	public static boolean removeSwipesAroundPoint(final Location loc, final double radius) {
		boolean removed = false;
		for (final AirSwipe aswipe : getAbilities(AirSwipe.class)) {
			for (final Vector vec : aswipe.streams.keySet()) {
				final Location vectorLoc = aswipe.streams.get(vec);
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
		for (final Vector direction : this.streams.keySet()) {
			Location location = this.streams.get(direction);
			if (direction != null && location != null) {

				BlockIterator blocks = new BlockIterator(this.getLocation().getWorld(), location.toVector(), direction, 0, (int) Math.ceil(direction.clone().multiply(speed).length()));

				while (blocks.hasNext()) {
					if(!checkLocation(blocks.next(), direction)) {
						this.streams.remove(direction);
						break;
					}
				}
				
				if(!this.streams.containsKey(direction)) {
					continue;
				}

				location = location.clone().add(direction.clone().multiply(this.speed));
				this.streams.put(direction, location);
				playAirbendingParticles(location, this.particles, 0.2F, 0.2F, 0);
				if (this.random.nextInt(4) == 0) {
					playAirbendingSound(location);
				}
				this.affectPeople(location, direction);
			}
		}
		if (this.streams.isEmpty()) {
			this.remove();
		}
	}

	public boolean checkLocation(Block block, Vector direction) {
		if (GeneralMethods.checkDiagonalWall(block.getLocation(), direction) || !block.isPassable()) {
			return false;
		}  else {
			if (block.getLocation().distanceSquared(this.origin) > this.range * this.range || RegionProtection.isRegionProtected(this, block.getLocation())) {
				this.streams.clear();
			} else {
				if (!ElementalAbility.isTransparent(this.caster, block) || !block.isPassable()) {
					return false;
				}

				for (final Block testblock : GeneralMethods.getBlocksAroundPoint(block.getLocation(), this.radius)) {
					if (FireAbility.isFire(testblock.getType())) {
						testblock.setType(Material.AIR);
					}
				}

				if (!isAir(block.getType())) {
					if (block.getType().equals(Material.SNOW)) {
						return true;
					} else if (isPlant(block.getType())) {
						block.breakNaturally();
						return false;
					} else if (isLava(block)) {
						if (LavaFlow.isLavaFlowBlock(block)) {
							LavaFlow.removeBlock(block);
							return false;// TODO: Make more generic for future lava generating moves.
						} else if (block.getBlockData() instanceof Levelled && ((Levelled) block.getBlockData()).getLevel() == 0) {
							new TempBlock(block, Material.OBSIDIAN, this);
							return false;
						} else {
							new TempBlock(block, Material.COBBLESTONE, this);
							return false;
						}
					} else {
						return false;
					}
				}
			}
		}
		return true;
	}
	private void affectPeople(final Location location, final Vector direction) {
		final List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(this, location, this.radius);
		final Vector fDirection = direction.clone();

		for (int i = 0; i < entities.size(); i++) {
			Location entityLocation = entities.get(i).getLocation();
			Vector dir = new Vector(entityLocation.getX() - location.getX(), entityLocation.getY() - location.getY(), entityLocation.getZ() - location.getZ());
			if (GeneralMethods.checkDiagonalWall(location, dir)) {
				entities.remove(entities.get(i--));
			}
		}

		for (int i = 0; i < entities.size(); i++) {
			final Entity entity = entities.get(i);
			final AirSwipe abil = this;
			new BukkitRunnable() {
				@Override
				public void run() {
					if (!GeneralMethods.canAbilityTarget(AirSwipe.this, entity)) {
						return;
					}
					if (RegionProtection.isRegionProtected(AirSwipe.this, entity.getLocation())) {
						return;
					}
					if (entity.getEntityId() != AirSwipe.this.caster.getEntityId() && entity instanceof LivingEntity) {
						if (entity instanceof Player) {
							if (Commands.invincible.contains(((Player) entity).getName())) {
								return;
							}
						}
						if (entities.size() < MAX_AFFECTABLE_ENTITIES) {
							GeneralMethods.setVelocity(AirSwipe.this, entity, fDirection.multiply(AirSwipe.this.pushFactor));
						}
						if (!AirSwipe.this.affectedEntities.contains(entity)) {
							if (AirSwipe.this.damage != 0) {
								DamageHandler.damageEntity(entity, AirSwipe.this.damage, abil);
							}
							AirSwipe.this.affectedEntities.add(entity);
						}
						breakBreathbendingHold(entity);
					} else if (entity.getEntityId() != AirSwipe.this.caster.getEntityId() && !(entity instanceof LivingEntity)) {
						GeneralMethods.setVelocity(AirSwipe.this, entity, fDirection.multiply(AirSwipe.this.pushFactor));
					}
				}
			}.runTaskLater(ProjectKorra.plugin, i / MAX_AFFECTABLE_ENTITIES);
		}
	}

	private void launch() {
		this.bender.addCooldown("AirSwipe", this.cooldown);
		this.origin = this.caster.getEyeLocation();
		for (double i = -this.arc; i <= this.arc; i += this.arcIncrement) {
			final double angle = Math.toRadians(i);
			final Vector direction = this.caster.getEyeLocation().getDirection().clone();

			Vector xz = GeneralMethods.rotateVectorAroundVector(direction, new Vector(-direction.getZ(), 0, direction.getX()).normalize(), 0);

			this.streams.put(direction.clone().multiply(Math.cos(angle))
					.add(xz.clone().multiply(Math.sin(angle))).normalize(), this.origin);
		}
	}

	@Override
	public void progress() {
		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		}

		if (this.caster.isDead() || (bPlayer != null && !player.isOnline())) {
			this.remove();
			return;
		}

		if (!this.charging) {
			if (this.streams.isEmpty()) {
				this.remove();
				return;
			}
			this.advanceSwipe();
		} else {
			if (!this.bender.isSneaking()) {
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
				playAirbendingParticles(this.caster.getEyeLocation(), this.particles);
			}
		}
	}

	@Override
	public String getName() {
		return "AirSwipe";
	}

	@Override
	public Location getLocation() {
		return !this.streams.isEmpty() ? this.streams.values().iterator().next() : null;
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
		return new ArrayList<>(this.streams.values());
	}

	public static int getMaxAffectableEntities() {
		return MAX_AFFECTABLE_ENTITIES;
	}

	public Map<Vector, Location> getElements() {
		return this.streams;
	}

}
