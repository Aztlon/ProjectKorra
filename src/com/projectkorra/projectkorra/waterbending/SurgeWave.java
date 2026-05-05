package com.projectkorra.projectkorra.waterbending;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.WaterAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.avatar.AvatarState;
import com.projectkorra.projectkorra.command.Commands;
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
public class SurgeWave extends WaterAbility {

	private boolean freezing;
	private boolean activateFreeze;
	private boolean progressing;
	private boolean canHitSelf;
	private boolean solidifyLava;
	private long time;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	private long interval;
	@Attribute("IceRevertTime")
	private long iceRevertTime;
	private long obsidianDuration;
	private double currentRadius;
	@Attribute(Attribute.RADIUS)
	private double maxRadius;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.SELECT_RANGE)
	private double selectRange;
	@Attribute(Attribute.KNOCKBACK)
	private double knockback;
	@Attribute(Attribute.KNOCKUP)
	private double knockup;
	@Attribute("Freeze" + Attribute.RADIUS)
	private double maxFreezeRadius;
	private Block sourceBlock;
	private Location location;
	private Location targetDestination;
	private Location frozenLocation;
	private Vector targetDirection;
	private Map<Block, Block> waveBlocks;
	private Map<Block, Material> frozenBlocks;
	private boolean bendableIce;

	public SurgeWave(final LivingEntity caster) {
		super(caster);

		if (!bender.hasUnlocked("SurgeWave")) return;

		SurgeWave wave = getAbility(caster, SurgeWave.class);
		if (wave != null) {
			if (wave.progressing && !wave.freezing) {
				wave.freezing = true;
				return;
			}
		}

		this.canHitSelf = true;
		this.currentRadius = 1;
		this.cooldown = applyInverseModifiers(getConfig().getLong("Abilities.Water.Surge.Wave.Cooldown"));
		this.interval = getConfig().getLong("Abilities.Water.Surge.Wave.Interval");
		this.maxRadius = applyModifiers(getConfig().getDouble("Abilities.Water.Surge.Wave.Radius"));
		this.knockback = applyModifiers(getConfig().getDouble("Abilities.Water.Surge.Wave.Knockback"));
		this.knockup = applyModifiers(getConfig().getDouble("Abilities.Water.Surge.Wave.Knockup"));
		this.maxFreezeRadius = applyModifiers(getConfig().getDouble("Abilities.Water.Surge.Wave.MaxFreezeRadius"));
		this.iceRevertTime = getConfig().getLong("Abilities.Water.Surge.Wave.IceRevertTime");
		this.range = applyModifiers(getConfig().getDouble("Abilities.Water.Surge.Wave.Range"));
		this.selectRange = applyModifiers(getConfig().getDouble("Abilities.Water.Surge.Wave.SelectRange"));
		this.solidifyLava = getConfig().getBoolean("Abilities.Water.Surge.Wave.SolidifyLava.Enabled");
		this.obsidianDuration = getConfig().getLong("Abilities.Water.Surge.Wave.SolidifyLava.Duration");
		this.bendableIce = getConfig().getBoolean("Abilities.Water.Surge.Wave.BendableIce");
		this.waveBlocks = new ConcurrentHashMap<>();
		this.frozenBlocks = new ConcurrentHashMap<>();

		if (this.bender.isAvatarState()) {
			this.maxRadius = getConfig().getDouble("Abilities.Avatar.AvatarState.Water.Surge.Wave.Radius");
		}

		if (this.prepare()) {
			wave = getAbility(caster, SurgeWave.class);
			if (wave != null) {
				wave.remove();
			}
			this.start();
			this.time = System.currentTimeMillis();
		}
	}

	private void addWater(final Block block) {
		if (RegionProtection.isRegionProtected(this, block.getLocation())) {
			return;
		} else if (!TempBlock.isTempBlock(block)) {
			new TempBlock(block, Material.WATER);
			this.waveBlocks.put(block, block);
		}
	}

	private void cancelPrevious() {
		final SurgeWave oldWave = getAbility(this.caster, SurgeWave.class);
		if (oldWave != null) {
			oldWave.remove();
		}
	}

	private void clearWave() {
		for (final Block block : this.waveBlocks.keySet()) {
			TempBlock.revertBlock(block, Material.AIR);
		}
		this.waveBlocks.clear();
	}

	private void finalRemoveWater(final Block block) {
		if (this.waveBlocks.containsKey(block)) {
			TempBlock.revertBlock(block, Material.AIR);
			this.waveBlocks.remove(block);
		}
	}

	private void focusBlock() {
		this.location = this.sourceBlock.getLocation();
	}

	private void freeze() {
		this.clearWave();
		if (!this.bender.canIcebend()) {
			return;
		}

		double freezeradius = this.currentRadius;
		if (freezeradius > this.maxFreezeRadius) {
			freezeradius = this.maxFreezeRadius;
		}

		final List<Block> ice = GeneralMethods.getBlocksAroundPoint(this.frozenLocation, freezeradius);
		final List<Entity> trapped = GeneralMethods.getEntitiesAroundPoint(this.frozenLocation, freezeradius);
		ICE_SETTING: for (final Block block : ice) {
			if (isTransparent(caster, block) && !isIce(block)) {
				for (final Entity entity : trapped) {
					if (entity instanceof Player) {
						if (Commands.invincible.contains(entity.getName())) {
							return;
						}
						if (!getConfig().getBoolean("Properties.Water.FreezePlayerHead") && GeneralMethods.playerHeadIsInBlock((Player) entity, block)) {
							continue ICE_SETTING;
						}
						if (!getConfig().getBoolean("Properties.Water.FreezePlayerFeet") && GeneralMethods.playerFeetIsInBlock((Player) entity, block)) {
							continue ICE_SETTING;
						}
					}
				}

				final TempBlock tblock = new TempBlock(block, iceMaterial(this.caster)).setBendableSource(bendableIce);

				tblock.setRevertTask(() -> SurgeWave.this.frozenBlocks.remove(block));

				tblock.setRevertTime(this.iceRevertTime + (new Random().nextInt(1000)));
				this.frozenBlocks.put(block, block.getType());

				for (final Block sound : this.frozenBlocks.keySet()) {
					if ((new Random()).nextInt(4) == 0) {
						playWaterbendingSound(sound.getLocation());
					}
				}
			}
		}
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

	public void moveWater() {
		if (this.bender.isOnCooldown("SurgeWave")) {
			return;
		}
		this.bender.addCooldown("SurgeWave", this.cooldown);

		if (this.sourceBlock != null) {
			if (!this.sourceBlock.getWorld().equals(this.caster.getWorld())) {
				return;
			}

			this.range = this.getNightFactor(this.range);
			if (this.bender.isAvatarState()) {
				this.knockback = AvatarState.getValue(this.knockback);
			}

			boolean npc = bPlayer == null;
			if (!npc) {
				final Entity target = GeneralMethods.getTargetedEntity(this.caster, this.range);
				if (target == null) {
					this.targetDestination = this.caster.getTargetBlock(getTransparentMaterialSet(), (int) this.range).getLocation();
				} else {
					this.targetDestination = ((LivingEntity) target).getEyeLocation();
				}
			}

			if (this.targetDestination.distanceSquared(this.location) <= 1) {
				this.progressing = false;
				this.targetDestination = null;
			} else {
				this.progressing = true;
				this.targetDirection = this.getDirection(this.sourceBlock.getLocation(), this.targetDestination).normalize();
				this.targetDestination = this.location.clone().add(this.targetDirection.clone().multiply(this.range));

				if (isPlant(this.sourceBlock) || isSnow(this.sourceBlock)) {
					new PlantRegrowth(this.caster, this.sourceBlock);
					this.sourceBlock.setType(Material.AIR, false);
				} else if (isCauldron(this.sourceBlock)) {
					GeneralMethods.setCauldronData(this.sourceBlock, ((Levelled) this.sourceBlock.getBlockData()).getLevel() - 1);
				}

				if (TempBlock.isTempBlock(this.sourceBlock)) {
					final TempBlock tb = TempBlock.get(this.sourceBlock);
					if (Torrent.getFrozenBlocks().containsKey(tb)) {
						Torrent.massThaw(tb);
					}
				}
				this.addWater(this.sourceBlock);
			}
		}
	}

	public boolean prepare() {
		this.cancelPrevious();
		final Block block = BlockSource.getWaterSourceBlock(this.caster, this.selectRange, ClickType.SHIFT_DOWN, true, true, this.bender.canPlantbend());
		if (block != null && !isDecayablePlant(block) && !RegionProtection.isRegionProtected(this, block.getLocation())) {
			this.sourceBlock = block;
			this.focusBlock();
			return true;
		}
		return false;
	}

	@Override
	public void progress() {
		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		} else if (!isWaterbendable(this.sourceBlock) && !this.progressing) {
			this.remove();
			return;
		}

		if (System.currentTimeMillis() - this.time >= this.interval) {
			this.time = System.currentTimeMillis();
			if (!this.progressing && !this.bender.getBoundAbilityName().equals(this.getName())) {
				this.remove();
				return;
			} else if (!this.progressing) {
				ParticleEffect.SMOKE_NORMAL.display(this.sourceBlock.getLocation().add(0.5, 0.5, 0.5), 4);
				return;
			}

			if (this.activateFreeze) {
				if (this.location.distanceSquared(this.caster.getLocation()) > this.range * this.range) {
					this.progressing = false;
					this.remove();
					return;
				}
			} else {
				final Vector direction = this.targetDirection;
				this.location = this.location.clone().add(direction);
				final Block blockl = this.location.getBlock();
				final ArrayList<Block> blocks = new ArrayList<>();

				if (!RegionProtection.isRegionProtected(this, this.location) && (((isAir(blockl.getType()) || blockl.getType() == Material.FIRE || isPlant(blockl) || isWater(blockl) || this.isWaterbendable(this.caster, blockl))))) {
					for (double i = 0; i <= this.currentRadius; i += .5) {
						for (double angle = 0; angle < 360; angle += 10) {
							final Vector vec = GeneralMethods.getOrthogonalVector(this.targetDirection, angle, i);
							final Block block = this.location.clone().add(vec).getBlock();

							if (!blocks.contains(block) && (isAir(block.getType()) || isFire(block.getType())) || this.isWaterbendable(block) || TempBlock.isTempBlock(block)) {
								blocks.add(block);
								FireBlast.removeFireBlastsAroundPoint(block.getLocation(), 2);
							}

							if ((new Random()).nextInt(15) == 0) {
								playWaterbendingSound(this.location);
							}
						}
					}
				}

				for (final Block block : this.waveBlocks.keySet()) {
					if (!blocks.contains(block)) {
						this.finalRemoveWater(block);
					}

					if (solidifyLava) {
						for (BlockFace relative : BlockFace.values()) {
							Block blockRelative = block.getRelative(relative);
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
				for (final Block block : blocks) {
					if (!this.waveBlocks.containsKey(block)) {
						this.addWater(block);
					}
				}

				if (this.waveBlocks.isEmpty()) {
					this.location = this.location.subtract(direction);
					this.remove();
					this.progressing = false;
					return;
				}

				for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(this.location, 2 * this.currentRadius)) {
					boolean knockback = false;
					for (final Block block : this.waveBlocks.keySet()) {
						if (entity.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) <= 4) {
							if (entity instanceof LivingEntity && this.freezing && entity.getEntityId() != this.caster.getEntityId()) {
								this.activateFreeze = true;
								this.frozenLocation = entity.getLocation();
								this.freeze();
								break;
							}
							if (entity.getEntityId() != this.caster.getEntityId() || this.canHitSelf) {
								knockback = true;
							}
						}
					}
					if (knockback) {
						if (RegionProtection.isRegionProtected(this, entity.getLocation()) || ((entity instanceof Player) && Commands.invincible.contains(entity.getName()))) {
							continue;
						}
						final Vector dir = direction.clone();
						dir.setY(dir.getY() * this.knockup);
						GeneralMethods.setVelocity(this, entity, entity.getVelocity().clone().add(dir.clone().multiply(this.getNightFactor(this.knockback))));

						entity.setFallDistance(0);
						if (entity.getFireTicks() > 0) {
							entity.getWorld().playEffect(entity.getLocation(), Effect.EXTINGUISH, 0);
						}
						entity.setFireTicks(0);
						AirAbility.breakBreathbendingHold(entity);
					}
				}

				if (!this.progressing) {
					this.remove();
					return;
				}

				if (this.location.distanceSquared(this.targetDestination) < 1) {
					this.progressing = false;
					this.remove();
					this.returnWater();
					return;
				}
				if (this.currentRadius < this.maxRadius) {
					this.currentRadius += 0.5;
				}
			}
		}
	}

	@Override
	public void remove() {
		super.remove();
		this.thaw();
		this.returnWater();
		if (this.waveBlocks != null) {
			for (final Block block : this.waveBlocks.keySet()) {
				this.finalRemoveWater(block);
			}
		}
	}

	public void returnWater() {
		if (this.location != null && this.bPlayer != null) {
			new WaterReturn(this.player, this.location.getBlock(), this.getDeterministicReturnAmount(CarriedWaterManager.getConsumedForAbility(this)), this.getName() + ".Return");
		}
	}

	private void thaw() {
		if (this.frozenBlocks != null) {
			for (final Block block : this.frozenBlocks.keySet()) {
				if (TempBlock.isTempBlock(block)) {
					TempBlock.get(block).revertBlock();
				}
			}
		}
	}

	public static boolean canThaw(final Block block) {
		for (final SurgeWave surgeWave : getAbilities(SurgeWave.class)) {
			if (surgeWave.frozenBlocks.containsKey(block)) {
				return false;
			}
		}
		return true;
	}

	public static void removeAllCleanup() {
		for (final SurgeWave surgeWave : getAbilities(SurgeWave.class)) {
			for (final Block block : surgeWave.waveBlocks.keySet()) {
				block.setType(Material.AIR, false);
				surgeWave.waveBlocks.remove(block);
			}
			for (final Block block : surgeWave.frozenBlocks.keySet()) {
				if (TempBlock.isTempBlock(block)) {
					TempBlock.get(block).revertBlock();
				}
			}
		}
	}

	public static boolean isBlockWave(final Block block) {
		for (final SurgeWave surgeWave : getAbilities(SurgeWave.class)) {
			if (surgeWave.waveBlocks.containsKey(block)) {
				return true;
			}
		}
		return false;
	}

	public static void thaw(final Block block) {
		for (final SurgeWave surgeWave : getAbilities(SurgeWave.class)) {
			if (surgeWave.frozenBlocks.containsKey(block)) {
				if (TempBlock.isTempBlock(block)) {
					final TempBlock tb = TempBlock.get(block);
					tb.revertBlock();
				}
			}
		}
	}

	@Override
	public String getName() {
		return "Surge";
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
	public boolean isCollidable() {
		return this.progressing || this.activateFreeze;
	}

	@Override
	public List<Location> getLocations() {
		final ArrayList<Location> locations = new ArrayList<>();
		for (final Block block : this.waveBlocks.keySet()) {
			locations.add(block.getLocation());
		}
		for (final Block block : this.frozenBlocks.keySet()) {
			locations.add(block.getLocation());
		}
		return locations;
	}

}
