package com.projectkorra.projectkorra.waterbending;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.combo.IceWave;
import com.projectkorra.projectkorra.waterbending.plant.PlantRegrowth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WaterSpoutWave extends WaterAbility {

	public enum AbilityType {
		CLICK, SHIFT, RELEASE
	}

	public enum AnimateState {
		RISE, TOWARD_PLAYER, CIRCLE, SHRINK
	}

	private static final Map<Block, TempBlock> FROZEN_BLOCKS = new ConcurrentHashMap<>();

	@Attribute(Attribute.RADIUS)
	private double radius;
	private boolean charging;
	private boolean iceWave;
	private boolean iceOnly;
	private boolean moving;
	private boolean plant;
	private boolean collidable;
	private boolean revertIceSphere;
	private int progressCounter;
	private long time;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private long revertSphereTime;
	@Attribute(Attribute.SELECT_RANGE)
	private double selectRange;
	@Attribute(Attribute.SPEED)
	private double speed;
	private double defaultSpeed;
	@Attribute(Attribute.CHARGE_DURATION)
	private double chargeTime;
	@Attribute("Flight" + Attribute.DURATION)
	private double flightDuration;
	@Attribute("Wave" + Attribute.RADIUS)
	private double waveRadius;
	@Attribute("Thaw" + Attribute.RADIUS)
	private double thawRadius;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	private double animationSpeed;
	private long trailRevertTime;
	private AbilityType type;
	private AnimateState animation;
	private Block sourceBlock;
	private Vector direction;
	private Location origin;
	private Location location;
	private ArrayList<Entity> affectedEntities;
	private ArrayList<BukkitRunnable> tasks;
	private ConcurrentHashMap<Block, TempBlock> affectedBlocks;
	private boolean bendableIce;

	public WaterSpoutWave(final LivingEntity caster) {
		this(caster, AbilityType.CLICK);
	}

	public WaterSpoutWave(final LivingEntity caster, final AbilityType type) {
		super(caster);

		if (!bender.hasUnlocked("WaterWave")) return;

		this.charging = false;
		this.iceWave = false;
		this.iceOnly = false;
		this.collidable = false;
		this.plant = getConfig().getBoolean("Abilities.Water.WaterSpout.Wave.AllowPlantSource");
		this.radius = applyModifiers(getConfig().getDouble("Abilities.Water.WaterSpout.Wave.Radius"));
		this.waveRadius = applyModifiers(getConfig().getDouble("Abilities.Water.WaterSpout.Wave.WaveRadius"));
		this.thawRadius = applyModifiers(getConfig().getDouble("Abilities.Water.IceWave.ThawRadius"));
		this.animationSpeed = getConfig().getDouble("Abilities.Water.WaterSpout.Wave.AnimationSpeed");
		this.selectRange = applyModifiers(getConfig().getDouble("Abilities.Water.WaterSpout.Wave.SelectRange"));
		this.speed = this.defaultSpeed = getConfig().getDouble("Abilities.Water.WaterSpout.Wave.Speed");
		this.damage = applyModifiers(getConfig().getDouble("Abilities.Water.IceWave.Damage"));
		this.chargeTime = applyInverseModifiers(getConfig().getLong("Abilities.Water.WaterSpout.Wave.ChargeTime"));
		this.flightDuration = applyModifiers(getConfig().getLong("Abilities.Water.WaterSpout.Wave.FlightDuration"));
		this.cooldown = applyInverseModifiers(getConfig().getLong("Abilities.Water.WaterSpout.Wave.Cooldown"));
		this.revertSphereTime = getConfig().getLong("Abilities.Water.IceWave.RevertSphereTime");
		this.revertIceSphere = getConfig().getBoolean("Abilities.Water.IceWave.RevertSphere");
		this.trailRevertTime = getConfig().getLong("Abilities.Water.WaterSpout.Wave.TrailRevertTime");
		this.bendableIce = getConfig().getBoolean("Abilities.Water.IceWave.BendableIce");
		this.affectedBlocks = new ConcurrentHashMap<>();
		this.affectedEntities = new ArrayList<>();
		this.tasks = new ArrayList<>();

		this.damage = this.getNightFactor(this.damage);

		if (!this.bender.canBendIgnoreBinds(this)) {
			return;
		}

		if (this.bender.isAvatarState()) {
			this.chargeTime = 0;
			this.flightDuration = getConfig().getDouble("Abilities.Avatar.AvatarState.Water.WaterWave.FlightDuration");
			this.damage = getConfig().getDouble("Abilities.Avatar.AvatarState.Water.IceWave.Damage");
			this.cooldown = 0;
		}

		this.time = System.currentTimeMillis();
		this.type = type;

		WaterSpoutWave wave = CoreAbility.getAbility(caster, WaterSpoutWave.class);
		if (type == AbilityType.CLICK && wave != null && (wave.charging || wave.moving)) {
//			this.remove();
			return;
		}

		this.start();

		if (type == AbilityType.CLICK) {
			// Need to progress immediately for the WaterSpout check.
			this.progress();
		}
	}

	@Override
	public void progress() {
		this.progressCounter++;
		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		}

		if (animation != AnimateState.CIRCLE) {
			final WaterSpout waterSpout = CoreAbility.getAbility(this.caster, WaterSpout.class);
			if (waterSpout != null) {
				waterSpout.remove();
			}
		}

		if (this.type != AbilityType.RELEASE) {
			if (!this.caster.hasPermission("bending.ability.WaterSpout.Wave")) {
				this.remove();
				return;
			}

			WaterAbility transformTo = WaterAbility.transformer.apply(this, this.bender.getBoundAbilityName());
			if (transformTo != null) {
				return;
			}
		}

		if (this.type == AbilityType.CLICK) {
			if (this.origin == null) {
				this.removeOldType(this.caster, AbilityType.CLICK);
				this.sourceBlock = getWaterSourceBlock(this.caster, this.selectRange, this.plant);

				if (this.sourceBlock == null) {
					this.remove();
					return;
				}

				final Block blockAbove = this.sourceBlock.getRelative(BlockFace.UP);
				if (!ElementalAbility.isAir(blockAbove.getType()) && !this.isWaterbendable(blockAbove)) {
					this.remove();
					return;
				}

				this.origin = this.sourceBlock.getLocation();
				if (!this.isWaterbendable(this.sourceBlock) || RegionProtection.isRegionProtected(this, this.origin)) {
					this.remove();
					return;
				} else if (this.iceOnly && !(this.isIcebendable(this.sourceBlock) || isSnow(this.sourceBlock))) {
					this.remove();
					return;
				}
			}

			if (this.caster.getLocation().distanceSquared(this.origin) > this.selectRange * this.selectRange || !isWaterbendable(this.sourceBlock)) {
				this.remove();
				return;
			} else if (this.bender.isSneaking()) {
				this.setType(AbilityType.SHIFT);
				return;
			}
			playFocusWaterEffect(this.origin.getBlock());
		} else if (this.type == AbilityType.SHIFT) {
			if (this.direction == null) {
				this.direction = this.caster.getEyeLocation().getDirection();
			}
			if (!this.charging) {
				if (!containsType(this.caster, AbilityType.SHIFT)) {
					this.removeOldType(this.caster, AbilityType.CLICK);
					this.remove();
					return;
				}

				this.charging = true;
				this.animation = AnimateState.RISE;
				this.location = this.origin.clone();

				if (isDecayablePlant(this.origin.getBlock())) {
					new PlantRegrowth(this.caster, this.origin.getBlock(), getConfig().getDouble("Abilities.Water.WaterSpout.GrassRadius"));
				} else if (isPlant(this.origin.getBlock()) || isSnow(this.origin.getBlock())) {
					new PlantRegrowth(this.caster, this.origin.getBlock());
					this.origin.getBlock().setType(Material.AIR);
				} else if (isCauldron(this.origin.getBlock())) {
					this.origin.getBlock().setType(Material.CAULDRON);
				}

				if (TempBlock.isTempBlock(this.origin.getBlock())) {
					final TempBlock tb = TempBlock.get(this.origin.getBlock());

					if (Torrent.getFrozenBlocks().containsKey(tb)) {
						Torrent.massThaw(tb);
					} else if (!isBendableWaterTempBlock(tb) && !PlantRegrowth.getDecayedBlocks().contains(tb)) {
						this.remove();
						return;
					}
				}
			}

			this.removeOldType(this.caster, AbilityType.CLICK);
			if (!this.bender.isSneaking()) {
				if (System.currentTimeMillis() - this.time > this.chargeTime) {
					this.setType(AbilityType.RELEASE);
					this.setAnimation(AnimateState.SHRINK);
				} else {
					this.remove();
				}
				return;
			}

			if (this.animation == AnimateState.RISE && this.location != null) {
				this.revertBlocks();
				this.location.add(0, this.animationSpeed, 0);
				final Block block = this.location.getBlock();

				if (!(this.isWaterbendable(block) || ElementalAbility.isAir(block.getType()) || RegionProtection.isRegionProtected(this, block.getLocation()))) {
					this.remove();
					return;
				}
				this.createBlock(block, Material.WATER.createBlockData());
				if (this.location.distanceSquared(this.origin) > 4) {
					this.animation = AnimateState.TOWARD_PLAYER;
				}
			} else if (this.animation == AnimateState.TOWARD_PLAYER) {
				this.revertBlocks();
				final Location eyeLoc = this.caster.getTargetBlock(null, 2).getLocation();
				eyeLoc.setY(this.caster.getEyeLocation().getY());
				final Vector vec = GeneralMethods.getDirection(this.location, eyeLoc);
				this.location.add(vec.normalize().multiply(this.animationSpeed));
				final Block block = this.location.getBlock();

				if (!(this.isWaterbendable(block) || ElementalAbility.isAir(block.getType()) || RegionProtection.isRegionProtected(this, block.getLocation()))) {
					this.remove();
					return;
				}

				this.createBlock(block, Material.WATER.createBlockData());
				if (this.location.distanceSquared(eyeLoc) < 1.7) {
					this.animation = AnimateState.CIRCLE;
					final Vector tempDir = this.caster.getLocation().getDirection();
					tempDir.setY(0);
					this.direction = tempDir.normalize();
					this.revertBlocks();
				}
			} else if (this.animation == AnimateState.CIRCLE) {
				this.drawCircle(120, 5);
			}
		} else if (this.type == AbilityType.RELEASE) {
			if (this.animation == AnimateState.SHRINK) {
				this.radius -= 0.20;
				this.drawCircle(360, 15);

				if (this.radius < 1) {
					this.revertBlocks();
					this.time = System.currentTimeMillis();
					this.animation = null;
				}
			} else {
				this.moving = true;
				this.collidable = true;
				if ((System.currentTimeMillis() - this.time > this.flightDuration && !this.bender.isAvatarState()) || this.bender.isSneaking()) {
					this.remove();
					return;
				}

				this.caster.setFallDistance(0f);
				double currentSpeed = this.speed - (this.speed * (System.currentTimeMillis() - this.time) / this.flightDuration);
				final double nightSpeed = this.getNightFactor(currentSpeed * 0.9);
				currentSpeed = nightSpeed > currentSpeed ? nightSpeed : currentSpeed;
				if (this.bender.isAvatarState()) {
					currentSpeed = this.getNightFactor(this.speed);
				}
				GeneralMethods.setVelocity(this, this.caster, this.caster.getEyeLocation().getDirection().normalize().multiply(currentSpeed));
				for (final Block block : GeneralMethods.getBlocksAroundPoint(this.caster.getLocation().add(0, -1, 0), this.waveRadius)) {
					if (ElementalAbility.isAir(block.getType()) && !RegionProtection.isRegionProtected(this, block.getLocation())) {
						if (this.iceWave) {
							this.createBlockDelay(block, iceMaterial(this.caster), 2L);
						} else {
							this.createBlock(block, Material.WATER.createBlockData());
						}
					}
				}

				if (this.iceWave && this.progressCounter % 3 == 0) {
					for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(this.caster.getLocation().add(0, -1, 0), this.waveRadius * 1.5)) {
						if (entity != this.caster && entity instanceof LivingEntity && !this.affectedEntities.contains(entity)) {
							this.affectedEntities.add(entity);
							final double augment = getNightFactor(this.caster.getWorld());
							DamageHandler.damageEntity(entity, this.damage, CoreAbility.getAbility(this.caster, IceWave.class));
							final LivingEntity fplayer = this.caster;
							final Entity fent = entity;

							new BukkitRunnable() {
								@Override
								public void run() {
									WaterSpoutWave.this.createIceSphere(fplayer, fent, augment * 2.5);
								}
							}.runTaskLater(ProjectKorra.plugin, 6);
						}
					}
					for (final Block block : FROZEN_BLOCKS.keySet()) {
						final TempBlock tBlock = FROZEN_BLOCKS.get(block);
						if (tBlock.getBlock().getWorld().equals(this.caster.getWorld()) && tBlock.getLocation().distance(this.caster.getLocation()) >= this.thawRadius) {
							tBlock.revertBlock();
							FROZEN_BLOCKS.remove(block);
						}
					}
				}
			}
		}
	}

	public void drawCircle(final double theta, final double increment) {
		final double rotateSpeed = 45;
		this.revertBlocks();
		this.direction = GeneralMethods.rotateXZ(this.direction, rotateSpeed);
		for (double i = 0; i < theta; i += increment) {
			final Vector dir = GeneralMethods.rotateXZ(this.direction, i - theta / 2).normalize().multiply(this.radius);
			dir.setY(0);
			final Block block = this.caster.getEyeLocation().add(dir).getBlock();
			this.location = block.getLocation();
			if (ElementalAbility.isAir(block.getType()) && !RegionProtection.isRegionProtected(this, block.getLocation())) {
				this.createBlock(block, Material.WATER.createBlockData());
			}
		}
	}

	@Override
	public void remove() {
		super.remove();
		if (this.moving) {
			this.bender.addCooldown(this);
		}
		this.revertBlocks();
		for (final BukkitRunnable task : this.tasks) {
			task.cancel();
		}
	}

	public void createBlockDelay(final Block block, final BlockData data, final long delay) {
		final BukkitRunnable br = new BukkitRunnable() {
			@Override
			public void run() {
				WaterSpoutWave.this.createBlock(block, block.getLocation().distance(player.getLocation()) >= 1.6 ? data : Material.WATER.createBlockData());
			}
		};
		br.runTaskLater(ProjectKorra.plugin, delay);
		this.tasks.add(br);
	}

	public void createBlock(final Block block, final BlockData data) {
		if (this.affectedBlocks.containsKey(block)) {
			this.affectedBlocks.get(block).revertBlock();
		}
		TempBlock tb = new TempBlock(block, data, this.trailRevertTime).setBendableSource(bendableIce);
		tb.setRevertTask(() -> this.affectedBlocks.remove(block));
		this.affectedBlocks.put(block, tb);
	}

	public void revertBlocks() {
		final Enumeration<Block> keys = this.affectedBlocks.keys();
		while (keys.hasMoreElements()) {
			final Block block = keys.nextElement();
			this.affectedBlocks.get(block).revertBlock();
			this.affectedBlocks.remove(block);
		}
	}

	public void createIceSphere(final LivingEntity caster, final Entity entity, final double radius) {
		for (double x = -radius; x <= radius; x += 0.5) {
			for (double y = -radius; y <= radius; y += 0.5) {
				for (double z = -radius; z <= radius; z += 0.5) {
					final Block block = entity.getLocation().getBlock().getLocation().add(x, y, z).getBlock();
					if (block.getLocation().distanceSquared(entity.getLocation().getBlock().getLocation()) > radius * radius) {
						continue;
					}
					if (RegionProtection.isRegionProtected(this, block.getLocation())) {
						continue;
					}
					if (entity instanceof Player) {
						if (Commands.invincible.contains(entity.getName())) {
							return;
						}
						if (!getConfig().getBoolean("Properties.Water.FreezePlayerHead") && GeneralMethods.playerHeadIsInBlock((Player) entity, block)) {
							continue;
						}
						if (!getConfig().getBoolean("Properties.Water.FreezePlayerFeet") && GeneralMethods.playerFeetIsInBlock((Player) entity, block)) {
							continue;
						}
					}
					if (ElementalAbility.isAir(block.getType()) || isIce(block) || this.isWaterbendable(block)) {
						if (!FROZEN_BLOCKS.containsKey(block)) {
							final TempBlock tblock = new TempBlock(block, iceMaterial(this.caster)).setBendableSource(bendableIce);
							FROZEN_BLOCKS.put(block, tblock);
							if (this.revertIceSphere) {
								tblock.setRevertTime(this.revertSphereTime + ThreadLocalRandom.current().nextLong(-500, 500));
							}
						}
					}
				}
			}
		}
	}

	public static boolean containsType(final LivingEntity caster, final AbilityType type) {
		for (final WaterSpoutWave wave : getAbilities(caster, WaterSpoutWave.class)) {
			if (wave.type.equals(type)) {
				return true;
			}
		}
		return false;
	}

	public void removeOldType(final LivingEntity caster, final AbilityType type) {
		for (final WaterSpoutWave wave : getAbilities(caster, WaterSpoutWave.class)) {
			if (wave.type.equals(type) && !wave.equals(this)) {
				wave.remove();
			}
		}
	}

	public static ArrayList<WaterSpoutWave> getType(final LivingEntity caster, final AbilityType type) {
		final ArrayList<WaterSpoutWave> list = new ArrayList<>();
		for (final WaterSpoutWave wave : getAbilities(caster, WaterSpoutWave.class)) {
			if (wave.type.equals(type)) {
				list.add(wave);
			}
		}
		return list;
	}

	public static boolean wasBrokenFor(final LivingEntity caster, final Block block) {
		final ArrayList<WaterSpoutWave> waves = getType(caster, AbilityType.CLICK);
		if (!waves.isEmpty()) {
			final WaterSpoutWave wave = waves.get(0);
			if (wave.origin == null) {
				return false;
			} else if (wave.origin.getBlock().equals(block)) {
				return true;
			}
		}
		return false;
	}

	public static void progressAllCleanup() {
		for (final Block block : FROZEN_BLOCKS.keySet()) {
			final TempBlock tb = FROZEN_BLOCKS.get(block);
			if (!isIce(block)) {
				FROZEN_BLOCKS.remove(block);
				continue;
			}
			if (tb == null || !TempBlock.isTempBlock(block)) {
				FROZEN_BLOCKS.remove(block);
				continue;
			}
		}
	}

	public static boolean canThaw(final Block block) {
		return FROZEN_BLOCKS.containsKey(block);
	}

	public static void thaw(final Block block) {
		if (FROZEN_BLOCKS.containsKey(block)) {
			FROZEN_BLOCKS.get(block).revertBlock();
			FROZEN_BLOCKS.remove(block);
		}
	}

	@Override
	public Location getLocation() {
		if (this.location != null) {
			return this.location;
		} else {
			return this.origin;
		}
	}

	@Override
	public String getName() {
		return "WaterSpoutWave";
	}

	@Override
	public Element getElement() {
		return this.isIceWave() ? Element.ICE : Element.WATER;
	}

	@Override
	public long getCooldown() {
		return this.cooldown;
	}

	@Override
	public boolean isSneakAbility() {
		return this.isIceWave();
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isCollidable() {
		return this.collidable;
	}

	@Override
	public double getCollisionRadius() {
		return this.getRadius();
	}

	@Override
	public boolean isEnabled() {
		return getConfig().getBoolean("Abilities.Water.WaterSpout.Wave.Enabled");
	}

	public static Map<Block, TempBlock> getFrozenBlocks() {
		return FROZEN_BLOCKS;
	}

	@Override
	public boolean allowBreakPlants() {
		return false;
	}
}
