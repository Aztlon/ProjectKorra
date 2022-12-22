package com.projectkorra.projectkorra.firebending.lightning;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.Ability;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.ability.LightningAbility;
import com.projectkorra.projectkorra.attribute.Attribute;
import com.projectkorra.projectkorra.firebending.FireJet;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.MovementHandler;
import com.projectkorra.projectkorra.util.ParticleEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Lightning extends LightningAbility {
	private static final int POINT_GENERATION = 5;

	@Attribute("Charged")
	private boolean charged;

	private boolean hitWater;
	private boolean hitIce;
	private boolean selfHitWater;
	private boolean selfHitClose;

	private boolean allowOnFireJet;

	@Attribute("ArcOnIce")
	private boolean arcOnIce;

	private int waterArcs;

	@Attribute("Range")
	private double range;

	@Attribute("ChargeTime")
	private double chargeTime;

	@Attribute("SubArcChance")
	private double subArcChance;

	@Attribute("Damage")
	private double damage;

	@Attribute("MaxChainArcs")
	private double maxChainArcs;

	@Attribute("ChainRange")
	private double chainRange;

	@Attribute("WaterArcRange")
	private double waterArcRange;

	@Attribute("ChainArcChance")
	private double chainArcChance;

	@Attribute("StunChance")
	private double stunChance;

	@Attribute("StunDuration")
	private double stunDuration;

	@Attribute("MaxArcAngle")
	private double maxArcAngle;

	private double particleRotation;
	private long time;

	@Attribute("Cooldown")
	private long cooldown;

	private State state;
	private Location origin;
	private Location destination;
	private ArrayList<Entity> affectedEntities;
	private ArrayList<Arc> arcs;
	private ArrayList<BukkitRunnable> tasks;
	private ArrayList<Location> locations;
	private int counter;
	public float radius = 1F;
	public float grow = 0;
	public double radials = 0.19634954084936207D;
	public int circles = 3;
	public int helixes = 4;
	protected int step = 0;
	private int currPoint;

	public enum State {
		START, STRIKE, MAINBOLT;
	}

	public Lightning(Player player) {
		super(player);
		if (!this.bPlayer.canBend((CoreAbility)this))
			return;
		if (hasAbility(player, Lightning.class) &&
				!((Lightning)getAbility(player, Lightning.class)).isCharged())
			return;
		this.charged = false;
		this.hitWater = false;
		this.hitIce = false;
		this.time = System.currentTimeMillis();
		this.state = State.START;
		this.affectedEntities = new ArrayList<>();
		this.arcs = new ArrayList<>();
		this.tasks = new ArrayList<>();
		this.locations = new ArrayList<>();
		this.selfHitWater = getConfig().getBoolean("Abilities.Fire.Lightning.SelfHitWater");
		this.selfHitClose = getConfig().getBoolean("Abilities.Fire.Lightning.SelfHitClose");
		this.arcOnIce = getConfig().getBoolean("Abilities.Fire.Lightning.ArcOnIce");
		this.range = getConfig().getDouble("Abilities.Fire.Lightning.Range");
		this.damage = getConfig().getDouble("Abilities.Fire.Lightning.Damage");
		this.maxArcAngle = getConfig().getDouble("Abilities.Fire.Lightning.MaxArcAngle");
		this.subArcChance = getConfig().getDouble("Abilities.Fire.Lightning.SubArcChance");
		this.chainRange = getConfig().getDouble("Abilities.Fire.Lightning.ChainArcRange");
		this.chainArcChance = getConfig().getDouble("Abilities.Fire.Lightning.ChainArcChance");
		this.waterArcRange = getConfig().getDouble("Abilities.Fire.Lightning.WaterArcRange");
		this.stunChance = getConfig().getDouble("Abilities.Fire.Lightning.StunChance");
		this.stunDuration = getConfig().getDouble("Abilities.Fire.Lightning.StunDuration");
		this.maxChainArcs = getConfig().getInt("Abilities.Fire.Lightning.MaxChainArcs");
		this.waterArcs = getConfig().getInt("Abilities.Fire.Lightning.WaterArcs");
		this.chargeTime = getConfig().getLong("Abilities.Fire.Lightning.ChargeTime");
		this.cooldown = getConfig().getLong("Abilities.Fire.Lightning.Cooldown");
		this.allowOnFireJet = getConfig().getBoolean("Abilities.Fire.Lightning.AllowOnFireJet");
		this.range = getDayFactor(this.range);
		this.subArcChance = getDayFactor(this.subArcChance);
		this.damage = getDayFactor(this.damage);
		this.maxChainArcs = getDayFactor(this.maxChainArcs);
		this.chainArcChance = getDayFactor(this.chainArcChance);
		this.chainRange = getDayFactor(this.chainRange);
		this.waterArcRange = getDayFactor(this.waterArcRange);
		this.stunChance = getDayFactor(this.stunChance);
		this.stunDuration = getDayFactor(this.stunDuration);
		if (this.bPlayer.isAvatarState()) {
			this.chargeTime = getConfig().getLong("Abilities.Avatar.AvatarState.Fire.Lightning.ChargeTime");
			this.cooldown = getConfig().getLong("Abilities.Avatar.AvatarState.Fire.Lightning.Cooldown");
			this.damage = getConfig().getDouble("Abilities.Avatar.AvatarState.Fire.Lightning.Damage");
		}
		start();
	}

	public void electrocute(LivingEntity lent) {
		playLightningbendingSound(lent.getLocation());
		playLightningbendingSound(this.player.getLocation());
		lent.getWorld().playSound(lent.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 5.0F, 1.35F);
		lent.getWorld().playSound(lent.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 5.0F, 0.65F);
		this.player.getWorld().playSound(this.player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 5.0F, 1.35F);
		this.player.getWorld().playSound(this.player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 5.0F, 0.65F);
		ParticleEffect.FLASH.display(lent.getLocation(), 2, 1.0D, 1.0D, 1.0D);
		ParticleEffect.CRIT_MAGIC.display(lent.getLocation(), 10, 1.0D, 1.0D, 1.0D, 0.11999999731779099D);
		DamageHandler.damageEntity((Entity)lent, this.damage, (Ability)this);
		if (Math.random() <= this.stunChance) {
			MovementHandler mh = new MovementHandler(lent, (CoreAbility)this);
			mh.stopWithDuration((long)this.stunDuration, Element.LIGHTNING.getColor() + "* Struck by Lightning *");
		}
	}

	private boolean isTransparentForLightning(Player player, Block block) {
		if (isTransparent(block)) {
			if (GeneralMethods.isRegionProtectedFromBuild((Ability)this, block.getLocation()))
				return false;
			if (isIce(block))
				return this.arcOnIce;
			return true;
		}
		return false;
	}

	public void progress() {
		if (this.player.isDead() || !this.player.isOnline()) {
			removeWithTasks();
			return;
		}
		if (!this.bPlayer.canBendIgnoreCooldowns((CoreAbility)this)) {
			remove();
			return;
		}
		if (CoreAbility.hasAbility(this.player, FireJet.class) && !this.allowOnFireJet) {
			removeWithTasks();
			return;
		}
		this.locations.clear();
		if (this.state == State.START) {
			if (this.bPlayer.isOnCooldown((Ability)this)) {
				remove();
				return;
			}
			if ((System.currentTimeMillis() - this.time) > this.chargeTime)
				this.charged = true;
			if (this.charged) {
				if (this.player.isSneaking()) {
					Location loc = this.player.getEyeLocation().add(this.player.getEyeLocation().getDirection().normalize().multiply(1.2D));
					loc.add(0.0D, 0.45D, 0.0D);

					playLightningbendingParticle(loc, 0.75, 0.75, 0.75);
					thundergrid();

					loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.65F, 1.35F);
				} else {
					this.state = State.MAINBOLT;
					this.bPlayer.addCooldown((Ability)this);
					Entity target = GeneralMethods.getTargetedEntity(this.player, this.range);
					this.origin = this.player.getEyeLocation();
					if (target != null) {
						this.destination = target.getLocation();
					} else {
						this.destination = this.player.getEyeLocation().add(this.player.getEyeLocation().getDirection().normalize().multiply(this.range));
					}
				}
			} else {
				if (!this.player.isSneaking()) {
					remove();
					return;
				}
				Location localLocation1 = this.player.getLocation();
				double d1 = 0.44D;
				double d2 = 0.35D;
				double d3 = 2.1D;
				double d4 = 2.1D;
				double d5 = -0.22D * this.particleRotation;
				double d6 = -0.13D * this.particleRotation;
				double d7 = localLocation1.getX() + 1.3D * Math.cos(d5);
				double d8 = localLocation1.getZ() + 1.3D * Math.sin(d5);
				double newY = localLocation1.getY() + 1.0D + 1.0D * Math.cos(d6);
				Location localLocation2 = new Location(this.player.getWorld(), d7, newY, d8);

				playLightningbendingParticle(localLocation2);
				ParticleEffect.END_ROD.display(localLocation2, 3, 0, 0, 0, 0.04);
				ParticleEffect.CRIT_MAGIC.display(localLocation2, 3, 0, 0, 0, 0.04);
				thunderingcharge(60, 2.35f, 2);

				playLightningbendingSound(localLocation2);
				this.particleRotation += 1.5D;

			}

		} else if (this.state == State.MAINBOLT) {
			Arc mainArc = new Arc(this.origin, this.destination);
			mainArc.generatePoints(5);
			this.arcs.add(mainArc);
			ArrayList<Arc> subArcs = mainArc.generateArcs(this.subArcChance, this.range / 2.0D, this.maxArcAngle);
			this.arcs.addAll(subArcs);
			this.state = State.STRIKE;
		} else if (this.state == State.STRIKE) {
			for (int i = 0; i < this.arcs.size(); i++) {
				Arc arc = this.arcs.get(i);
				for (int j = 0; j < arc.getAnimationLocations().size() - 1; j++) {
					Location iterLoc = ((AnimationLocation)arc.getAnimationLocations().get(j)).getLocation().clone();
					Location dest = ((AnimationLocation)arc.getAnimationLocations().get(j + 1)).getLocation().clone();
					if (this.selfHitClose && this.player.getLocation().distanceSquared(iterLoc) < 9.0D && !isTransparentForLightning(this.player, iterLoc.getBlock()) && !this.affectedEntities.contains(this.player)) {
						this.affectedEntities.add(this.player);
						electrocute((LivingEntity)this.player);
					}
					while (iterLoc.distanceSquared(dest) > 0.0225D) {
						BukkitRunnable task = new LightningParticle(arc, iterLoc.clone(), this.selfHitWater, this.waterArcs);
						double timer = (((AnimationLocation)arc.getAnimationLocations().get(j)).getAnimCounter() / 2);
						task.runTaskTimer((Plugin)ProjectKorra.plugin, (long)timer, 1L);
						this.tasks.add(task);
						iterLoc.add(GeneralMethods.getDirection(iterLoc, dest).normalize().multiply(0.15D));
					}
				}
				this.arcs.remove(i);
				i--;
			}
			if (this.tasks.size() == 0) {
				remove();
				return;
			}
		}
	}

	private void thunderingcharge(int points, float size, int speed) {
		for (int i = 0; i < speed; ++i) {
			currPoint += 360 / points;

			if (currPoint > 360) {
				currPoint = 0;
			}

			double angle = currPoint * 3.141592653589793D / 180.0D;
			double x = size * Math.cos(angle);
			double z = size * Math.sin(angle);

			Location loc = player.getLocation().add(x, 1.0D, z);
			ParticleEffect.END_ROD.display(loc, 3, 0, 0, 0, 0.04);
			ParticleEffect.CRIT_MAGIC.display(loc, 4, 0.3, 0.3, 0.3, 0.01);

		}
	}

	public void thundergrid() {
		Location location = this.player.getEyeLocation().add(this.player.getEyeLocation().getDirection().normalize().multiply(1.2D));
		location.add(0.0D, 0.3D, 0.0D);
		for (int x = 0; x < this.circles; x++) {
			for (int i = 0; i < this.helixes; i++) {
				double angle = this.step * this.radials + 6.283185307179586D * i / this.helixes;
				Vector v = new Vector(Math.cos(angle) * this.radius, (this.step * this.grow),
						Math.sin(angle) * this.radius);
				rotateAroundAxisX(v, ((location.getPitch() + 90.0F) * 0.017453292F));
				rotateAroundAxisY(v, (-location.getYaw() * 0.017453292F));
				location.add(v);
				ParticleEffect.CRIT_MAGIC.display(location, 1, 0.05, 0.05, 0.05, 0);
				location.subtract(v);
			}
			this.step++;
		}
	}

	public static final Vector rotateAroundAxisY(Vector v, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double x = v.getX() * cos + v.getZ() * sin;
		double z = v.getX() * -sin + v.getZ() * cos;
		return v.setX(x).setZ(z);
	}

	public static final Vector rotateAroundAxisX(Vector v, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double y = v.getY() * cos - v.getZ() * sin;
		double z = v.getY() * sin + v.getZ() * cos;
		return v.setY(y).setZ(z);
	}

	public void removeWithTasks() {
		for (int i = 0; i < this.tasks.size(); i++) {
			((BukkitRunnable)this.tasks.get(i)).cancel();
			i--;
		}
		remove();
	}

	public class AnimationLocation {
		private Location location;

		private int animationCounter;

		public AnimationLocation(Location loc, int animationCounter) {
			this.location = loc;
			this.animationCounter = animationCounter;
		}

		public int getAnimCounter() {
			return this.animationCounter;
		}

		public Location getLocation() {
			return this.location;
		}

		public void setAnimationCounter(int animationCounter) {
			this.animationCounter = animationCounter;
		}

		public void setLocation(Location location) {
			this.location = location;
		}
	}

	public class Arc {
		private int animationCounter;

		private Vector direction;

		private final ArrayList<Location> points;

		private final ArrayList<Lightning.AnimationLocation> animationLocations;

		private final ArrayList<Lightning.LightningParticle> particles;

		private final ArrayList<Arc> subArcs;

		public Arc(Location startPoint, Location endPoint) {
			this.points = new ArrayList<>();
			this.points.add(startPoint.clone());
			this.points.add(endPoint.clone());
			this.direction = GeneralMethods.getDirection(startPoint, endPoint);
			this.particles = new ArrayList<>();
			this.subArcs = new ArrayList<>();
			this.animationLocations = new ArrayList<>();
			this.animationCounter = 0;
		}

		public void cancel() {
			for (int i = 0; i < this.particles.size(); i++)
				((Lightning.LightningParticle)this.particles.get(i)).cancel();
			for (Arc subArc : this.subArcs)
				subArc.cancel();
		}

		public ArrayList<Arc> generateArcs(double chance, double range, double maxArcAngle) {
			ArrayList<Arc> arcs = new ArrayList<>();
			for (int i = 0; i < this.animationLocations.size(); i++) {
				if (Math.random() < chance) {
					Location loc = ((Lightning.AnimationLocation)this.animationLocations.get(i)).getLocation();
					double angle = (Math.random() - 0.5D) * maxArcAngle * 2.0D;
					Vector dir = GeneralMethods.rotateXZ(this.direction.clone(), angle);
					double randRange = Math.random() * range + range / 3.0D;
					Location loc2 = loc.clone().add(dir.normalize().multiply(randRange));
					Arc arc = new Arc(loc, loc2);
					this.subArcs.add(arc);
					arc.setAnimationCounter(((Lightning.AnimationLocation)this.animationLocations.get(i)).getAnimCounter());
					arc.generatePoints(5);
					arcs.add(arc);
					arcs.addAll(arc.generateArcs(chance / 2.0D, range / 2.0D, maxArcAngle));
				}
			}
			return arcs;
		}

		public void generatePoints(int times) {
			int i;
			for (i = 0; i < times; i++) {
				for (int j = 0; j < this.points.size() - 1; j += 2) {
					Location loc1 = this.points.get(j);
					Location loc2 = this.points.get(j + 1);
					double adjac = 0.0D;
					if (loc1.getWorld().equals(loc2.getWorld()))
						adjac = loc1.distance(loc2) / 2.0D;
					double angle = (Math.random() - 0.5D) * Lightning.this.maxArcAngle;
					angle += (angle >= 0.0D) ? 10.0D : -10.0D;
					double radians = Math.toRadians(angle);
					double hypot = adjac / Math.cos(radians);
					Vector dir = GeneralMethods.rotateXZ(this.direction.clone(), angle);
					Location newLoc = loc1.clone().add(dir.normalize().multiply(hypot));
					newLoc.add(0.0D, (Math.random() - 0.5D) / 2.0D, 0.0D);
					this.points.add(j + 1, newLoc);
				}
			}
			for (i = 0; i < this.points.size(); i++) {
				this.animationLocations.add(new Lightning.AnimationLocation(this.points.get(i), this.animationCounter));
				this.animationCounter++;
			}
		}

		public int getAnimationCounter() {
			return this.animationCounter;
		}

		public void setAnimationCounter(int animationCounter) {
			this.animationCounter = animationCounter;
		}

		public Vector getDirection() {
			return this.direction;
		}

		public void setDirection(Vector direction) {
			this.direction = direction;
		}

		public ArrayList<Location> getPoints() {
			return this.points;
		}

		public ArrayList<Lightning.AnimationLocation> getAnimationLocations() {
			return this.animationLocations;
		}

		public ArrayList<Lightning.LightningParticle> getParticles() {
			return this.particles;
		}

		public ArrayList<Arc> getSubArcs() {
			return this.subArcs;
		}
	}

	public class LightningParticle extends BukkitRunnable {
		private boolean selfHitWater;

		private int count = 0;

		private int waterArcs;

		private Lightning.Arc arc;

		private Location location;

		private Vector direction;

		public LightningParticle(Lightning.Arc arc, Location location, boolean selfHitWater, int waterArcs) {
			this.arc = arc;
			this.location = location;
			this.selfHitWater = selfHitWater;
			this.waterArcs = waterArcs;
			arc.particles.add(this);
		}

		public void cancel() {
			super.cancel();
			Lightning.this.tasks.remove(this);
		}

		public void run() {

			ParticleEffect.END_ROD.display(this.location, 1, 0, 0, 0, 0.005);
			ParticleEffect.CRIT_MAGIC.display(this.location, 1, 0, 0, 0);

			this.count++;
			if (this.count > 5) {
				cancel();
			} else if (this.count == 1) {
				if (ThreadLocalRandom.current().nextDouble() < .1) {
					playLightningbendingSound(location);
					location.getWorld().playSound(location, Sound.BLOCK_BEEHIVE_WORK, 1, 0);
				}
				if (!Lightning.this.isTransparentForLightning(Lightning.this.player, this.location.getBlock())) {
					this.arc.cancel();
					ParticleEffect.EXPLOSION_LARGE.display(this.location.getBlock().getLocation(), 20, 2.0D, 2.0D, 2.0D);
					ParticleEffect.FLAME.display(this.location.getBlock().getLocation(), 30, 1.0D, 1.0D, 1.0D, 0.14000000059604645D);
					ParticleEffect.LAVA.display(this.location.getBlock().getLocation(), 15, 1.0D, 1.0D, 1.0D, 0.14000000059604645D);
					this.location.getBlock().getWorld().playSound(this.location.getBlock().getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 10.0F, 1.0F);
					this.location.getBlock().getWorld().playSound(this.location.getBlock().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 10.0F, 0.0F);
					this.location.getBlock().getWorld().playSound(this.location.getBlock().getLocation(), Sound.ENTITY_CREEPER_HURT, 10.0F, 0.0F);
				}

				Block block = this.location.getBlock();
				Lightning.this.locations.add(block.getLocation());
				if (!Lightning.this.hitWater && (ElementalAbility.isWater(block) || (Lightning.this.arcOnIce && ElementalAbility.isIce(block)))) {
					Lightning.this.hitWater = true;
					if (ElementalAbility.isIce(block))
						Lightning.this.hitIce = true;
					for (int i = 0; i < this.waterArcs; i++) {
						Location origin = this.location.clone();
						origin.add(new Vector((Math.random() - 0.5D) * 2.0D, 0.0D, (Math.random() - 0.5D) * 2.0D));
						Lightning.this.destination = origin.clone().add(new Vector((Math.random() - 0.5D) * Lightning.this.waterArcRange, Math.random() - 0.7D, (Math.random() - 0.5D) * Lightning.this.waterArcRange));
						Lightning.Arc newArc = new Lightning.Arc(origin, Lightning.this.destination);
						newArc.generatePoints(5);
						Lightning.this.arcs.add(newArc);
					}
				}
				for (Entity entity : GeneralMethods.getEntitiesAroundPoint(this.location, 2.5D)) {
					if (entity.equals(Lightning.this.player) && (!this.selfHitWater || !Lightning.this.hitWater || !ElementalAbility.isWater(Lightning.this.player.getLocation().getBlock())) && (!this.selfHitWater || !Lightning.this.hitIce))
						continue;
					if (entity instanceof LivingEntity && !Lightning.this.affectedEntities.contains(entity)) {
						Lightning.this.affectedEntities.add(entity);
						LivingEntity lent = (LivingEntity)entity;
						if (lent instanceof Player) {
							FireAbility.playLightningbendingSound(lent.getLocation());
							FireAbility.playLightningbendingSound(Lightning.this.player.getLocation());
							lent.getWorld().playSound(lent.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 5.0F, 1.35F);
							lent.getWorld().playSound(lent.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 5.0F, 0.65F);
							lent.getWorld().playSound(lent.getLocation(), Sound.ENTITY_CREEPER_HURT, 5.0F, 0F);
							Lightning.this.player.getWorld().playSound(Lightning.this.player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 5.0F, 1.35F);
							Lightning.this.player.getWorld().playSound(Lightning.this.player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 5.0F, 0.65F);
							Lightning.this.player.getWorld().playSound(Lightning.this.player.getLocation(), Sound.ENTITY_CREEPER_HURT, 5.0F, 0.0F);
							ParticleEffect.FLASH.display(lent.getLocation(), 2, 1.0D, 1.0D, 1.0D);
							ParticleEffect.CRIT_MAGIC.display(lent.getLocation(), 10, 1.0D, 1.0D, 1.0D, 0.11999999731779099D);
							Player p = (Player)lent;
							Lightning light = (Lightning)CoreAbility.getAbility(p, Lightning.class);
							if (light != null && light.state == Lightning.State.START) {
								light.charged = true;
								Lightning.this.remove();
								return;
							}
						}
						Lightning.this.electrocute(lent);
						if (Lightning.this.maxChainArcs >= 1.0D && Math.random() <= Lightning.this.chainArcChance) {
							Lightning.this.maxChainArcs--;
							for (Entity ent : GeneralMethods.getEntitiesAroundPoint(lent.getLocation(), Lightning.this.chainRange)) {
								if (!ent.equals(Lightning.this.player) && !ent.equals(lent) && ent instanceof LivingEntity && !Lightning.this.affectedEntities.contains(ent)) {
									Lightning.this.origin = lent.getLocation().add(0.0D, 1.0D, 0.0D);
									Lightning.this.destination = ent.getLocation().add(0.0D, 1.0D, 0.0D);
									Lightning.Arc newArc = new Lightning.Arc(Lightning.this.origin, Lightning.this.destination);
									newArc.generatePoints(5);
									Lightning.this.arcs.add(newArc);
									cancel();
									return;
								}
							}
						}
					}
				}
			}
		}

		public boolean isSelfHitWater() {
			return this.selfHitWater;
		}

		public void setSelfHitWater(boolean selfHitWater) {
			this.selfHitWater = selfHitWater;
		}

		public int getCount() {
			return this.count;
		}

		public void setCount(int count) {
			this.count = count;
		}

		public int getWaterArcs() {
			return this.waterArcs;
		}

		public void setWaterArcs(int waterArcs) {
			this.waterArcs = waterArcs;
		}

		public Lightning.Arc getArc() {
			return this.arc;
		}

		public void setArc(Lightning.Arc arc) {
			this.arc = arc;
		}

		public Location getLocation() {
			return this.location;
		}

		public void setLocation(Location location) {
			this.location = location;
		}
	}

	public String getName() {
		return "Lightning";
	}

	public Location getLocation() {
		return this.origin;
	}

	public long getCooldown() {
		return this.cooldown;
	}

	public boolean isSneakAbility() {
		return true;
	}

	public boolean isHarmlessAbility() {
		return false;
	}

	public boolean isCollidable() {
		return (this.arcs.size() > 0);
	}

	public List<Location> getLocations() {
		return this.locations;
	}

	public boolean isCharged() {
		return this.charged;
	}

	public void setCharged(boolean charged) {
		this.charged = charged;
	}

	public boolean isHitWater() {
		return this.hitWater;
	}

	public void setHitWater(boolean hitWater) {
		this.hitWater = hitWater;
	}

	public boolean isHitIce() {
		return this.hitIce;
	}

	public void setHitIce(boolean hitIce) {
		this.hitIce = hitIce;
	}

	public boolean isSelfHitWater() {
		return this.selfHitWater;
	}

	public void setSelfHitWater(boolean selfHitWater) {
		this.selfHitWater = selfHitWater;
	}

	public boolean isSelfHitClose() {
		return this.selfHitClose;
	}

	public void setSelfHitClose(boolean selfHitClose) {
		this.selfHitClose = selfHitClose;
	}

	public boolean isArcOnIce() {
		return this.arcOnIce;
	}

	public void setArcOnIce(boolean arcOnIce) {
		this.arcOnIce = arcOnIce;
	}

	public int getWaterArcs() {
		return this.waterArcs;
	}

	public void setWaterArcs(int waterArcs) {
		this.waterArcs = waterArcs;
	}

	public double getRange() {
		return this.range;
	}

	public void setRange(double range) {
		this.range = range;
	}

	public double getChargeTime() {
		return this.chargeTime;
	}

	public void setChargeTime(double chargeTime) {
		this.chargeTime = chargeTime;
	}

	public double getSubArcChance() {
		return this.subArcChance;
	}

	public void setSubArcChance(double subArcChance) {
		this.subArcChance = subArcChance;
	}

	public double getDamage() {
		return this.damage;
	}

	public void setDamage(double damage) {
		this.damage = damage;
	}

	public double getMaxChainArcs() {
		return this.maxChainArcs;
	}

	public void setMaxChainArcs(double maxChainArcs) {
		this.maxChainArcs = maxChainArcs;
	}

	public double getChainRange() {
		return this.chainRange;
	}

	public void setChainRange(double chainRange) {
		this.chainRange = chainRange;
	}

	public double getWaterArcRange() {
		return this.waterArcRange;
	}

	public void setWaterArcRange(double waterArcRange) {
		this.waterArcRange = waterArcRange;
	}

	public double getChainArcChance() {
		return this.chainArcChance;
	}

	public void setChainArcChance(double chainArcChance) {
		this.chainArcChance = chainArcChance;
	}

	public double getStunChance() {
		return this.stunChance;
	}

	public void setStunChance(double stunChance) {
		this.stunChance = stunChance;
	}

	public double getStunDuration() {
		return this.stunDuration;
	}

	public void setStunDuration(double stunDuration) {
		this.stunDuration = stunDuration;
	}

	public double getMaxArcAngle() {
		return this.maxArcAngle;
	}

	public void setMaxArcAngle(double maxArcAngle) {
		this.maxArcAngle = maxArcAngle;
	}

	public double getParticleRotation() {
		return this.particleRotation;
	}

	public void setParticleRotation(double particleRotation) {
		this.particleRotation = particleRotation;
	}

	public long getTime() {
		return this.time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public State getState() {
		return this.state;
	}

	public void setState(State state) {
		this.state = state;
	}

	public Location getOrigin() {
		return this.origin;
	}

	public void setOrigin(Location origin) {
		this.origin = origin;
	}

	public Location getDestination() {
		return this.destination;
	}

	public void setDestination(Location destination) {
		this.destination = destination;
	}

	public static int getPointGeneration() {
		return 5;
	}

	public ArrayList<Entity> getAffectedEntities() {
		return this.affectedEntities;
	}

	public ArrayList<Arc> getArcs() {
		return this.arcs;
	}

	public ArrayList<BukkitRunnable> getTasks() {
		return this.tasks;
	}

	public void setCooldown(long cooldown) {
		this.cooldown = cooldown;
	}
}