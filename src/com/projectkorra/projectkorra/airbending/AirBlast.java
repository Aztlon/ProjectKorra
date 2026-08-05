// Decompiled with: CFR 0.152
// Class Version: 8
package com.projectkorra.projectkorra.airbending;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Switch;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.Bender;
import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.command.Commands;
import com.projectkorra.projectkorra.earthbending.lava.LavaFlow;
import com.projectkorra.projectkorra.object.HorizontalVelocityTracker;
import com.projectkorra.projectkorra.phasing.PhasedEntityEffectManager;
import com.projectkorra.projectkorra.phasing.PhasedSoundManager;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AirBlast extends AirAbility {
	private static final int MAX_TICKS = 10000;
	private static final Map<LivingEntity, Location> ORIGINS = new ConcurrentHashMap<>();
	public static final Material[] DOORS = Tag.WOODEN_DOORS.getValues().toArray(new Material[0]);
	public static final Material[] TDOORS = Tag.WOODEN_TRAPDOORS.getValues().toArray(new Material[0]);
	public static final Material[] BUTTONS = Tag.BUTTONS.getValues().toArray(new Material[0]);
	@Attribute(value="Knockback")
	private double pushFactorSlide;
	private boolean canFlickLevers;
	private boolean canOpenDoors;
	private boolean canPressButtons;
	private boolean canCoolLava;
	private boolean isFromOtherOrigin;
	private boolean showParticles;
	private int ticks;
	private int particles;
	@Attribute(value="Cooldown")
	private long cooldown;
	private double speedFactor;
	@Attribute(value="Range")
	private double range;
	@Attribute(value="SelfPush")
	private double pushFactor;
	@Attribute(value="Knockback")
	private double pushFactorForOthers;
	@Attribute(value="Damage")
	private double damage;
	@Attribute(value="Speed")
	private double speed;
	@Attribute(value="Radius")
	private double radius;
	private Location location;
	private Location origin;
	private Vector direction;
	private AirBurst source;
	private Random random;
	private ArrayList<Block> affectedLevers;
	private ArrayList<Entity> affectedEntities;

	public AirBlast(LivingEntity caster) {
		super(caster);
		if (this.bender.isOnCooldown(this)) {
			return;
		}
		if (caster.getEyeLocation().getBlock().isLiquid()) {
			return;
		}
		this.setFields();
		if (ORIGINS.containsKey(caster)) {
			Entity entity = GeneralMethods.getTargetedEntity(caster, this.getRange());
			this.isFromOtherOrigin = true;
			this.origin = ORIGINS.get(caster);
			ORIGINS.remove(caster);
			this.direction = entity != null ? GeneralMethods.getDirection(this.origin, GeneralMethods.getTargetedLocation(player, this.range, false, false, new Material[0])).normalize() : GeneralMethods.getDirection(this.origin, GeneralMethods.getTargetedLocation(player, this.range, new Material[0])).normalize();
		} else {
			this.origin = caster.getEyeLocation();
			this.direction = caster.getEyeLocation().getDirection().normalize();
		}
		if (!(Double.isFinite(this.direction.getX()) && Double.isFinite(this.direction.getY()) && Double.isFinite(this.direction.getZ()))) {
			return;
		}
		this.location = this.origin.clone();
		this.bender.addCooldown(this);
		this.start();
	}

	public AirBlast(LivingEntity caster, Location location, Vector direction, double modifiedPushFactor, AirBurst burst) {
		super(caster);
		if (location.getBlock().isLiquid()) {
			return;
		}
		this.source = burst;
		this.origin = location.clone();
		this.direction = direction.clone();
		this.location = location.clone();
		this.setFields();
		this.affectedLevers = new ArrayList<>();
		this.affectedEntities = new ArrayList<>();
		this.canOpenDoors = false;
		this.canPressButtons = false;
		this.canFlickLevers = false;
		if (this.bender.isAvatarState()) {
			this.pushFactor = AirBlast.getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirBlast.Push.Self");
			this.pushFactorForOthers = AirBlast.getConfig().getDouble("Abilities.Avatar.AvatarState.Air.AirBlast.Push.Entities");
		}
		this.pushFactor *= modifiedPushFactor;
		this.start();
	}

	private void setFields() {
		this.particles = AirBlast.getConfig().getInt("Abilities.Air.AirBlast.Particles");
		this.cooldown = AirBlast.getConfig().getLong("Abilities.Air.AirBlast.Cooldown");
		this.range = AirBlast.getConfig().getDouble("Abilities.Air.AirBlast.Range");
		this.speed = AirBlast.getConfig().getDouble("Abilities.Air.AirBlast.Speed");
		this.radius = AirBlast.getConfig().getDouble("Abilities.Air.AirBlast.Radius");
		this.pushFactor = AirBlast.getConfig().getDouble("Abilities.Air.AirBlast.Push.Self");
		this.pushFactorSlide = AirBlast.getConfig().getDouble("Abilities.Air.AirBlast.PushSlideFactor");
		this.pushFactorForOthers = AirBlast.getConfig().getDouble("Abilities.Air.AirBlast.Push.Entities");
		this.canFlickLevers = AirBlast.getConfig().getBoolean("Abilities.Air.AirBlast.CanFlickLevers");
		this.canOpenDoors = AirBlast.getConfig().getBoolean("Abilities.Air.AirBlast.CanOpenDoors");
		this.canPressButtons = AirBlast.getConfig().getBoolean("Abilities.Air.AirBlast.CanPressButtons");
		this.canCoolLava = AirBlast.getConfig().getBoolean("Abilities.Air.AirBlast.CanCoolLava");
		this.isFromOtherOrigin = false;
		this.showParticles = true;
		this.random = new Random();
		this.affectedLevers = new ArrayList<>();
		this.affectedEntities = new ArrayList<>();
	}

	private static void playOriginEffect(LivingEntity caster) {
		if (!ORIGINS.containsKey(caster)) {
			return;
		}
		Location origin = ORIGINS.get(caster);
		Bender bender = Bender.get(caster);
		if (bender == null || caster.isDead()) {
			return;
		}
		if (!origin.getWorld().equals(caster.getWorld())) {
			ORIGINS.remove(caster);
			return;
		}
		if (!bender.canBendIgnoreBindsCooldowns(AirBlast.getAbility("AirBlast"))) {
			ORIGINS.remove(caster);
			return;
		}
		if (origin.distanceSquared(caster.getEyeLocation()) > AirBlast.getSelectRange() * AirBlast.getSelectRange()) {
			ORIGINS.remove(caster);
			return;
		}
		playAirbendingParticles(caster, origin, AirBlast.getSelectParticles());
	}

	public static void progressOrigins() {
		for (LivingEntity caster : ORIGINS.keySet()) {
			AirBlast.playOriginEffect(caster);
		}
	}

	public static void setOrigin(LivingEntity caster) {
		Location location = GeneralMethods.getTargetedLocation(caster, AirBlast.getSelectRange(), AirBlast.getTransparentMaterials());
		setOrigin(caster, location);
	}

	public static void setOrigin(LivingEntity caster, Location location) {
		Bender bender = Bender.get(caster);
		if (bender == null) {
			return;
		}
		if (location.getBlock().isLiquid() || GeneralMethods.isSolid(location.getBlock())) {
			return;
		}
		if (RegionProtection.isRegionProtected(caster, location, "AirBlast")) {
			return;
		}
		ORIGINS.put(caster, location);
	}

	private void advanceLocation() {
		if (this.showParticles) {
			playAirbendingParticles(this.location, this.particles, 0.275f, 0.275f, 0.275f);
		}
		if (this.random.nextInt(4) == 0) {
			playAirbendingSound(this.location);
		}
		BlockIterator blocks = new BlockIterator(this.getLocation().getWorld(), this.location.toVector(), this.direction, 0.0, (int)Math.ceil(this.direction.clone().multiply(this.speedFactor).length()));
		while (blocks.hasNext() && this.checkLocation(blocks.next())) {
		}
		this.location.add(this.direction.clone().multiply(this.speedFactor));
	}

	public boolean checkLocation(Block block) {
		if (GeneralMethods.checkDiagonalWall(block.getLocation(), this.direction)) {
			this.remove();
			return false;
		}
		if (!(block.isPassable() && !block.isLiquid() || this.affectedLevers.contains(block))) {
			if (block.getType() == Material.LAVA && this.canCoolLava) {
				if (LavaFlow.isLavaFlowBlock(block)) {
					LavaFlow.removeBlock(block);
				} else if (block.getBlockData() instanceof Levelled && ((Levelled)block.getBlockData()).getLevel() == 0) {
					new TempBlock(block, Material.OBSIDIAN, this);
				} else {
					new TempBlock(block, Material.COBBLESTONE, this);
				}
			}
			this.remove();
			return false;
		}
		if (!this.processBlock(block.getLocation())) {
			this.remove();
			return false;
		}
		return true;
	}

	private void oldPkEffect(Entity entity) {
		boolean isUser = entity.getUniqueId() == this.caster.getUniqueId();
		if (this.isFromOtherOrigin || !isUser) {
			double comp;
			Vector velocity = entity.getVelocity();
			double max = 1.0 / this.getPushFactorForOthers();
			double factor = isUser ? this.getPushFactor() : this.getPushFactorForOthers();
			Vector push = this.direction.clone();
			if (Math.abs(push.getY()) > max && !isUser) {
				if (push.getY() < 0.0) {
					push.setY(-max);
				} else {
					push.setY(max);
				}
			}
			factor *= 1.0 - this.location.distance(this.origin) / (2.0 * this.range);
			if (isUser && GeneralMethods.isSolid(this.caster.getLocation().add(0.0, -0.5, 0.0).getBlock())) {
				factor *= 0.5;
			}
			if ((comp = velocity.dot(push.clone().normalize())) > factor) {
				velocity.multiply(0.5);
				velocity.add(push.clone().normalize().multiply(velocity.clone().dot(push.clone().normalize())));
			} else if (comp + factor * 0.5 > factor) {
				velocity.add(push.clone().multiply(factor - comp));
			} else {
				velocity.add(push.clone().multiply(factor * 0.5));
			}
			if (Double.isNaN(velocity.length())) {
				return;
			}
			GeneralMethods.setVelocity(this, entity, velocity);
			PhasedEntityEffectManager.setFireTicks(this, entity, 0);
			AirBlast.breakBreathbendingHold(entity);
		}
	}

	@Override
	public void progress() {
		if (this.caster.isDead()) {
			this.remove();
			return;
		}
		if (RegionProtection.isRegionProtected(this, this.location)) {
			this.remove();
			return;
		}

		if (bPlayer != null && bPlayer.isQueueAirBlastStop()) {
			this.remove();
			bPlayer.setQueueAirBlastStop(false);
			return;
		}

		this.speedFactor = this.getSpeed() * ((double)ProjectKorra.time_step / 1000.0);
		++this.ticks;
		if (this.ticks > 10000) {
			this.remove();
			return;
		}
		for (Block testblock : GeneralMethods.getBlocksAroundPoint(this.location, this.getRadius())) {
			if (this.processBlock(testblock.getLocation())) continue;
			this.remove();
			return;
		}
		double dist = 0.0;
		if (this.location.getWorld().equals(this.origin.getWorld())) {
			dist = this.location.distance(this.origin);
		}
		if (Double.isNaN(dist) || dist > this.getRange()) {
			this.remove();
			return;
		}
		for (Entity entity : GeneralMethods.getEntitiesAroundPoint(this.location, this.radius)) {
			if (RegionProtection.isRegionProtected(this, entity.getLocation()) || entity instanceof Player && Commands.invincible.contains(entity.getName())) continue;
			if (this.getSource() == null) {
				this.oldPkEffect(entity);
				continue;
			}
			this.affect(entity);
		}
		this.advanceLocation();
	}

	private boolean processBlock(Location location) {
		Lightable lightable;
		final Block testblock = location.getBlock();
		if (RegionProtection.isRegionProtected(this, location)) {
			return false;
		}
		if (FireAbility.isFire(testblock.getType())) {
			if (TempBlock.isTempBlock(testblock)) {
				TempBlock.removeBlock(testblock);
			} else {
				testblock.setType(Material.AIR);
			}
			testblock.getWorld().playEffect(testblock.getLocation(), Effect.EXTINGUISH, 0);
			return false;
		}
		if (this.affectedLevers.contains(testblock)) {
			return false;
		}
		if (Arrays.asList(DOORS).contains(testblock.getType())) {
			if (testblock.getBlockData() instanceof Door door) {
				BlockFace face = door.getFacing();
				Vector toPlayer = GeneralMethods.getDirection(testblock.getLocation(), this.caster.getLocation().getBlock().getLocation());
				double[] dims = new double[]{toPlayer.getX(), toPlayer.getY(), toPlayer.getZ()};
				for (int i = 0; i < 3; ++i) {
					BlockFace bf;
					if (i == 1 || !((bf = GeneralMethods.getBlockFaceFromValue(i, dims[i])) == face ? !door.isOpen() : bf.getOppositeFace() == face && door.isOpen())) continue;
					return false;
				}
				door.setOpen(!door.isOpen());
				testblock.setBlockData(door);
				PhasedSoundManager.playSound(this, testblock.getLocation(), Sound.valueOf("BLOCK_WOODEN_DOOR_" + (door.isOpen() ? "OPEN" : "CLOSE")), 0.5f, 0.0f);
				this.affectedLevers.add(testblock);
			}
		} else if (Arrays.asList(TDOORS).contains(testblock.getType())) {
			if (testblock.getBlockData() instanceof TrapDoor tDoor) {
				if ((this.origin.getY() < (double) testblock.getY()) != tDoor.isOpen()) {
					return false;
				}
				tDoor.setOpen(!tDoor.isOpen());
				testblock.setBlockData(tDoor);
				PhasedSoundManager.playSound(this, testblock.getLocation(), Sound.valueOf("BLOCK_WOODEN_TRAPDOOR_" + (tDoor.isOpen() ? "OPEN" : "CLOSE")), 0.5f, 0.0f);
			}
		} else if (Arrays.asList(BUTTONS).contains(testblock.getType())) {
			if (testblock.getBlockData() instanceof Switch button) {
				if (!button.isPowered()) {
					button.setPowered(true);
					testblock.setBlockData(button);
					this.affectedLevers.add(testblock);
					new BukkitRunnable(){

						public void run() {
							button.setPowered(false);
							testblock.setBlockData(button);
							AirBlast.this.affectedLevers.remove(testblock);
							PhasedSoundManager.playSound(AirBlast.this, testblock.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF, 0.5f, 0.0f);
						}
					}.runTaskLater(ProjectKorra.plugin, 15L);
				}
				PhasedSoundManager.playSound(this, testblock.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.5f, 0.0f);
			}
		} else if (testblock.getType() == Material.LEVER) {
			if (testblock.getBlockData() instanceof Switch lever) {
				lever.setPowered(!lever.isPowered());
				testblock.setBlockData(lever);
				this.affectedLevers.add(testblock);
				PhasedSoundManager.playSound(this, testblock.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 0);
			}
		} else if ((testblock.getType().toString().contains("CANDLE") || testblock.getType().toString().contains("CAMPFIRE") || testblock.getType() == Material.REDSTONE_WALL_TORCH) && testblock.getBlockData() instanceof Lightable && (lightable = (Lightable)testblock.getBlockData()).isLit()) {
			lightable.setLit(false);
			testblock.setBlockData(lightable);
			testblock.getWorld().playEffect(testblock.getLocation(), Effect.EXTINGUISH, 0);
		}
		return true;
	}

	@Deprecated
	public static boolean removeAirBlastsAroundPoint(Location location, double radius) {
		boolean removed = false;
		for (AirBlast airBlast : AirBlast.getAbilities(AirBlast.class)) {
			Location airBlastlocation = airBlast.location;
			if (location.getWorld() != airBlastlocation.getWorld()) continue;
			if (location.distanceSquared(airBlastlocation) <= radius * radius) {
				airBlast.remove();
			}
			removed = true;
		}
		return removed;
	}

	private void affect(Entity entity) {
		if (entity instanceof Player && Commands.invincible.contains(entity.getName())) {
			return;
		}
		boolean isUser = entity.getUniqueId() == this.caster.getUniqueId();
		double knockback = this.getPushFactorForOthers();
		if (isUser) {
			if (this.isFromOtherOrigin) {
				knockback = this.getPushFactor();
			} else {
				return;
			}
		}
		double max = this.getSpeed() / this.speedFactor;
		Vector push = this.direction.clone();
		if (Math.abs(push.getY()) > max && !isUser) {
			if (push.getY() < 0.0) {
				push.setY(-max);
			} else {
				push.setY(max);
			}
		}
		if (this.location.getWorld().equals(this.origin.getWorld())) {
			knockback *= 1.0 - this.location.distance(this.origin) / (2.0 * this.getRange());
		}
		if (GeneralMethods.isSolid(entity.getLocation().add(0.0, -0.5, 0.0).getBlock()) && this.source == null) {
			knockback *= 0.85;
		}
		push.normalize().multiply(knockback);
		if (Math.abs(entity.getVelocity().dot(push)) > knockback && (double)entity.getVelocity().angle(push) > 1.0471975511965976) {
			push.normalize().add(entity.getVelocity()).multiply(knockback);
		}
		GeneralMethods.setVelocity(this, entity, push);
		new HorizontalVelocityTracker(entity, this.caster, 200L, Objects.requireNonNullElse(this.source, this));
		if (this.damage > 0.0 && entity instanceof LivingEntity && !entity.equals(this.caster) && !this.affectedEntities.contains(entity)) {
			DamageHandler.damageEntity(entity, this.damage, Objects.requireNonNullElse(this.source, this));
			this.affectedEntities.add(entity);
		}
		final boolean wasOnFire = entity.getFireTicks() > 0;
		if (PhasedEntityEffectManager.setFireTicks(this, entity, 0) && wasOnFire) {
			entity.getWorld().playEffect(entity.getLocation(), Effect.EXTINGUISH, 0);
		}
		AirBlast.breakBreathbendingHold(entity);
	}

	@Override
	public String getName() {
		return "AirBlast";
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
	public double getCollisionRadius() {
		return this.getRadius();
	}

	public static int getSelectParticles() {
		return getConfig().getInt("Abilities.Air.AirBlast.SelectParticles");
	}

	public static double getSelectRange() {
		return getConfig().getDouble("Abilities.Air.AirBlast.SelectRange");
	}
}
