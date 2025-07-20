package com.projectkorra.projectkorra.earthbending;

import java.util.ArrayList;

import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AbstractSkill;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.util.TempFallingBlock;

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

	public EarthBlast(final Player player) {
		super(player);

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

		if (this.bPlayer.isAvatarState()) {
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
			if (blast.player.equals(this.player)) {
				continue;
			} else if (!blast.location.getWorld().equals(this.player.getWorld())) {
				continue;
			} else if (!blast.isProgressing) {
				continue;
			} else if (RegionProtection.isRegionProtected(this, blast.location)) {
				continue;
			}

			final Location blastLocation = blast.location;

			if(loc.distanceSquared(blastLocation) < deflectRange * deflectRange) {
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
		
		if(TempBlock.isTempBlock(block)) {
			TempBlock.get(block).revertBlock();
		}
		
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
		final Material cosmetic = earthCosmetic(player);
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
		final Entity target = GeneralMethods.getTargetedEntity(this.player, this.range);
		if (target != null) {
			LivingEntity e = ((LivingEntity) target);

			if (isSettingUp) {
				return e.getEyeLocation();
			}

			double r = range;
			Vector c = this.player.getLocation().toVector();
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
			return finalPosition.toLocation(this.player.getWorld());
		}
		
		return GeneralMethods.getTargetedLocation(this.player, this.range, true, getTransparentMaterials());
	}

	public boolean prepare() {
		final Block block = getTargetEarthBlock(player, (int) selectRange);

		if (block == null || !this.isEarthbendable(block)) {
			return false;
		} else if (TempBlock.isTempBlock(block) && !isBendableEarthTempBlock(block)) {
			try {
				if (TempBlock.get(block).getAbility().get().getName().equals(this.getName())) {
					disableEnemyBlasts(block.getLocation());
				}
			} catch (Exception ignored) {}

			return false;
		}
		
		for (final EarthBlast blast : getAbilities(this.player, EarthBlast.class)) {
			if (!blast.isProgressing) {
				blast.remove();
			}
		}

		if (block.getLocation().distanceSquared(this.player.getLocation()) > this.selectRange * this.selectRange) {
			return false;
		}

		this.focusBlock(block);
		return true;
	}

	@Override
	public void progress() {
		if (!this.bPlayer.canBendIgnoreBindsCooldowns(this)) {
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

		if (!this.isProgressing && !this.isAtDestination) {
			if (this.sourceBlock == null || !this.bPlayer.getBoundAbilityName().equals(this.getName())) {
				this.remove();
				return;
			} else if (this.sourceBlock.getLocation().distanceSquared(this.player.getLocation()) > this.selectRange * this.selectRange) {
				this.remove();
				return;
			}
		}

		if (!this.isProgressing) {
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

			if (entity instanceof LivingEntity && (entity.getEntityId() != this.player.getEntityId() || this.canHitSelf)) {
				final Location location = this.player.getEyeLocation();
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

	private void redirect(final Player player, final Location targetlocation) {
		if (this.isProgressing) {
			if (this.location.distanceSquared(player.getLocation()) <= this.range * this.range) {
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
			if(this.sourceBlock == null || !this.sourceBlock.getBlock().getWorld().equals(this.player.getWorld())) {
				return;
			}

			this.destination = this.getTargetLocation();
			this.firstDestination = this.location.clone();
			if (this.destination.getY() - this.location.getY() > 2) {
				this.firstDestination.setY(this.destination.getY() - 1);
			} else if (this.location.getY() > player.getEyeLocation().getY() && this.location.getBlock().getRelative(BlockFace.UP).isPassable()) {
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
			if(this.blastBlock == null || !this.blastBlock.getBlock().getWorld().equals(this.player.getWorld())) {
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
	public static boolean annihilateBlasts(final Location location, final double radius, final Player source) {
		boolean broke = false;
		for (final EarthBlast blast : getAbilities(EarthBlast.class)) {
			if (blast.location.getWorld().equals(location.getWorld()) && !source.equals(blast.player)) {
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

	private static void redirectTargettedBlasts(final Player player, final ArrayList<EarthBlast> ignore) {
		if (AbstractSkill.isLocked("EarthBlastRedirect", player)) return;
		for (final EarthBlast blast : getAbilities(EarthBlast.class)) {
			if (!blast.isProgressing || ignore.contains(blast)) {
				continue;
			} else if (!blast.location.getWorld().equals(player.getWorld())) {
				continue;
			} else if (RegionProtection.isRegionProtected(blast, blast.location)) {
				continue;
			} else if (blast.player.equals(player)) {
				blast.redirect(player, blast.getTargetLocation());
			}

			final Location location = player.getEyeLocation();
			final Vector vector = location.getDirection();
			final Location mloc = blast.location;

			if (mloc.distanceSquared(location) <= blast.range * blast.range 
					&& GeneralMethods.getDistanceFromLine(vector, location, blast.location) < blast.deflectRange 
					&& mloc.distanceSquared(location.clone().add(vector)) < mloc.distanceSquared(location.clone().add(vector.clone().multiply(-1)))) {
				blast.redirect(player, blast.getTargetLocation());
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

	public static void throwEarth(final Player player) {
		final ArrayList<EarthBlast> ignore = new ArrayList<>();
		final BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
		EarthBlast earthBlast = null;

		if (bPlayer == null) {
			return;
		}

		for (final EarthBlast blast : getAbilities(player, EarthBlast.class)) {
			if (!blast.isProgressing && bPlayer.canBend(blast)) {
				blast.throwEarth();
				ignore.add(blast);
				earthBlast = blast;
			}
		}

		if (earthBlast != null) {
			bPlayer.addCooldown(earthBlast);
		}

		redirectTargettedBlasts(player, ignore);
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

	public boolean isProgressing() {
		return this.isProgressing;
	}

	public void setProgressing(final boolean isProgressing) {
		this.isProgressing = isProgressing;
	}

	public boolean isAtDestination() {
		return this.isAtDestination;
	}

	public void setAtDestination(final boolean isAtDestination) {
		this.isAtDestination = isAtDestination;
	}

	public boolean isSettingUp() {
		return this.isSettingUp;
	}

	public void setSettingUp(final boolean isSettingUp) {
		this.isSettingUp = isSettingUp;
	}

	public boolean isCanHitSelf() {
		return this.canHitSelf;
	}

	public void setCanHitSelf(final boolean canHitSelf) {
		this.canHitSelf = canHitSelf;
	}

	public long getTime() {
		return this.time;
	}

	public void setTime(final long time) {
		this.time = time;
	}

	public long getInterval() {
		return this.interval;
	}

	public void setInterval(final long interval) {
		this.interval = interval;
	}

	public double getRange() {
		return this.range;
	}

	public void setRange(final double range) {
		this.range = range;
	}

	public double getDamage() {
		return this.damage;
	}

	public void setDamage(final double damage) {
		this.damage = damage;
	}

	public double getSpeed() {
		return this.speed;
	}

	public void setSpeed(final double speed) {
		this.speed = speed;
	}

	public double getPushFactor() {
		return this.pushFactor;
	}

	public void setPushFactor(final double pushFactor) {
		this.pushFactor = pushFactor;
	}

	public double getSelectRange() {
		return this.selectRange;
	}

	public void setSelectRange(final double selectRange) {
		this.selectRange = selectRange;
	}

	public double getDeflectRange() {
		return this.deflectRange;
	}

	public void setDeflectRange(final double deflectRange) {
		this.deflectRange = deflectRange;
	}

	public void setCollisionRadius(final double collisionRadius) {
		this.collisionRadius = collisionRadius;
	}

	public Material getSourcetype() {
		return this.blastType;
	}

	public void setSourcetype(final Material sourcetype) {
		this.blastType = sourcetype;
	}

	public Location getDestination() {
		return this.destination;
	}

	public void setDestination(final Location destination) {
		this.destination = destination;
	}

	public Location getFirstDestination() {
		return this.firstDestination;
	}

	public void setFirstDestination(final Location firstDestination) {
		this.firstDestination = firstDestination;
	}

	public Block getSourceBlock() {
		return this.blastBlock.getBlock();
	}

	public void setSourceBlock(final Block sourceBlock) {
		this.blastBlock = new TempBlock(sourceBlock, blockData, this);
	}

	public void setCooldown(final long cooldown) {
		this.cooldown = cooldown;
	}

	public void setLocation(final Location location) {
		this.location = location;
	}
}
