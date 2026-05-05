package com.projectkorra.projectkorra.waterbending;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Bender;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.firebending.FireBlast;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.BlockSource;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.plant.PlantRegrowth;
import com.projectkorra.projectkorra.waterbending.util.WaterReturn;
import com.projectkorra.projectkorra.waterbending.util.carry.CarriedWaterManager;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SurgeWall extends WaterAbility {

	private static final Map<Block, Block> AFFECTED_BLOCKS = new ConcurrentHashMap<>();
	private static final Map<Block, LivingEntity> WALL_BLOCKS = new ConcurrentHashMap<>();
	public static final List<TempBlock> SOURCE_BLOCKS = new ArrayList<>();

	private boolean progressing;
	private boolean settingUp;
	private boolean forming;
	private boolean frozen;
	private boolean solidifyLava;
	private long time;
	private long interval;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.DURATION)
	private long duration;
	private long obsidianDuration;
	@Attribute(Attribute.RADIUS)
	private double radius;
	private double defaultRadius;
	@Attribute(Attribute.RANGE)
	private double range;
	private Block sourceBlock;
	private Location location;
	private Location firstDestination;
	private Location targetDestination;
	private ArrayList<Location> locations;
	private Vector firstDirection;
	private Vector targetDirection;
	private Map<Block, Material> oldTemps;

	public SurgeWall(final LivingEntity caster) {
		this(caster, true);
	}

	public SurgeWall(final LivingEntity caster, boolean needsSource) {
		super(caster);

		this.interval = getConfig().getLong("Abilities.Water.Surge.Wall.Interval");
		this.cooldown = applyInverseModifiers(getConfig().getLong("Abilities.Water.Surge.Wall.Cooldown"));
		this.duration = applyModifiers(getConfig().getLong("Abilities.Water.Surge.Wall.Duration"));
		this.range = applyModifiers(getConfig().getDouble("Abilities.Water.Surge.Wall.Range"));
		this.radius = this.defaultRadius = applyModifiers(getConfig().getDouble("Abilities.Water.Surge.Wall.Radius"));
		this.solidifyLava = getConfig().getBoolean("Abilities.Water.Surge.Wall.SolidifyLava.Enabled");
		this.obsidianDuration = getConfig().getLong("Abilities.Water.Surge.Wall.SolidifyLava.Duration");
		this.locations = new ArrayList<>();
		this.oldTemps = new HashMap<>();

		SurgeWave wave = getAbility(caster, SurgeWave.class);
		if (wave != null && !wave.isProgressing() && !this.bender.isOnCooldown("SurgeWave")) {
			wave.moveWater();
			return;
		}

		if (this.bender.isAvatarState()) {
			this.radius = getConfig().getDouble("Abilities.Avatar.AvatarState.Water.Surge.Wall.Radius");
		}

		final SurgeWall wall = getAbility(caster, SurgeWall.class);
		if (wall != null) {
			if (wall.progressing) {
				wall.freezeThaw();
			} else if (this.prepare()) { // reselect source
				wall.remove();
				this.start();
				this.time = System.currentTimeMillis();
			}
			return;
		}

		if (this.bender.isOnCooldown("SurgeWall")) {
			return;
		}

		if (needsSource && this.prepare()) {
			this.start();
			this.time = System.currentTimeMillis();
			return;
		}

		if (!needsSource) {
			this.start();
			this.time = System.currentTimeMillis();
			return;
		}

		if (bPlayer != null && WaterReturn.hasWaterBottle(player)) {
			final Location eyeLoc = caster.getEyeLocation();
			final Block block = eyeLoc.add(eyeLoc.getDirection().normalize()).getBlock();

			if (isTransparent(caster, block) && isTransparent(caster, eyeLoc.getBlock())) {
				final TempBlock tempBlock = new TempBlock(block, Material.WATER);
				tempBlock.setBendableSource(true);
				SOURCE_BLOCKS.add(tempBlock);

				wave = new SurgeWave(caster);
				wave.setCanHitSelf(false);
				wave.moveWater();

				if (!wave.isProgressing()) {
					wave.remove();
				} else if (bPlayer != null) {
					if (!CarriedWaterManager.consumeForAbility(wave, wave.getCarriedWaterCost(), wave.getName() + ".Consume")) {
						wave.remove();
					}
				}

				SOURCE_BLOCKS.remove(tempBlock);
				tempBlock.revertBlock();
			}
		}
	}

	public void freezeThaw() {
		if (!this.bender.canIcebend()) {
			return;
		} else if (this.frozen) {
			this.thaw();
		} else {
			this.freeze();
		}
	}

	public void freeze() {
		this.frozen = true;
		for (final Block block : WALL_BLOCKS.keySet()) {
			if (WALL_BLOCKS.get(block) == this.caster) {
				new TempBlock(block, iceMaterial(this.caster));
				playIcebendingSound(block.getLocation());
			}
		}
	}

	public void thaw() {
		this.frozen = false;
		for (final Block block : WALL_BLOCKS.keySet()) {
			if (WALL_BLOCKS.get(block) == this.caster) {
				new TempBlock(block, Material.WATER);
			}
		}
	}

	public boolean prepare() {
		this.cancelPrevious();
		final Block block = BlockSource.getWaterSourceBlock(this.caster, this.range, ClickType.LEFT_CLICK, true, true, this.bender.canPlantbend());

		if (block != null && !RegionProtection.isRegionProtected(this, block.getLocation())) {
			this.sourceBlock = block;
			this.focusBlock();
			return true;
		}
		return false;
	}

	private void cancelPrevious() {
		final SurgeWall oldWave = getAbility(this.caster, SurgeWall.class);
		if (oldWave != null) {
			if (oldWave.progressing) {
				oldWave.removeWater(oldWave.sourceBlock);
			} else {
				oldWave.remove();
			}
		}
	}

	private void focusBlock() {
		this.location = this.sourceBlock.getLocation();
	}

	public void moveWater() {
		if (this.sourceBlock != null) {
			this.targetDestination = this.caster.getTargetBlock(getTransparentMaterialSet(), (int) this.range).getLocation();

			if (this.targetDestination.distanceSquared(this.location) <= 1) {
				this.progressing = false;
				this.targetDestination = null;
			} else {
				this.bender.addCooldown("SurgeWall", this.cooldown);
				this.progressing = true;
				this.settingUp = true;
				this.firstDestination = this.getToEyeLevel();
				this.firstDirection = this.getDirection(this.sourceBlock.getLocation(), this.firstDestination);
				this.targetDirection = this.getDirection(this.firstDestination, this.targetDestination);

				if (isDecayablePlant(this.sourceBlock)) {
					new PlantRegrowth(this.caster, this.sourceBlock, 3);
				} else if (isPlant(this.sourceBlock) || isSnow(this.sourceBlock)) {
					new PlantRegrowth(this.caster, this.sourceBlock);
					this.sourceBlock.setType(Material.AIR, false);
				} else if (isCauldron(this.sourceBlock)) {
					GeneralMethods.setCauldronData(this.sourceBlock, ((Levelled) this.sourceBlock.getBlockData()).getLevel() - 1);
				}
				this.addWater(this.sourceBlock);
			}

		}
	}

	private Location getToEyeLevel() {
		final Location loc = this.sourceBlock.getLocation().clone();
		loc.setY(this.targetDestination.getY());
		return loc;
	}

	private Vector getDirection(final Location location, final Location destination) {
		double x1, y1, z1;
		double x0, y0, z0;

		x1 = destination.getX();
		y1 = destination.getY();
		z1 = destination.getZ();

		x0 = location.getX();
		y0 = location.getY();
		z0 = location.getZ();

		return new Vector(x1 - x0, y1 - y0, z1 - z0);
	}

	@Override
	public void progress() {
		if (progressing) {
			WaterAbility transformTo = WaterAbility.transformer.apply(this, this.bender.getBoundAbilityName());
			if (transformTo != null) {
				return;
			}
		}

		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		} else if (this.duration != 0 && System.currentTimeMillis() > this.getStartTime() + this.duration) {
			this.bender.addCooldown(this);
			this.remove();
			return;
		} else if (!isWaterbendable(this.sourceBlock) && !this.settingUp && !this.forming && !this.progressing) {
			remove();
			return;
		}

		this.locations.clear();

		if (System.currentTimeMillis() - this.time >= this.interval) {
			this.time = System.currentTimeMillis();
			final boolean matchesName = this.bender.boundAbilityMatches("Surge");

			if (!this.progressing && !matchesName) {
				this.remove();
				return;
			} else if (this.progressing && (!this.bender.isSneaking() || !matchesName)) {
				this.remove();
				return;
			} else if (!this.progressing) {
				ParticleEffect.SMOKE_NORMAL.display(this.sourceBlock.getLocation().add(0.5, 1.5, 0.5), 1);
				return;
			}

			if (this.forming) {
				if ((new Random()).nextInt(7) == 0) {
					playWaterbendingSound(this.location);
				}

				final ArrayList<Block> blocks = new ArrayList<>();
				final Location targetLoc = GeneralMethods.getTargetedLocation(this.caster, (int) this.range, false, false, b -> isWater(b) || isIcebendable(b));
				this.location = targetLoc.clone();
				final Vector eyeDir = this.caster.getEyeLocation().getDirection();
				Vector vector;
				Block block;
				for (double i = 0; i <= this.getNightFactor(this.radius); i += 0.5) {
					for (double angle = 0; angle < 360; angle += 10) {
						vector = GeneralMethods.getOrthogonalVector(eyeDir.clone(), angle, i);
						block = targetLoc.clone().add(vector).getBlock();

						if (RegionProtection.isRegionProtected(this, block.getLocation())) {
							continue;
						} else if (WALL_BLOCKS.containsKey(block)) {
							blocks.add(block);
						} else if (!blocks.contains(block) && (ElementalAbility.isAir(block.getType()) || FireAbility.isFire(block.getType()) || this.isWaterbendable(block)) && this.isTransparent(block)) {
							WALL_BLOCKS.put(block, this.caster);
							this.addWallBlock(block);
							blocks.add(block);
							this.locations.add(block.getLocation());
							FireBlast.removeFireBlastsAroundPoint(block.getLocation(), 2);
						}
					}
				}

				for (final Block blocki : WALL_BLOCKS.keySet()) {
					if (WALL_BLOCKS.get(blocki) == this.caster && !blocks.contains(blocki)) {
						this.finalRemoveWater(blocki);
					}

					if (solidifyLava) {
						for (BlockFace relative : BlockFace.values()) {
							Block blockRelative = blocki.getRelative(relative);
							if (blockRelative.getType() == Material.LAVA) {
								Levelled levelled = (Levelled)blockRelative.getBlockData();
								TempBlock tempBlock;

								if (levelled.getLevel() == 0)
									tempBlock = new TempBlock(blockRelative, Material.OBSIDIAN);
								else
									tempBlock = new TempBlock(blockRelative, Material.COBBLESTONE);

								tempBlock.setRevertTime(obsidianDuration);
								tempBlock.getBlock().getWorld().playSound(tempBlock.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.2F, 1);
							}
						}
					}
				}

				return;
			}

			if (this.sourceBlock.getLocation().distanceSquared(this.firstDestination) < 0.5 * 0.5 && this.settingUp) {
				this.settingUp = false;
			}

			Vector direction;
			if (this.settingUp) {
				direction = this.firstDirection;
			} else {
				direction = this.targetDirection;
			}

			this.location = this.location.clone().add(direction);

			Block block = this.location.getBlock();
			if (block.getLocation().equals(this.sourceBlock.getLocation())) {
				this.location = this.location.clone().add(direction);
				block = this.location.getBlock();
			}

			if (!ElementalAbility.isAir(block.getType())) {
				this.remove();
				return;
			} else if (!this.progressing) {
				this.remove();
				return;
			}

			this.addWater(block);
			this.removeWater(this.sourceBlock);
			this.sourceBlock = block;

			if (this.location.distanceSquared(this.targetDestination) < 1) {
				this.removeWater(this.sourceBlock);
				this.forming = true;
			}
		}
	}

	private void addWallBlock(final Block block) {
		if (TempBlock.isTempBlock(block)) {
			this.oldTemps.put(block, block.getType());
		}

		if (this.frozen) {
			new TempBlock(block, iceMaterial(this.caster));
		} else {
			new TempBlock(block, Material.WATER);
		}
	}

	@Override
	public void remove() {
		super.remove();
		this.returnWater();
		this.finalRemoveWater(this.sourceBlock);

		for (final Block block : WALL_BLOCKS.keySet()) {
			if (WALL_BLOCKS.get(block) == this.caster) {
				this.finalRemoveWater(block);
			}
		}

	}

	private void removeWater(final Block block) {
		if (block != null) {
			if (AFFECTED_BLOCKS.containsKey(block)) {
				if (!GeneralMethods.isAdjacentToThreeOrMoreSources(block)) {
					if (this.oldTemps.containsKey(block)) {
						final TempBlock tb = TempBlock.get(block);
						if (tb != null) {
							tb.setType(this.oldTemps.get(block));
						}
					} else {
						TempBlock.revertBlock(block, Material.AIR);
					}
				}
				AFFECTED_BLOCKS.remove(block);
			}
		}
	}

	private void finalRemoveWater(final Block block) {
		if (block != null) {
			if (AFFECTED_BLOCKS.containsKey(block)) {
				if (this.oldTemps.containsKey(block)) {
					final TempBlock tb = TempBlock.get(block);
					if (tb != null) {
						tb.setType(this.oldTemps.get(block));
					}
				} else {
					TempBlock.revertBlock(block, Material.AIR);
				}
				AFFECTED_BLOCKS.remove(block);
			}
			if (WALL_BLOCKS.containsKey(block)) {
				if (this.oldTemps.containsKey(block)) {
					final TempBlock tb = TempBlock.get(block);
					if (tb != null) {
						tb.setType(this.oldTemps.get(block));
					}
				} else {
					TempBlock.revertBlock(block, Material.AIR);
				}
				WALL_BLOCKS.remove(block);
			}
		}
	}

	private void addWater(final Block block) {
		if (RegionProtection.isRegionProtected(this, block.getLocation())) {
			return;
		} else if (!TempBlock.isTempBlock(block)) {
			new TempBlock(block, Material.WATER);
			AFFECTED_BLOCKS.put(block, block);
		}
	}

	@Override
	public boolean allowBreakPlants() {
		return false;
	}

	public static void form(final LivingEntity caster) {
		final Bender bender = Bender.get(caster);
		if (bender == null) {
			return;
		}

		final double range = WaterAbility.getNightFactor(caster.getWorld()) * getConfig().getDouble("Abilities.Water.Surge.Wall.Range");
		SurgeWall wall = getAbility(caster, SurgeWall.class);
		SurgeWave wave = getAbility(caster, SurgeWave.class);

		if (wave != null) {
			if (wave.isProgressing() && !wave.isFreezing()) {
				// Freeze the wave.
				new SurgeWave(caster);
			} else if (wave.isActivateFreeze()) {
				wave.remove();
				return;
			}
		}

		if (wall == null) {
			final Block source = BlockSource.getWaterSourceBlock(caster, range, ClickType.SHIFT_DOWN, true, true, bender.canPlantbend());

			if (wave == null && source == null && caster instanceof Player p && p.isOnline() && WaterReturn.hasWaterBottle(p)) {
				if (bender.isOnCooldown("SurgeWall")) {
					return;
				}

				final Location eyeLoc = caster.getEyeLocation();
				final Block block = eyeLoc.add(eyeLoc.getDirection().normalize()).getBlock();

				if (isTransparent(caster, block) && isTransparent(caster, eyeLoc.getBlock())) {
					final TempBlock tempBlock = new TempBlock(block, Material.WATER);
					tempBlock.setBendableSource(true);
					SOURCE_BLOCKS.add(tempBlock);

					wall = new SurgeWall(caster);
					wall.moveWater();

					if (!wall.progressing) {
						SOURCE_BLOCKS.remove(tempBlock);
						tempBlock.revertBlock();
						wall.remove();
					} else {
						if (!CarriedWaterManager.consumeForAbility(wall, wall.getCarriedWaterCost(), wall.getName() + ".Consume")) {
							wall.remove();
						}
					}

					SOURCE_BLOCKS.remove(tempBlock);
					tempBlock.revertBlock();
					return;
				}
			}

			// If SurgeWall isn't being created, then try to source SurgeWave.
			if (!bender.isOnCooldown("SurgeWave")) {
				wave = new SurgeWave(caster);
			}
			return;
		} else {
			if (isWaterbendable(caster, null, caster.getTargetBlock(null, Math.min(1, (int)range)))) {
				wave = new SurgeWave(caster);
				return;
			}
		}

		wall.moveWater();
	}

	public static void removeAllCleanup() {
		for (final Block block : AFFECTED_BLOCKS.keySet()) {
			TempBlock.revertBlock(block, Material.AIR);
			AFFECTED_BLOCKS.remove(block);
			WALL_BLOCKS.remove(block);
		}
		for (final Block block : WALL_BLOCKS.keySet()) {
			TempBlock.revertBlock(block, Material.AIR);
			AFFECTED_BLOCKS.remove(block);
			WALL_BLOCKS.remove(block);
		}
	}

	public static boolean wasBrokenFor(final Player player, final Block block) {
		final SurgeWall wall = getAbility(player, SurgeWall.class);
		if (wall != null) {
			if (wall.sourceBlock == null) {
				return false;
			} else if (wall.sourceBlock.equals(block)) {
				return true;
			}
		}
		return false;
	}

	private void returnWater() {
		if (this.location != null) {
			if (this.frozen) {
				this.thaw();
			}
			if (bPlayer != null) {
				new WaterReturn(this.player, this.location.getBlock(), this.getDeterministicReturnAmount(CarriedWaterManager.getConsumedForAbility(this)), this.getName() + ".Return");
			}
		}
	}

	@Override
	public String getName() {
		return "SurgeWall";
	}

	@Override
	public Location getLocation() {
		if (this.location != null) {
			return this.location;
		} else if (this.sourceBlock != null) {
			return this.sourceBlock.getLocation();
		}
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
	public List<Location> getLocations() {
		return this.locations;
	}

	public static Map<Block, Block> getAffectedBlocks() {
		return AFFECTED_BLOCKS;
	}

	public static Map<Block, LivingEntity> getWallBlocks() {
		return WALL_BLOCKS;
	}

}
