package com.projectkorra.projectkorra.earthbending;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EarthSmash extends EarthAbility {

	public enum State {
		START, LIFTING, LIFTED, GRABBED, SHOT, FLYING, REMOVED
	}

	@Attribute("AllowGrab")
	private boolean allowGrab;
	@Attribute("AllowFlight")
	private boolean allowFlight;
	private int animationCounter;
	private int progressCounter;
	private int requiredBendableBlocks;
	private int maxBlocksToPassThrough;
	private long delay;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.CHARGE_DURATION)
	private long chargeTime;
	@Attribute(Attribute.DURATION)
	private long duration;
	@Attribute("Flight" + Attribute.DURATION)
	private long flightDuration;
	private long flightStartTime;
	private long shootAnimationInterval;
	private long flightAnimationInterval;
	private long liftAnimationInterval;
	@Attribute(Attribute.SELECT_RANGE)
	private double selectRange;
	@Attribute("GrabRange")
	private double grabRange;
	@Attribute(Attribute.RANGE)
	private double shootRange;
	@Attribute("Min" + Attribute.DAMAGE)
	private double minDamage;
	@Attribute(Attribute.DAMAGE)
	private double maxDamage;
	@Attribute(Attribute.KNOCKBACK)
	private double knockback;
	@Attribute(Attribute.KNOCKUP)
	private double knockup;
	private double liftKnockup;
	private double liftRange;
	@Attribute(Attribute.SPEED)
	private double flightSpeed;
	private double grabbedDistance;
	private double grabDetectionRadius;
	private double hitRadius;
	private double flightDetectionRadius;
	private State state;
	private Block origin;
	private Location location;
	private Location destination;
	private ArrayList<Entity> affectedEntities;
	private ArrayList<BlockRepresenter> currentBlocks;
	private ArrayList<TempBlock> affectedBlocks;

	public EarthSmash(final LivingEntity caster) {
		this(caster, ClickType.SHIFT_DOWN);
	}

	public EarthSmash(final LivingEntity caster, final ClickType type) {
		super(caster);

		this.state = State.START;
		this.requiredBendableBlocks = getConfig().getInt("Abilities.Earth.EarthSmash.RequiredBendableBlocks");
		this.maxBlocksToPassThrough = getConfig().getInt("Abilities.Earth.EarthSmash.MaxBlocksToPassThrough");
		this.setFields();
		this.affectedEntities = new ArrayList<>();
		this.currentBlocks = new ArrayList<>();
		this.affectedBlocks = new ArrayList<>();

		if (type == ClickType.SHIFT_DOWN || type == ClickType.SHIFT_UP && !bender.isSneaking()) {
			final EarthSmash flySmash = flyingInSmashCheck(caster);
			if (flySmash != null) {
				if (!bender.hasUnlocked("EarthSmashRide")) return;
				flySmash.state = State.FLYING;
				flySmash.caster = caster;
				flySmash.setFields();
				flySmash.flightStartTime = System.currentTimeMillis();
				return;
			}

			EarthSmash grabbedSmash = this.aimingAtSmashCheck(caster, State.LIFTED);
			if (grabbedSmash == null) {
				if (this.bender.isOnCooldown(this)) {
					return;
				}
				grabbedSmash = this.aimingAtSmashCheck(caster, State.SHOT);
				if (grabbedSmash != null && !bender.hasUnlocked("EarthSmashRedirect")) return;
			}

			if (grabbedSmash != null) {
				grabbedSmash.state = State.GRABBED;
				grabbedSmash.grabbedDistance = 0;
				if (grabbedSmash.location.getWorld().equals(caster.getWorld())) {
					grabbedSmash.grabbedDistance = grabbedSmash.location.distance(caster.getEyeLocation());
				}
				grabbedSmash.caster = caster;
				grabbedSmash.setFields();
				return;
			}

			this.start();
		} else if (type == ClickType.LEFT_CLICK && bender.isSneaking()) {
			for (final EarthSmash smash : getAbilities(EarthSmash.class)) {
				if (smash.state == State.GRABBED && smash.caster == caster) {
					smash.state = State.SHOT;
					smash.destination = caster.getEyeLocation().clone().add(caster.getEyeLocation().getDirection().normalize().multiply(smash.shootRange));
					smash.location.getWorld().playEffect(smash.location, Effect.GHAST_SHOOT, 0, 10);
				}
			}
		} else if (type == ClickType.RIGHT_CLICK && bender.isSneaking()) {
			final EarthSmash grabbedSmash = this.aimingAtSmashCheck(caster, State.GRABBED);
			if (grabbedSmash != null) {
				caster.teleport(grabbedSmash.location.clone().add(0, 2, 0));
				grabbedSmash.state = State.FLYING;
				grabbedSmash.caster = caster;
				grabbedSmash.setFields();
				grabbedSmash.flightStartTime = System.currentTimeMillis();
			}
		}
	}

	public void setFields() {
		this.shootAnimationInterval = getConfig().getLong("Abilities.Earth.EarthSmash.Shoot.AnimationInterval");
		this.flightAnimationInterval = getConfig().getLong("Abilities.Earth.EarthSmash.Flight.AnimationInterval");
		this.liftAnimationInterval = getConfig().getLong("Abilities.Earth.EarthSmash.LiftAnimationInterval");
		this.grabDetectionRadius = getConfig().getDouble("Abilities.Earth.EarthSmash.Grab.DetectionRadius");
		this.flightDetectionRadius = getConfig().getDouble("Abilities.Earth.EarthSmash.Flight.DetectionRadius");
		this.hitRadius = getConfig().getDouble("Abilities.Earth.EarthSmash.Shoot.CollisionRadius");
		this.allowGrab = getConfig().getBoolean("Abilities.Earth.EarthSmash.Grab.Enabled");
		this.allowFlight = getConfig().getBoolean("Abilities.Earth.EarthSmash.Flight.Enabled");
		this.selectRange = getConfig().getDouble("Abilities.Earth.EarthSmash.SelectRange");
		this.grabRange = getConfig().getDouble("Abilities.Earth.EarthSmash.Grab.Range");
		this.shootRange = getConfig().getDouble("Abilities.Earth.EarthSmash.Shoot.Range");
		this.minDamage = getConfig().getDouble("Abilities.Earth.EarthSmash.MinimumDamage");
		this.maxDamage = getConfig().getDouble("Abilities.Earth.EarthSmash.MaximumDamage");
		this.knockback = getConfig().getDouble("Abilities.Earth.EarthSmash.Knockback");
		this.knockup = getConfig().getDouble("Abilities.Earth.EarthSmash.Knockup");
		this.liftKnockup = getConfig().getDouble("Abilities.Earth.EarthSmash.Lift.Knockup");
		this.liftRange = getConfig().getDouble("Abilities.Earth.EarthSmash.Lift.Range");
		this.flightSpeed = getConfig().getDouble("Abilities.Earth.EarthSmash.Flight.Speed");
		this.chargeTime = getConfig().getLong("Abilities.Earth.EarthSmash.ChargeTime");
		this.cooldown = getConfig().getLong("Abilities.Earth.EarthSmash.Cooldown");
		this.flightDuration = getConfig().getLong("Abilities.Earth.EarthSmash.Flight.Duration");
		this.duration = getConfig().getLong("Abilities.Earth.EarthSmash.Duration");

		if (bender.isAvatarState()) {
			this.selectRange = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.EarthSmash.SelectRange");
			this.grabRange = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.EarthSmash.GrabRange");
			this.chargeTime = getConfig().getLong("Abilities.Avatar.AvatarState.Earth.EarthSmash.ChargeTime");
			this.cooldown = getConfig().getLong("Abilities.Avatar.AvatarState.Earth.EarthSmash.Cooldown");
			this.minDamage = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.EarthSmash.MinimumDamage");
			this.maxDamage = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.EarthSmash.MaximumDamage");
			this.knockback = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.EarthSmash.Knockback");
			this.flightSpeed = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.EarthSmash.FlightSpeed");
			this.flightDuration = getConfig().getLong("Abilities.Avatar.AvatarState.Earth.EarthSmash.FlightTimer");
			this.shootRange = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.EarthSmash.ShootRange");
		}
	}

	@Override
	public void progress() {
		this.progressCounter++;
		if (this.state == State.LIFTED && this.duration > 0 && System.currentTimeMillis() - this.getStartTime() > this.duration) {
			this.remove();
			return;
		}

		if (this.state == State.START) {
			if (!this.bender.canBend(this)) {
				this.remove();
				return;
			}
		} else if (this.state == State.FLYING || this.state == State.GRABBED) {
			if (!this.bender.canBendIgnoreCooldowns(this)) {
				this.remove();
				return;
			}
		}

		if (this.state == State.START && this.progressCounter > 1) {
			if (!this.bender.isSneaking()) {
				if (System.currentTimeMillis() - this.getStartTime() >= this.chargeTime) {
					this.origin = this.getEarthSourceBlock(this.selectRange);
					if (this.origin == null) {
						this.remove();
						return;
					} else if (TempBlock.isTempBlock(this.origin) && !isBendableEarthTempBlock(this.origin)) {
						this.remove();
						return;
					}
					this.bender.addCooldown(this);
					this.location = this.origin.getLocation();
					this.state = State.LIFTING;
					this.minDamage = applyMetalPowerFactor(this.minDamage, this.origin);
					this.maxDamage = applyMetalPowerFactor(this.maxDamage, this.origin);
				} else {
					this.remove();
					return;
				}
			} else if (System.currentTimeMillis() - this.getStartTime() > this.chargeTime) {
				final Location tempLoc = this.caster.getEyeLocation().add(this.caster.getEyeLocation().getDirection().normalize().multiply(1.2));
				tempLoc.add(0, 0.3, 0);
				ParticleEffect.SMOKE_NORMAL.display(tempLoc, 4, 0.3, 0.1, 0.3, 0);
			}
		} else if (this.state == State.LIFTING) {
			if (System.currentTimeMillis() - this.delay >= this.liftAnimationInterval) {
				this.delay = System.currentTimeMillis();
				this.animateLift();
			}
		} else if (this.state == State.GRABBED) {
			if (this.bender.isSneaking()) {
				this.revert();
				final Location oldLoc = this.location.clone();
				this.location = this.caster.getEyeLocation().add(this.caster.getEyeLocation().getDirection().normalize().multiply(this.grabbedDistance));

				// Check to make sure the new location is available to move to.
				for (final Block block : this.getBlocks()) {
					if (!ElementalAbility.isAir(block.getType()) && !this.isTransparent(block)) {
						this.location = oldLoc;
						break;
					}
				}
				this.draw();
			} else {
				this.state = State.LIFTED;
			}
		} else if (this.state == State.SHOT) {
			if (System.currentTimeMillis() - this.delay >= this.shootAnimationInterval) {
				this.delay = System.currentTimeMillis();
				if (RegionProtection.isRegionProtected(this, this.location)) {
					this.remove();
					return;
				}

				this.revert();
				this.location.add(GeneralMethods.getDirection(this.location, this.destination).normalize().multiply(1));
				if (this.location.distanceSquared(this.destination) < 4) {
					this.remove();
					return;
				}

				// If an earthsmash runs into too many blocks we should remove it.
				int badBlocksFound = 0;
				for (final Block block : this.getBlocks()) {
					if (!ElementalAbility.isAir(block.getType()) && (!this.isTransparent(block) || block.getType() == Material.WATER)) {
						badBlocksFound++;
					}
				}

				if (badBlocksFound > this.maxBlocksToPassThrough) {
					this.remove();
					return;
				}
				this.shootingCollisionDetection();
				this.draw();
				this.smashToSmashCollisionDetection();
			}
		} else if (this.state == State.FLYING) {
			if (!this.bender.isSneaking()) {
				this.remove();
				return;
			} else if (System.currentTimeMillis() - this.delay >= this.flightAnimationInterval) {
				this.delay = System.currentTimeMillis();
				if (RegionProtection.isRegionProtected(this, this.location)) {
					this.remove();
					return;
				}
				this.revert();
				this.destination = this.caster.getEyeLocation().clone().add(this.caster.getEyeLocation().getDirection().normalize().multiply(this.shootRange));
				final Vector direction = GeneralMethods.getDirection(this.location, this.destination).normalize();

				final List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(this.location.clone().add(0, 2, 0), this.flightDetectionRadius);
				if (entities.isEmpty()) {
					this.remove();
					return;
				}
				for (final Entity entity : entities) {
					if (RegionProtection.isRegionProtected(this, entity.getLocation()) || ((entity instanceof Player) && Commands.invincible.contains(entity.getName()))) {
						continue;
					}
					GeneralMethods.setVelocity(this, entity, direction.clone().multiply(this.flightSpeed));
				}

				// These values tend to work well when dealing with a person aiming upward or downward.
				if (direction.getY() < -0.35) {
					this.location = this.caster.getLocation().clone().add(0, -3.2, 0);
				} else if (direction.getY() > 0.35) {
					this.location = this.caster.getLocation().clone().add(0, -1.7, 0);
				} else {
					this.location = this.caster.getLocation().clone().add(0, -2.2, 0);
				}
				this.draw();
			}
			if (System.currentTimeMillis() - this.flightStartTime > this.flightDuration) {
				this.remove();
			}
		}
	}

	/**
	 * Begins animating the EarthSmash from the ground. The lift animation
	 * consists of 3 steps, and each one has to design the shape in the ground
	 * that removes the Earthbendable material. We also need to make sure that
	 * there is a clear path for the EarthSmash to rise, and that there is
	 * enough Earthbendable material for it to be created.
	 */
	public void animateLift() {
		if (this.animationCounter < 4) {
			this.revert();
			this.location.add(0, 1, 0);
			// Remove the blocks underneath the rising smash.
			if (this.animationCounter == 0) {
				// Check all of the blocks and make sure that they can be removed AND make sure there is enough dirt.
				int totalBendableBlocks = 0;
				for (int x = -1; x <= 1; x++) {
					for (int y = -2; y <= -1; y++) {
						for (int z = -1; z <= 1; z++) {
							final Block block = this.location.clone().add(x, y, z).getBlock();
							if (RegionProtection.isRegionProtected(this, block.getLocation())) {
								this.remove();
								return;
							}
							if (this.isEarthbendable(block)) {
								totalBendableBlocks++;
							}
						}
					}
				}
				if (totalBendableBlocks < this.requiredBendableBlocks) {
					this.remove();
					return;
				}
				// Make sure there is a clear path upward otherwise remove.
				for (int y = 0; y <= 3; y++) {
					final Block tempBlock = this.location.clone().add(0, y, 0).getBlock();
					if (!this.isTransparent(tempBlock) && !ElementalAbility.isAir(tempBlock.getType())) {
						this.remove();
						return;
					}
				}
				// Design what this EarthSmash looks like by using BlockRepresenters.
				final Location tempLoc = this.location.clone().add(0, -2, 0);
				for (int x = -1; x <= 1; x++) {
					for (int y = -1; y <= 1; y++) {
						for (int z = -1; z <= 1; z++) {
							if ((Math.abs(x) + Math.abs(y) + Math.abs(z)) % 2 == 0) {
								final Block block = tempLoc.clone().add(x, y, z).getBlock();
								this.currentBlocks.add(new BlockRepresenter(x, y, z, this.selectMaterialForRepresenter(block.getType()), block.getBlockData()));
							}
						}
					}
				}

				// Remove the design of the second level of removed dirt.
				for (int x = -1; x <= 1; x++) {
					for (int z = -1; z <= 1; z++) {
						if ((Math.abs(x) + Math.abs(z)) % 2 == 1) {
							final Block block = this.location.clone().add(x, -2, z).getBlock();
							if (this.isEarthbendable(block)) {
								addTempAirBlock(block);
							}
						}

						// Remove the first level of dirt.
						final Block block = this.location.clone().add(x, -1, z).getBlock();
						if (this.isEarthbendable(block)) {
							addTempAirBlock(block);
						}
					}
				}

				/*
				 * We needed to calculate all of the blocks based on the
				 * location being 1 above the initial bending block, however we
				 * want to animate it starting from the original bending block.
				 * We must readjust the location back to what it originally was.
				 */
				this.location.add(0, -1, 0);

				// Move any entities that are above the rock.
				final List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(this.location, this.liftRange);
				for (final Entity entity : entities) {
					final org.bukkit.util.Vector velocity = entity.getVelocity();
					GeneralMethods.setVelocity(this, entity, velocity.add(new Vector(0, this.liftKnockup, 0)));
				}
			}

			this.location.getWorld().playEffect(this.location, Effect.GHAST_SHOOT, 0, 7);
			this.draw();
		} else {
			this.state = State.LIFTED;
		}
		this.animationCounter++;
	}

	/**
	 * Redraws the blocks for this instance of EarthSmash.
	 */
	public void draw() {
		if (this.currentBlocks.isEmpty()) {
			this.remove();
			return;
		}
		for (final BlockRepresenter blockRep : this.currentBlocks) {
			final Block block = this.location.clone().add(blockRep.getX(), blockRep.getY(), blockRep.getZ()).getBlock();
			if (this.caster != null && this.isTransparent(block)) {
				this.affectedBlocks.add(new TempBlock(block, blockRep.getType(), this));
				getPreventEarthbendingBlocks().add(block);
			}
		}
	}

	public void revert() {
		this.checkRemainingBlocks();
		for (int i = 0; i < this.affectedBlocks.size(); i++) {
			final TempBlock tblock = this.affectedBlocks.get(i);
			getPreventEarthbendingBlocks().remove(tblock.getBlock());
			tblock.revertBlock();
			this.affectedBlocks.remove(i);
			i--;
		}
	}

	/**
	 * Checks to see which of the blocks are still attached to the EarthSmash,
	 * remember that blocks can be broken or used in other abilities so we need
	 * to double check and remove any that are not still attached.
	 * <p>
	 * Also when we remove the blocks from instances, movedearth, or tempair we
	 * should do it on a delay because tempair takes a couple seconds before the
	 * block shows up in that map.
	 */
	public void checkRemainingBlocks() {
		for (int i = 0; i < this.currentBlocks.size(); i++) {
			final BlockRepresenter brep = this.currentBlocks.get(i);
			final Block block = this.location.clone().add(brep.getX(), brep.getY(), brep.getZ()).getBlock();
			// Check for grass because sometimes the dirt turns into grass.
			if (block.getType() != brep.getType() && (block.getType() != Material.GRASS_BLOCK) && (block.getType() != Material.COBBLESTONE)) {
				this.currentBlocks.remove(i);
				i--;
			}
		}
	}

	@Override
	public void remove() {
		super.remove();
		this.state = State.REMOVED;
		this.revert();
	}

	/**
	 * Gets the blocks surrounding the EarthSmash's loc. This method ignores the
	 * blocks that should be Air, and only returns the ones that are dirt.
	 */
	public List<Block> getBlocks() {
		final List<Block> blocks = new ArrayList<>();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if ((Math.abs(x) + Math.abs(y) + Math.abs(z)) % 2 == 0) { // Give it the cool shape.
						if (this.location != null) {
							blocks.add(this.location.getWorld().getBlockAt(this.location.clone().add(x, y, z)));
						}
					}
				}
			}
		}
		return blocks;
	}

	/**
	 * Gets the blocks surrounding the EarthSmash's loc. This method returns all
	 * the blocks surrounding the loc, including dirt and air.
	 */
	public List<Block> getBlocksIncludingInner() {
		final List<Block> blocks = new ArrayList<>();
		for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
				for (int z = -1; z <= 1; z++) {
					if (this.location != null) {
						blocks.add(this.location.getWorld().getBlockAt(this.location.clone().add(x, y, z)));
					}
				}
			}
		}
		return blocks;
	}

	/**
	 * Switches the Sand Material and Gravel to SandStone and stone
	 * respectively, since gravel and sand cannot be bent due to gravity.
	 */
	public static Material selectMaterial(final Material mat) {
		if (mat == Material.SAND) {
			return Material.SANDSTONE;
		} else if (mat == Material.GRAVEL) {
			return Material.STONE;
		} else {
			return mat;
		}
	}

	public Material selectMaterialForRepresenter(final Material mat) {
		final Material cosmetic = earthCosmetic(caster);
		if (cosmetic != null)
			return cosmetic;

		final Material tempMat = selectMaterial(mat);
		final Random rand = new Random();
		if (!isEarthbendable(tempMat, true, true, true) && !this.isMetalbendable(tempMat)) {
			if (this.currentBlocks.isEmpty()) {
				return Material.DIRT;
			} else {
				return this.currentBlocks.get(rand.nextInt(this.currentBlocks.size())).getType();
			}
		}
		return tempMat;
	}

	/**
	 * Determines if a caster is trying to grab an EarthSmash. A caster is
	 * trying to grab an EarthSmash if they are staring at it and holding shift.
	 */
	private EarthSmash aimingAtSmashCheck(final LivingEntity caster, final State reqState) {
		if (!this.allowGrab) {
			return null;
		}

		final List<Block> blocks = GeneralMethods.getBlocksAroundPoint(GeneralMethods.getTargetedLocation(caster, this.grabRange, getTransparentMaterials()), 1);
		for (final EarthSmash smash : getAbilities(EarthSmash.class)) {
			if (reqState == null || smash.state == reqState) {
				for (final Block block : blocks) {
					if (block == null || smash.getLocation() == null) {
						continue;
					}
					if (block.getLocation().getWorld() == smash.location.getWorld() && block.getLocation().distanceSquared(smash.location) <= Math.pow(this.grabDetectionRadius, 2)) {
						return smash;
					}
				}
			}
		}
		return null;
	}

	/**
	 * This method handles any collision between an EarthSmash and the
	 * surrounding entities, the method only applies to earthsmashes that have
	 * already been shot.
	 */
	public void shootingCollisionDetection() {
		final List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(this.location, this.hitRadius);
		for (final Entity entity : entities) {
			if (entity instanceof LivingEntity && entity != this.caster && !this.affectedEntities.contains(entity)) {
				if (RegionProtection.isRegionProtected(this, entity.getLocation()) || ((entity instanceof Player) && Commands.invincible.contains(entity.getName()))) {
					continue;
				}
				this.affectedEntities.add(entity);
				double damage = this.currentBlocks.size() / 13.0 * this.maxDamage;
				if (damage < this.minDamage) {
					damage = this.minDamage;
				}

				DamageHandler.damageEntity(entity, damage, this);
				final Vector travelVec = GeneralMethods.getDirection(this.location, entity.getLocation());
				GeneralMethods.setVelocity(this, entity, travelVec.setY(this.knockup).normalize().multiply(this.knockback));
			}
		}
	}

	/**
	 * EarthSmash to EarthSmash collision can only happen when one of the
	 * Smashes have been shot by a player. If we find out that one of them have
	 * collided then we want to return since a smash can only remove 1 at a
	 * time.
	 */
	public void smashToSmashCollisionDetection() {
		for (final EarthSmash smash : getAbilities(EarthSmash.class)) {
			if (smash.location != null && smash != this && smash.location.getWorld() == this.location.getWorld() && smash.location.distanceSquared(this.location) < Math.pow(this.flightDetectionRadius, 2)) {
				smash.remove();
				this.remove();
				return;
			}
		}
	}

	/**
	 * Determines whether or not a caster is trying to fly ontop of an
	 * EarthSmash. A caster is considered "flying" if they are standing ontop of
	 * the earthsmash and holding shift.
	 */
	private static EarthSmash flyingInSmashCheck(final LivingEntity caster) {
		for (final EarthSmash smash : getAbilities(EarthSmash.class)) {
			if (!smash.allowFlight) {
				continue;
			}
			// Check to see if the caster is standing on top of the smash.
			if (smash.state == State.LIFTED) {
				if (smash.location.getWorld().equals(caster.getWorld()) && smash.location.clone().add(0, 2, 0).distanceSquared(caster.getLocation()) <= Math.pow(smash.flightDetectionRadius, 2)) {
					return smash;
				}
			}
		}
		return null;
	}

	/**
	 * A BlockRepresenter is used to keep track of each of the individual types
	 * of blocks that are attached to an EarthSmash. Without the representer
	 * then an EarthSmash can only be made up of 1 material at a time. For
	 * example, an ESmash that is entirely dirt, coalore, or sandstone. Using
	 * the representer will allow all the materials to be mixed together.
	 */
	@Getter
	@Setter
	public static class BlockRepresenter {
		private int x, y, z;
		private Material type;
		private BlockData data;

		public BlockRepresenter(final int x, final int y, final int z, final Material type, final BlockData data) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.type = type;
			this.data = data;
		}

		@Override
		public String toString() {
			return this.x + ", " + this.y + ", " + this.z + ", " + this.type.toString();
		}
	}

	@Override
	public String getName() {
		return "EarthSmash";
	}

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

	@Override
	public List<Location> getLocations() {
		final ArrayList<Location> locations = new ArrayList<>();
		for (final TempBlock tblock : this.affectedBlocks) {
			locations.add(tblock.getLocation());
		}
		return locations;
	}

}
