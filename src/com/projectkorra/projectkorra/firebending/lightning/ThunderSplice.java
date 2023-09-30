//package com.projectkorra.projectkorra.firebending.lightning;
//
//import java.util.Random;
//import java.util.concurrent.ConcurrentHashMap;
//
//import org.bukkit.Location;
//import org.bukkit.Sound;
//import org.bukkit.entity.Entity;
//import org.bukkit.entity.Player;
//import org.bukkit.util.Vector;
//
//import com.projectkorra.projectkorra.Element;
//import com.projectkorra.projectkorra.GeneralMethods;
//import com.projectkorra.projectkorra.ability.Ability;
//import com.projectkorra.projectkorra.ability.CoreAbility;
//import com.projectkorra.projectkorra.ability.LightningAbility;
//import com.projectkorra.projectkorra.util.ActionBar;
//import com.projectkorra.projectkorra.util.DamageHandler;
//import com.projectkorra.projectkorra.util.ParticleEffect;
//
//public class ThunderSplice extends LightningAbility {
//
//	private double range;
//	private double damage;
//	private double angleTheta;
//	private double anglePhi;
//	private double rotation;
//	private double angle;
//	private double particleRotation;
//
//	private int cooldown;
//	private long chargeTime;
//	private boolean hasReached = true;
//	private boolean launched;
//	private boolean charged;
//	private int id;
//	private static int ID = Integer.MIN_VALUE;
//	private ConcurrentHashMap<Integer, Bolt> bolts = new ConcurrentHashMap<>();
//	private boolean created;
//	private Location loc;
//	private Vector direction;
//	private Location origin;
//	private Location location;
//	private int counter;
//
//	Random rand = new Random();
//
//	private int knockback;
//	private int currPoint;
//
//	public ThunderSplice(Player player) {
//		super(player);
//		if (!this.bPlayer.canBend(this))
//			return;
//		setFields();
//		start();
//	}
//
//	private void setFields() {
//		this.loc = this.player.getLocation().add(0.0D, -1.0D, 0.0D).clone();
//		this.cooldown = getConfig().getInt("Abilities.Fire.Lightning.ThunderSplice.Cooldown");
//		this.chargeTime = getConfig().getInt("Abilities.Fire.Lightning.ThunderSplice.ChargeTime");
//		this.range = getConfig().getDouble("Abilities.Fire.Lightning.ThunderSplice.Range");
//		this.damage = getConfig().getDouble("Abilities.Fire.Lightning.ThunderSplice.Damage");
//		this.knockback = 0;
//		this.angleTheta = getConfig().getDouble("Abilities.Fire.Lightning.ThunderSplice.AngleTheta");
//		this.anglePhi = getConfig().getLong("Abilities.Fire.Lightning.ThunderSplice.AnglePhi");
//		this.rotation = 0.0D;
//		this.counter = 0;
//		this.created = false;
//	}
//
//	public void progress() {
//		if (!this.bPlayer.canBend(this) && this.bPlayer.canBendIgnoreBinds(this) && !this.bPlayer.canBendIgnoreCooldowns(this)) {
//			remove();
//			return;
//		}
//		if (this.player.isDead() || !this.player.isOnline()) {
//			remove();
//			this.bPlayer.addCooldown(this);
//			return;
//		}
//		if (System.currentTimeMillis() > getStartTime() + this.chargeTime)
//			this.charged = true;
//        if (this.player.isSneaking() && !this.launched) {
//			charge();
//			displayFireStar();
//		} else {
//			if (!this.charged) {
//				remove();
//				return;
//			}
//			if (!this.launched) {
//				this.bPlayer.addCooldown(this);
//				this.launched = true;
//			}
//			if (GeneralMethods.isSolid(this.location.getBlock())) {
//				remove();
//				this.bPlayer.addCooldown(this);
//				return;
//			}
//			if (!this.created) {
//				createCone();
//				this.created = true;
//			} else {
//				if (this.bolts.isEmpty()) {
//					remove();
//					return;
//				}
//				progressBolts();
//			}
//		}
//	}
//
//	public void charge() {
//		if (this.charged) {
//			Location loc = GeneralMethods.getTargetedLocation(this.player, 2);
//			spawnHelix(loc, 1.5D, 1.0D, this.angle);
//			spawnHelix(loc, 1.5D, -1.0D, this.angle + 180.0D);
//			loc.getWorld().playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 1.0F, 0.0F);
//			this.angle -= 3.0D;
//		}
//	}
//
//	public void displayFireStar() {
//		if (this.hasReached) {
//			this.location = GeneralMethods.getTargetedLocation(this.player, 2);
//			this.origin = GeneralMethods.getTargetedLocation(this.player, 2);
//			grid();
//		}
//	}
//
//	public void spawnHelix(Location origin, double radius, double height, double angle) {
//		int particles = 3;
//		for (int i = 1; i < particles; i++) {
//			double y = height / particles * i;
//			double a = (180D / particles * i) + angle;
//			double x = radius * Math.cos(Math.toRadians(a));
//			double z = radius * Math.sin(Math.toRadians(a));
//			Location p = origin.clone().add(x, y, z);
//			playLightningbendingParticle(p);
//		}
//	}
//
//	private void grid() {
//		Location localLocation1 = this.player.getLocation();
//		double d1 = 0.1570796326794897D;
//		double d2 = 0.06283185307179587D;
//		double d3 = 1.0D;
//		double d4 = 1.0D;
//		double d5 = 0.1570796326794897D * this.particleRotation;
//		double d6 = 0.06283185307179587D * this.particleRotation;
//		double d7 = localLocation1.getX() + Math.cos(d5);
//		double d8 = localLocation1.getZ() + Math.sin(d5);
//		double newY = localLocation1.getY() + 1.3D + 1.3D * Math.cos(d6);
//		Location localLocation2 = new Location(this.player.getWorld(), d7, newY, d8);
//		playLightningbendingParticle(localLocation2, 0.0D, 0.0D, 0.0D);
//		playLightningbendingParticle(localLocation2, 0.15D, 0.15D, 0.15D);
//		LightningRings(60, 2.25F, 3);
//		LightningRings(60, 2.5F, 3);
//		LightningRings(60, 3.0F, 3);
//		localLocation2.getWorld().playSound(localLocation1, Sound.ENTITY_CREEPER_HURT, 1.0F, 0.0F);
//		localLocation2.getWorld().playSound(localLocation2, Sound.BLOCK_BEEHIVE_WORK, 1.0F, 0.0F);
//		double xd7 = localLocation1.getX() + -Math.cos(d5);
//		double xd8 = localLocation1.getZ() + -Math.sin(d5);
//		double xnewY = localLocation1.getY() + 1.3D + 1.3D * Math.cos(d6);
//		Location localLocation3 = new Location(this.player.getWorld(), xd7, xnewY, xd8);
//		playLightningbendingParticle(localLocation3, 0.0D, 0.0D, 0.0D);
//		playLightningbendingParticle(localLocation3, 0.15D, 0.15D, 0.15D);
//		this.particleRotation++;
//		this.loc = this.player.getLocation().add(0.0D, -1.0D, 0.0D).clone();
//		ActionBar.sendActionBar(Element.LIGHTNING.getColor() + "* Charging *", new Player[] { this.player });
//	}
//
//	private void LightningRings(int points, float size, int speed) {
//		for (int i = 0; i < speed; i++) {
//			this.currPoint += 360 / points;
//			if (this.currPoint > 360)
//				this.currPoint = 0;
//			double angle = this.currPoint * Math.PI / 180.0D;
//			double x = size * Math.cos(angle);
//			double z = size * Math.sin(angle);
//			Location loc = this.player.getLocation().add(x, 0.75D, z);
//			playLightningbendingParticle(loc, 0.0D, 0.0D, 0.0D);
//		}
//	}
//	private void createCone() {
//		final Location loc = this.player.getEyeLocation().add(this.player.getEyeLocation().getDirection().multiply(1));
//		final Vector vector = loc.getDirection();
//
//		final double angle = Math.toRadians(30);
//		double x, y, z;
//		final double r = 1;
//
//		for (double theta = 0; theta <= 180; theta += this.angleTheta) {
//			final double dPhi = this.anglePhi / Math.sin(Math.toRadians(theta));
//			for (double phi = 0; phi < 360; phi += dPhi) {
//				final double rPhi = Math.toRadians(phi);
//				final double rTheta = Math.toRadians(theta);
//
//				x = r * Math.cos(rPhi) * Math.sin(rTheta);
//				y = r * Math.sin(rPhi) * Math.sin(rTheta);
//				z = r * Math.cos(rTheta);
//				final Vector direction = new Vector(x, z, y);
//				Location temp = loc.clone().setDirection(direction);
//
//				if (direction.angle(vector) <= angle) {
//					spawnBolt(temp, this.range, 1, 20);
//				}
//			}
//		}
//	}
//
//	private void spawnBolt(Location location, double max, double gap, int arc) {
//		this.id = ID;
//		this.bolts.put(Integer.valueOf(this.id), new Bolt(this, location, this.id, max, gap, arc));
//		if (ID == Integer.MAX_VALUE)
//			ID = Integer.MIN_VALUE;
//		ID++;
//	}
//
//	private void progressBolts() {
//        for (int id : this.bolts.keySet()) {
//            this.bolts.get(id).progressBolt();
//        }
//	}
//
//	public long getCooldown() {
//		return this.cooldown;
//	}
//
//	public boolean isEnabled() {
//		return getConfig().getBoolean("Abilities.Fire.Lightning.ThunderSplice.Enabled");
//	}
//
//	public Location getLocation() {
//		return this.location;
//	}
//
//	public Location getOrigin() {
//		return this.origin;
//	}
//
//	public String getName() {
//		return "ThunderSplice";
//	}
//
//	public boolean isHarmlessAbility() {
//		return false;
//	}
//
//	public boolean isIgniteAbility() {
//		return false;
//	}
//
//	public boolean isExplosiveAbility() {
//		return false;
//	}
//
//	public boolean isSneakAbility() {
//		return true;
//	}
//
//	public String getDescription() {
//		return "ThunderSplice is an advanced Lightningbending ability. It allows you to create a huge outburst of multiple bolts of lightning, in creasing in size the further it goes! Use this ability to truly electrocute your enemies to death!";
//	}
//
//	public String getInstructions() {
//		return "- Hold-Shift > Release when it is charged! -";
//	}
//
//	public class Bolt {
//		private ThunderSplice ability;
//
//		private Location location;
//
//		private float initYaw;
//		private float initPitch;
//
//		private double step;
//		private double max;
//		private double gap;
//
//		private int id;
//		private int arc;
//		private int greenHex;
//		private int blueHex;
//
//		public Bolt(ThunderSplice ability, Location location, int id, double max, double gap, int arc) {
//			this.ability = ability;
//			this.location = location;
//			this.id = id;
//			this.max = max;
//			this.gap = gap;
//			this.arc = arc;
//			this.initPitch = this.location.getPitch();
//			this.initYaw = this.location.getYaw();
//			this.greenHex = 0;
//			this.blueHex = 255;
//		}
//
//		public void progressBolt() {
//			if (this.step >= this.max) {
//				ThunderSplice.this.bolts.remove(Integer.valueOf(this.id));
//				return;
//			}
//			double step = 0.5D;
//			for (double i = 0.0D; i < this.gap; i += step) {
//				this.step += step;
//				this.location = this.location.add(this.location.getDirection().clone().multiply(step));
//				if (GeneralMethods.isSolid(this.location.getBlock())) {
//					ParticleEffect.EXPLOSION_LARGE.display(this.location.getBlock().getLocation(), 3, 0.2D, 0.2D, 0.2D);
//					ThunderSplice.this.bolts.remove(Integer.valueOf(this.id));
//				}
//				playLightningbendingParticle(this.location, 0.0D, 0.0D, 0.0D);
//				for (Entity entity : GeneralMethods.getEntitiesAroundPoint(this.location, 1.2D)) {
//					if (entity instanceof org.bukkit.entity.LivingEntity && !entity.equals(ThunderSplice.this.player)) {
//						DamageHandler.damageEntity(entity, ThunderSplice.this.player, ThunderSplice.this.damage, (Ability)this.ability);
//						GeneralMethods.setVelocity((Ability)this.ability, entity, this.location.getDirection().normalize().multiply(ThunderSplice.this.knockback));
//					}
//				}
//                switch (ThunderSplice.this.rand.nextInt(3)) {
//                    case 0 -> this.location.setYaw(this.initYaw - this.arc);
//                    case 1 -> this.location.setYaw(this.initYaw + this.arc);
//                    default -> this.location.setYaw(this.initYaw);
//                }
//                switch (ThunderSplice.this.rand.nextInt(3)) {
//                    case 0 -> this.location.setPitch(this.initPitch - this.arc);
//                    case 1 -> this.location.setPitch(this.initPitch + this.arc);
//                    default -> this.location.setPitch(this.initPitch);
//                }
//				if (ThunderSplice.this.rand.nextInt(50) == 0) {
//					this.location.getWorld().playSound(this.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0F, 1.25F);
//					this.location.getWorld().playSound(this.location, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.65F);
//				}
//			}
//		}
//	}
//}