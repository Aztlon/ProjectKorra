package com.projectkorra.projectkorra.earthbending;

import java.util.ArrayList;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Bender;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.util.TempFallingBlock;

import lombok.Getter;

@Getter
public class EarthBlast extends EarthAbility {
	private boolean isProgressing;
	private boolean isAtDestination;
	private boolean isSettingUp;
	private boolean canHitSelf;
	private long time;
	private long interval;
	@Attribute(Attribute.COOLDOWN)
	private long cooldown;
	@Attribute(Attribute.RANGE)
	private double range;
	@Attribute(Attribute.DAMAGE)
	private double damage;
	@Attribute(Attribute.SPEED)
	private double speed;
	@Attribute(Attribute.KNOCKBACK)
	private double pushFactor;
	@Attribute(Attribute.SELECT_RANGE)
	private double selectRange;
	@Attribute("DeflectRange")
	private double deflectRange;
	private double collisionRadius;
	private Location location;
	private Location destination;
	private Location firstDestination;
    private Material blastType;
	private TempBlock sourceBlock;
	private TempBlock blastBlock;
	BlockData blockData;

	public EarthBlast(final LivingEntity caster) {
		super(caster);

		this.isProgressing = false;
		this.isAtDestination = false;
		this.isSettingUp = true;
		this.deflectRange = getConfig().getDouble("Abilities.Earth.EarthBlast.DeflectRange");
		this.collisionRadius = getConfig().getDouble("Abilities.Earth.EarthBlast.CollisionRadius");
		this.cooldown = getConfig().getLong("Abilities.Earth.EarthBlast.Cooldown");
		this.canHitSelf = getConfig().getBoolean("Abilities.Earth.EarthBlast.CanHitSelf");
		this.range = getConfig().getDouble("Abilities.Earth.EarthBlast.Range");
		this.damage = getConfig().getDouble("Abilities.Earth.EarthBlast.Damage");
		this.speed = getConfig().getDouble("Abilities.Earth.EarthBlast.Speed");
		this.pushFactor = getConfig().getDouble("Abilities.Earth.EarthBlast.Push");
		this.selectRange = getConfig().getDouble("Abilities.Earth.EarthBlast.SelectRange");
		this.time = System.currentTimeMillis();
		this.interval = (long) (1000.0 / this.speed);

		if (bender.isAvatarState()) {
			this.cooldown = getConfig().getLong("Abilities.Avatar.AvatarState.Earth.EarthBlast.Cooldown");
			this.damage = getConfig().getDouble("Abilities.Avatar.AvatarState.Earth.EarthBlast.Damage");
		}

		if (this.prepare()) {
			this.start();
			this.time = System.currentTimeMillis();
		}
	}
	
	private void disableEnemyBlasts(Location loc) {
		for (EarthBlast blast : getAbilities(EarthBlast.class)) {
			if (blast.caster.equals(this.caster)) {
				continue;
			} else if (!blast.location.getWorld().equals(this.caster.getWorld())) {
				continue;
			} else if (!blast.isProgressing) {
				continue;
			} else if (RegionProtection.isRegionProtected(this, blast.location)) {
				continue;
			}

			final Location blastLocation = blast.location;

			if (loc.distanceSquared(blastLocation) < deflectRange * deflectRange) {
				playEarthbendingSound(loc);
				blast.becomeFallingBlock(blastLocation);
			}
		}
	}

	public void becomeFallingBlock(Location location) {
		blastBlock.revertBlock();
		this.remove();
		new TempFallingBlock(location, blockData, new Vector(0, 0, 0), this);
	}

	private void focusBlock(Block block) {
		Material material = block.getType();

		Optional.ofNullable(TempBlock.get(block)).ifPresent(TempBlock::revertBlock);
		
		this.damage = applyMetalPowerFactor(this.damage, block);
		this.location = block.getLocation();

        Material originalType = block.getType();
        Material sourceType = selectMaterialForSource(material);
		this.blastType = selectMaterialForBlast(material);
		this.blockData = blastType.createBlockData();
		
        // physics update for observers
        block.setType(sourceType);
        block.setType(originalType);
	
		sourceBlock = new TempBlock(block, sourceType.createBlockData(), 20000);
	}

	private Material selectMaterialForSource(Material material) {
		return switch (material) {
			case SAND -> Material.SANDSTONE;
			case RED_SAND -> Material.RED_SANDSTONE;
			case STONE -> Material.COBBLESTONE;
			default -> Material.STONE;
		};
	}

	private Material selectMaterialForBlast(Material material) {
		final Material cosmetic = earthCosmetic(caster);
		if (cosmetic != null) {
			return cosmetic;
		}

		switch (material) {
			case SAND:
				return Material.SANDSTONE;
				
			case RED_SAND:
				return Material.RED_SANDSTONE;

			case GRAVEL:
				return Material.STONE;

			default:
				break;
		};

		String type = material.toString();
		if (type.endsWith("CONCRETE_POWDER")) {
			return Material.valueOf(type.replace("_POWDER", ""));
		}

		if (isSand(material)) {
			return Material.STONE;
		}
		
		return material;
	}

	private Location getTargetLocation() {
		final Entity target = GeneralMethods.getTargetedEntity(this.caster, this.range);
		if (target != null) {
			LivingEntity e = ((LivingEntity) target);

			if (isSettingUp) {
				return e.getEyeLocation();
			}

			double r = range;
			Vector c = this.caster.getLocation().toVector();
			Vector ro = this.location.toVector();
			Vector rd = GeneralMethods.getDirection(location, e.getEyeLocation()).normalize();

			Vector oc = c.subtract(ro);
			double t = oc.dot(rd);
			double y = oc.lengthSquared() - (t * t);
			
			if (y > (r * r)) {
				return e.getEyeLocation();
			}

			double x = Math.sqrt((r * r) - y);

			double intersect1 = x - t;
			double intersect2 = x + t;

			Vector finalPosition = ro.add(rd.multiply(intersect2));
			return finalPosition.toLocation(this.caster.getWorld());
		}
		
		return GeneralMethods.getTargetedLocation(this.caster, this.range, true, getTransparentMaterials());
	}

	public boolean prepare() {
		final Block block = getTargetEarthBlock(caster, (int) selectRange);

		if (!this.isEarthbendable(block)) {
			return false;
		} else if (TempBlock.isTempBlock(block) && !isBendableEarthTempBlock(block)) {
			Optional.ofNullable(TempBlock.get(block)).ifPresent(tb -> {
				if (tb.getAbility().map(abil -> abil.getName().equals(this.getName())).orElse(false))
					disableEnemyBlasts(block.getLocation());
			});

			return false;
		}
		
		for (final EarthBlast blast : getAbilities(this.caster, EarthBlast.class)) {
			if (!blast.isProgressing) {
				blast.remove();
			}
		}

		if (block.getLocation().distanceSquared(this.caster.getLocation()) > this.selectRange * this.selectRange) {
			return false;
		}

		this.focusBlock(block);
		return true;
	}

	@Override
	public void progress() {
		if (!this.bender.canBendIgnoreBindsCooldowns(this)) {
			this.remove();
			return;
		}

		if (System.currentTimeMillis() - this.time < this.interval) {
			return;
		}

		this.time = System.currentTimeMillis();

		if (this.isAtDestination) {
			this.remove();
			return;
		}

		if (!this.isProgressing) {
			if (this.sourceBlock == null || !bender.boundAbilityMatches(getName())) {
				this.remove();
			} else if (this.sourceBlock.getLocation().distanceSquared(this.caster.getLocation()) > this.selectRange * this.selectRange) {
				this.remove();
			}
			return;
		}

        if (!TempBlock.isTempBlock(blastBlock.getBlock())) {
            this.remove();
            return;
        }

		// Checks if block has been altered by other moves
		if (!isBendableEarthTempBlock(blastBlock)) {
			if (!TempBlock.get(blastBlock.getLocation().getBlock()).getAbility().equals(blastBlock.getAbility())) {
				this.remove();
				return;
			}
		}

		if (this.blastBlock.getBlock().getY() == this.firstDestination.getBlockY()) {
			this.isSettingUp = false;
		}

		Vector direction;
		if (this.isSettingUp) {
			direction = GeneralMethods.getDirection(this.location, this.firstDestination).normalize();
		} else {
			direction = GeneralMethods.getDirection(this.location, this.destination).normalize();
		}

		this.location = this.location.clone().add(direction);
		Block block = this.location.getBlock();

		if (block.getLocation().equals(this.blastBlock.getLocation())) {
			this.location = this.location.clone().add(direction);
			block = this.location.getBlock();
		}

		if (this.isTransparent(block) && !block.isLiquid()) {
			GeneralMethods.breakBlock(block);
		} else {
			remove();
			return;
		}

		for (final Entity entity : GeneralMethods.getEntitiesAroundPoint(this.location, this.collisionRadius)) {
			if (RegionProtection.isRegionProtected(this, entity.getLocation())) {
				continue;
			}

			if (entity instanceof LivingEntity && (entity.getEntityId() != this.caster.getEntityId() || this.canHitSelf)) {
				final Location location = this.caster.getEyeLocation();
				final Vector vector = location.getDirection();
				GeneralMethods.setVelocity(this, entity, vector.normalize().multiply(this.pushFactor));
				double damage = this.damage;

				DamageHandler.damageEntity(entity, damage, this);
				this.isProgressing = false;
			}
		}

		if (!this.isProgressing) {
			this.remove();
			return;
		}

		this.blastBlock.revertBlock();
		this.blastBlock = new TempBlock(block, blockData, this);

		if (this.location.distanceSquared(this.destination) < 1) {
			this.isAtDestination = true;
			this.isProgressing = false;
		}
	}

	private void redirect(final LivingEntity caster, final Location targetlocation) {
		if (this.isProgressing) {
			if (this.location.distanceSquared(caster.getLocation()) <= this.range * this.range) {
				this.isSettingUp = false;
				this.destination = targetlocation;
			}
		}
	}

	@Override
	public void remove() {
		super.remove();
		
		if (sourceBlock != null && !sourceBlock.isReverted()) {
			sourceBlock.revertBlock();
			sourceBlock = null;
		}

		if (blastBlock != null && !blastBlock.isReverted()) {
			blastBlock.revertBlock();
			blastBlock = null;
		}
	}

	public void throwEarth() {
		if (!this.isProgressing && !isAtDestination) {
			if (this.sourceBlock == null || !this.sourceBlock.getBlock().getWorld().equals(this.caster.getWorld())) {
				return;
			}

			this.destination = this.getTargetLocation();
			this.firstDestination = this.location.clone();
			if (this.destination.getY() - this.location.getY() > 2) {
				this.firstDestination.setY(this.destination.getY() - 1);
			} else if (this.location.getY() > caster.getEyeLocation().getY() && this.location.getBlock().getRelative(BlockFace.UP).isPassable()) {
				this.firstDestination.subtract(0, 2, 0);
			} else if (this.location.getBlock().getRelative(BlockFace.UP).isPassable() && this.location.getBlock().getRelative(BlockFace.UP, 2).isPassable()) {
				this.firstDestination.add(0, 2, 0);
			} else {
				this.firstDestination.add(GeneralMethods.getDirection(this.location, this.destination).normalize().setY(0));
			}

			isProgressing = true;
			Location location = this.sourceBlock.getLocation().clone();
			playEarthbendingSound(location);

			sourceBlock.revertBlock();
			new TempBlock(location.getBlock(), Material.AIR.createBlockData(), 10000);
			blastBlock = new TempBlock(location.getBlock(), blockData, this);
		}

		if (this.isProgressing) {
			if(this.blastBlock == null || !this.blastBlock.getBlock().getWorld().equals(this.caster.getWorld())) {
				return;
			}
			
			this.destination = this.getTargetLocation();
			this.location = this.blastBlock.getLocation();
			if (this.destination.distanceSquared(this.location) <= 1) {
				this.isProgressing = false;
				this.destination = null;
			}
		}
	}

	/**
	 * This method was used for the old collision detection system. Please see
	 * {@link Collision} for the new system.
	 */
	@Deprecated
	public static boolean annihilateBlasts(final Location location, final double radius, final LivingEntity source) {
		boolean broke = false;
		for (final EarthBlast blast : getAbilities(EarthBlast.class)) {
			if (blast.location.getWorld().equals(location.getWorld()) && !source.equals(blast.caster)) {
				if (blast.location.distanceSquared(location) <= radius * radius) {
					blast.remove();
					broke = true;
				}
			}
		}

		return broke;
	}

	public static ArrayList<EarthBlast> getAroundPoint(final Location location, final double radius) {
		final ArrayList<EarthBlast> list = new ArrayList<EarthBlast>();
		for (final EarthBlast blast : getAbilities(EarthBlast.class)) {
			if (blast.location.getWorld().equals(location.getWorld())) {
				if (blast.location.distanceSquared(location) <= radius * radius) {
					list.add(blast);
				}
			}
		}

		return list;
	}

	public static EarthBlast getBlastFromSource(final Block block) {
		for (final EarthBlast blast : getAbilities(EarthBlast.class)) {
			if (blast.blastBlock.getBlock().equals(block)) {
				return blast;
			}
		}

		return null;
	}

	private static void redirectTargettedBlasts(final LivingEntity caster, final ArrayList<EarthBlast> ignore) {
		if (Optional.ofNullable(Bender.get(caster)).map(b -> !b.hasUnlocked("EarthBlastRedirect")).orElse(true)) return;
		for (final EarthBlast blast : getAbilities(EarthBlast.class)) {
			if (!blast.isProgressing || ignore.contains(blast)) {
				continue;
			} else if (!blast.location.getWorld().equals(caster.getWorld())) {
				continue;
			} else if (RegionProtection.isRegionProtected(blast, blast.location)) {
				continue;
			} else if (blast.caster.equals(caster)) {
				blast.redirect(caster, blast.getTargetLocation());
			}

			final Location location = caster.getEyeLocation();
			final Vector vector = location.getDirection();
			final Location mloc = blast.location;

			if (mloc.distanceSquared(location) <= blast.range * blast.range 
					&& GeneralMethods.getDistanceFromLine(vector, location, blast.location) < blast.deflectRange 
					&& mloc.distanceSquared(location.clone().add(vector)) < mloc.distanceSquared(location.clone().add(vector.clone().multiply(-1)))) {
				blast.redirect(caster, blast.getTargetLocation());
			}
		}
	}

	public static void removeAroundPoint(final Location location, final double radius) {
		for (final EarthBlast blast : getAbilities(EarthBlast.class)) {
			if (blast.location.getWorld().equals(location.getWorld())) {
				if (blast.location.distanceSquared(location) <= radius * radius) {
					blast.remove();
				}
			}
		}
	}

	public static void throwEarth(final LivingEntity caster) {
		final ArrayList<EarthBlast> ignore = new ArrayList<>();
		final Bender bender = Bender.get(caster);
		EarthBlast earthBlast = null;

		if (bender == null) {
			return;
		}

		for (final EarthBlast blast : getAbilities(caster, EarthBlast.class)) {
			if (!blast.isProgressing && bender.canBend(blast)) {
				blast.throwEarth();
				ignore.add(blast);
				earthBlast = blast;
			}
		}

		if (earthBlast != null) {
			bender.addCooldown(earthBlast);
		}

		redirectTargettedBlasts(caster, ignore);
	}

	@Override
	public String getName() {
		return "EarthBlast";
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
	public boolean isCollidable() {
		return this.isProgressing;
	}

	@Override
	public double getCollisionRadius() {
		return this.collisionRadius;
	}

	public Material getSourcetype() {
		return this.blastType;
	}

	public void setSourcetype(final Material sourcetype) {
		this.blastType = sourcetype;
	}

	public Block getSourceBlock() {
		return this.blastBlock.getBlock();
	}

	public void setSourceBlock(final Block sourceBlock) {
		this.blastBlock = new TempBlock(sourceBlock, blockData, this);
	}
}
