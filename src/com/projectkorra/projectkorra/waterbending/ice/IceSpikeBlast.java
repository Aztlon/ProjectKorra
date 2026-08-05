package com.projectkorra.projectkorra.waterbending.ice;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Bender;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.util.TempPotionEffect;
import com.projectkorra.projectkorra.waterbending.plant.PlantRegrowth;
import com.projectkorra.projectkorra.waterbending.util.WaterReturn;
import com.projectkorra.projectkorra.waterbending.util.carry.CarriedWaterManager;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IceSpikeBlast extends IceAbility {

	private boolean prepared;
	private boolean settingUp;
	private boolean progressing;
	private byte data;
	@Attribute("SlowPotency")
	private int slowPotency;
	@Attribute("Slow" + Attribute.DURATION)
	private int slowDuration;
	private long time;
	private long interval;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute("Slow" + Attribute.COOLDOWN)
	private long slowCooldown;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	private double collisionRadius;
	@Attribute("Deflect" + Attribute.RANGE)
	private double deflectRange;
	private Block sourceBlock;
	private Location location;
	private Location firstDestination;
	private Location destination;
	private TempBlock source;
	private BlockData sourceType;

	public IceSpikeBlast(final LivingEntity caster) {
		super(caster);

		if (this.bender.isOnCooldown("IceSpikeBlast")) {
			return;
		}

		this.data = 0;
		this.interval = getConfig().getLong("Abilities.Water.IceSpike.Blast.Interval");
		this.slowCooldown = applyInverseModifiers(getConfig().getLong("Abilities.Water.IceSpike.Blast.SlowCooldown"));
		this.collisionRadius = getConfig().getDouble("Abilities.Water.IceSpike.Blast.CollisionRadius");
		this.deflectRange = applyModifiers(getConfig().getDouble("Abilities.Water.IceSpike.Blast.DeflectRange"));
		this.range = applyModifiers(getConfig().getDouble("Abilities.Water.IceSpike.Blast.Range"));
		this.damage = applyModifiers(getConfig().getDouble("Abilities.Water.IceSpike.Blast.Damage"));
		this.cooldown = applyInverseModifiers(getConfig().getLong("Abilities.Water.IceSpike.Blast.Cooldown"));
		this.slowPotency = getConfig().getInt("Abilities.Water.IceSpike.Blast.SlowPotency");
		this.slowDuration = getConfig().getInt("Abilities.Water.IceSpike.Blast.SlowDuration");

		if (!this.bender.canBend(this) || !this.bender.canIcebend()) {
			return;
		}

		if (this.bender.isAvatarState()) {
			this.cooldown = 0;
			this.slowCooldown = 0;
			this.range = getConfig().getDouble("Abilities.Avatar.AvatarState.Water.IceSpike.Blast.Range");
			this.damage = getConfig().getDouble("Abilities.Avatar.AvatarState.Water.IceSpike.Blast.Damage");
			this.slowPotency = getConfig().getInt("Abilities.Avatar.AvatarState.Water.IceSpike.Blast.SlowPotency");
			this.slowDuration = getConfig().getInt("Abilities.Avatar.AvatarState.Water.IceSpike.Blast.SlowDuration");
		}

		block(caster);
		this.sourceBlock = getWaterSourceBlock(caster, this.range, this.bender.canPlantbend());
		if (this.sourceBlock == null) {
			this.sourceBlock = getIceSourceBlock(caster, this.range);
		}

		if (this.sourceBlock == null) {
			new IceSpikePillarField(caster);
		} else if (!RegionProtection.isRegionProtected(this, this.sourceBlock.getLocation())) {
			this.prepare(this.sourceBlock);
		}
	}

	private void affect(final LivingEntity entity) {
		DamageHandler.damageEntity(entity, this.damage, this);
		AirAbility.breakBreathbendingHold(entity);

		if (entity instanceof Player && this.bPlayer != null) { // TODO ?
			if (!this.bPlayer.canBeSlowed())
				return;

			this.bPlayer.slow(this.slowCooldown);
		}

		new TempPotionEffect(entity, new PotionEffect(PotionEffectType.SLOWNESS, this.slowDuration, this.slowPotency));
	}

	private void prepare(final Block block) {
		for (final IceSpikeBlast iceSpike : getAbilities(this.caster, IceSpikeBlast.class)) {
			if (iceSpike.prepared) {
				iceSpike.remove();
			}
		}

		this.sourceBlock = block;
		if (!isIce(block)) {
			this.sourceType = iceMaterial(caster);
		} else {
			this.sourceType = block.getType().createBlockData();
		}
		this.location = this.sourceBlock.getLocation();
		this.prepared = true;
		this.start();
	}

	@Override
	public void progress() {
		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		} else if (this.caster.getEyeLocation().distanceSquared(this.location) >= this.range * this.range) {
			if (this.progressing) {
				this.remove();
			} else {
				this.remove();
			}
			return;
		} else if (!this.bender.boundAbilityMatches(this.getName()) && this.prepared) {
			this.remove();
			return;
		}
		
		if (System.currentTimeMillis() < this.time + this.interval) {
			return;
		}

		this.time = System.currentTimeMillis();

		if (this.progressing) {
			Vector direction;
			if (this.location.getBlockY() == this.firstDestination.getBlockY()) {
				this.settingUp = false;
			}

			if (this.location.distanceSquared(this.destination) <= 4) {
				this.remove();
				return;
			}

			if (this.settingUp) {
				direction = GeneralMethods.getDirection(this.location, this.firstDestination).normalize();
			} else {
				direction = GeneralMethods.getDirection(this.location, this.destination).normalize();
			}

			this.location.add(direction);
			final Block block = this.location.getBlock();
			if (block.equals(this.sourceBlock)) {
				return;
			}

			if (isTransparent(this.caster, block) && !block.isLiquid() && !isDecayablePlant(block)) {
				GeneralMethods.breakBlock(block);
			} else if (!isWater(block)) {
				this.remove();
				return;
			}

			if (RegionProtection.isRegionProtected(this, this.location)) {
				this.remove();
				return;
			}

			for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(this.location, this.collisionRadius + 0.5)) {
				if (entity.getEntityId() != this.caster.getEntityId() && entity instanceof LivingEntity) {
					this.affect((LivingEntity) entity);
					this.progressing = false;
				}
			}

			if ((new Random()).nextInt(4) == 0) {
				playIcebendingSound(this.location);
			}

			if (!this.progressing) {
				this.remove();
				return;
			}

			this.sourceBlock = block;
			this.source = new TempBlock(this.sourceBlock, this.sourceType, this);
			this.source.setRevertTime(130);
		} else if (this.prepared) {
			if (this.sourceBlock != null) {
				playFocusWaterEffect(this.sourceBlock);
			}
		}
	}

	private void redirect(final Location destination, final LivingEntity caster) {
		this.destination = destination;
		this.setCaster(caster);
	}

	@Override
	public void remove() {
		Player consumer = null;
		String cause = null;
		boolean returned = false;
		if (!this.isRemoved()) {
			final int returnAmount = this.getDeterministicReturnAmount(CarriedWaterManager.getConsumedForAbility(this));
			consumer = CarriedWaterManager.getConsumerForAbility(this);
			cause = this.getName() + ".Return";
			returned = CarriedWaterManager.returnForAbility(this, returnAmount, cause);
		}
		super.remove();
		if (this.source != null) {
			this.source.revertBlock();
		}
		this.progressing = false;
		if (returned && consumer != null && this.location != null) {
			new WaterReturn(consumer, this.location.getBlock(), 0, cause + ".Cosmetic");
		}
	}

	private void throwIce() {
		if (!this.prepared) {
			return;
		}

		final LivingEntity target = (LivingEntity) GeneralMethods.getTargetedEntity(this.caster, this.range);
		if (target == null) {
			this.destination = GeneralMethods.getTargetedLocation(this.caster, this.range, true, getTransparentMaterials());
		} else {
			this.destination = target.getLocation();
		}

		if (this.sourceBlock == null) {
			return;
		}
		this.location = this.sourceBlock.getLocation();
		if (this.destination.distanceSquared(this.location) < 1) {
			return;
		}

		this.firstDestination = this.location.clone();
		if (this.destination.getY() - this.location.getY() > 2) {
			this.firstDestination.setY(this.destination.getY() - 1);
		} else {
			this.firstDestination.add(0, 2, 0);
		}

		this.destination = GeneralMethods.getPointOnLine(this.firstDestination, this.destination, this.range);
		this.progressing = true;
		this.settingUp = true;
		this.prepared = false;

		if (isDecayablePlant(this.sourceBlock)) {
			new PlantRegrowth(this.caster, this.sourceBlock, 2);
		} else if (isPlant(this.sourceBlock) || isSnow(this.sourceBlock)) {
			new PlantRegrowth(this.caster, this.sourceBlock);
			this.sourceBlock.setType(Material.AIR);
		} else if (isWater(this.sourceBlock)) {
			if (!GeneralMethods.isAdjacentToThreeOrMoreSources(this.sourceBlock)) {
				this.sourceBlock.setType(Material.AIR);
			}
		} else if (TempBlock.isTempBlock(this.sourceBlock)) {
			final TempBlock tb = TempBlock.get(this.sourceBlock);
			if (isBendableWaterTempBlock(tb)) {
				tb.revertBlock();
			}
		} else if (isCauldron(this.sourceBlock)) {
			GeneralMethods.setCauldronData(this.sourceBlock, ((Levelled) this.sourceBlock.getBlockData()).getLevel() - 1);
		}
	}

	public static void activate(final LivingEntity caster) {
		redirect(caster);
		boolean activate = false;
		final Bender bender = Bender.get(caster);

		if (bender == null) {
			return;
		}

		if (bender.isOnCooldown("IceSpikeBlast")) {
			return;
		}

		for (final IceSpikeBlast ice : getAbilities(caster, IceSpikeBlast.class)) {
			if (ice.prepared) {
				ice.throwIce();
				bender.addCooldown("IceSpikeBlast", ice.getCooldown());
				activate = true;
			}
		}

		if (!activate && !getCasters(IceSpikeBlast.class).contains(caster)) {
			final IceSpikePillar spike = new IceSpikePillar(caster);
			if (!spike.isStarted()) {
				waterBottle(caster);
			}
		}
	}

	private static void block(final LivingEntity caster) {
		for (final IceSpikeBlast iceSpike : getAbilities(IceSpikeBlast.class)) {
			if (iceSpike.caster.equals(caster)) {
				continue;
			} else if (!iceSpike.location.getWorld().equals(caster.getWorld())) {
				continue;
			} else if (!iceSpike.progressing) {
				continue;
			}
			if (RegionProtection.isRegionProtected(iceSpike, iceSpike.location)) {
				continue;
			}

			final Location location = caster.getEyeLocation();
			final Vector vector = location.getDirection();
			final Location mloc = iceSpike.location;
			if (mloc.distanceSquared(location) <= iceSpike.range * iceSpike.range && GeneralMethods.getDistanceFromLine(vector, location, iceSpike.location) < iceSpike.deflectRange && mloc.distanceSquared(location.clone().add(vector)) < mloc.distanceSquared(location.clone().add(vector.clone().multiply(-1)))) {
				iceSpike.remove();
			}
		}
	}

	private static void redirect(final LivingEntity caster) {
		for (final IceSpikeBlast iceSpike : getAbilities(IceSpikeBlast.class)) {
			if (!iceSpike.progressing) {
				continue;
			} else if (!iceSpike.location.getWorld().equals(caster.getWorld())) {
				continue;
			}

			if (iceSpike.caster.equals(caster)) {
				Location location;
				final Entity target = GeneralMethods.getTargetedEntity(caster, iceSpike.range);
				if (target == null) {
					location = GeneralMethods.getTargetedLocation(caster, iceSpike.range);
				} else {
					location = ((LivingEntity) target).getEyeLocation();
				}
				location = GeneralMethods.getPointOnLine(iceSpike.location, location, iceSpike.range * 2);
				iceSpike.redirect(location, caster);
			}

			final Location location = caster.getEyeLocation();
			final Vector vector = location.getDirection();
			final Location mloc = iceSpike.location;

			if (RegionProtection.isRegionProtected(iceSpike, mloc)) {
				continue;
			} else if (mloc.distanceSquared(location) <= iceSpike.range * iceSpike.range && GeneralMethods.getDistanceFromLine(vector, location, iceSpike.location) < iceSpike.deflectRange && mloc.distanceSquared(location.clone().add(vector)) < mloc.distanceSquared(location.clone().add(vector.clone().multiply(-1)))) {
				Location loc;
				final Entity target = GeneralMethods.getTargetedEntity(caster, iceSpike.range);
				if (target == null) {
					loc = GeneralMethods.getTargetedLocation(caster, iceSpike.range, true);
				} else {
					loc = ((LivingEntity) target).getEyeLocation();
				}
				loc = GeneralMethods.getPointOnLine(iceSpike.location, loc, iceSpike.range * 2);
				iceSpike.redirect(loc, caster);
			}
		}
	}

	private static void waterBottle(final LivingEntity caster) {
		if (!(caster instanceof Player p) || !p.isOnline()) return;

		final long range = getConfig().getLong("Abilities.Water.IceSpike.Projectile.Range");

		if (WaterReturn.hasWaterBottle(p)) {
			final Location eyeLoc = caster.getEyeLocation();
			final Block block = eyeLoc.add(eyeLoc.getDirection().normalize()).getBlock();

			if (isTransparent(caster, block) && isTransparent(caster, eyeLoc.getBlock())) {
				final LivingEntity target = (LivingEntity) GeneralMethods.getTargetedEntity(caster, range);
				Location destination;

				if (target == null) {
					destination = GeneralMethods.getTargetedLocation(caster, range, getTransparentMaterials());
				} else {
					destination = GeneralMethods.getPointOnLine(caster.getEyeLocation(), target.getEyeLocation(), range);
				}

				if (destination.distanceSquared(block.getLocation()) < 1) {
					return;
				}

				final BlockState state = block.getState();
				block.setType(Material.WATER);
				block.setBlockData(GeneralMethods.getWaterData(0));
				final IceSpikeBlast iceSpike = new IceSpikeBlast(caster);
				iceSpike.throwIce();
				iceSpike.sourceBlock = null;

				if (iceSpike.progressing) {
					if (!CarriedWaterManager.consumeForAbility(iceSpike, iceSpike.getCarriedWaterCost(), iceSpike.getName() + ".Consume")) {
						iceSpike.remove();
					}
				}
				block.setType(state.getType());
				block.setBlockData(state.getBlockData());

			}
		}
	}

	@Override
	public String getName() {
		return "IceSpike";
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
		return this.progressing;
	}

	@Override
	public double getCollisionRadius() {
		return this.collisionRadius;
	}

}
