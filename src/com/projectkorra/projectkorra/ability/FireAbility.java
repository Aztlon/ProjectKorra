package com.projectkorra.projectkorra.ability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Fire;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.util.logging.PkLang;

import me.clip.placeholderapi.PlaceholderAPI;

public abstract class FireAbility extends ElementalAbility {

	private static final Map<Block, LivingEntity> SOURCE_CASTERS = new ConcurrentHashMap<>();
	private static final Set<BlockFace> IGNITE_FACES = new HashSet<>(Arrays.asList(BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.UP));

	public FireAbility(final LivingEntity caster) {
		super(caster);
	}

	@Override
	public boolean isIgniteAbility() {
		return true;
	}

	@Override
	public boolean isExplosiveAbility() {
		return false;
	}

	@Override
	public Element getElement() {
		return Element.FIRE;
	}

	@Override
	public void handleCollision(final Collision collision) {
		super.handleCollision(collision);
		if (collision.isRemovingFirst()) {
			ParticleEffect.BLOCK_CRACK.display(collision.getLocationFirst(), 10, 1, 1, 1, 0.1, getFireType().createBlockData());
		}
	}
	/**
	 *
	 * @return Material based on whether the player is a Blue Firebender, SOUL_FIRE if true, FIRE if false.
	 */
	public Material getFireType() {
		return bender.canUseSubElement(SubElement.BLUE_FIRE) ? Material.SOUL_FIRE : Material.FIRE;
	}

	/**
	 * Gets the fire particles the player is permitted to use
	 * @return ParticleEffect of the fire the player can use
	 */
	public ParticleEffect getFirebendingParticles() {
		if (this.bender.canUseSubElement(SubElement.BLUE_FIRE)) {
			return ParticleEffect.SOUL_FIRE_FLAME;
		} else {
			return ParticleEffect.FLAME;
		}
	}

	/**
	 * Returns if fire is allowed to completely replace blocks or if it should
	 * place a temp fire block.
	 */
	public static boolean canFireGrief() {
		return getConfig().getBoolean("Properties.Fire.FireGriefing");
	}

	/**
	 * Creates a fire block meant to replace other blocks but reverts when the
	 * fire dissipates or is destroyed.
	 */
	public void createTempFire(final Location loc) {
		createTempFire(loc, getConfig().getLong("Properties.Fire.RevertTicks") + (long) ((new Random()).nextDouble() * getConfig().getLong("Properties.Fire.RevertTicks")));
	}

	public void createTempFire(final Location loc, final long time) {
		if (isIgnitable(loc.getBlock())) {
			new TempBlock(loc.getBlock(), createFireState(loc.getBlock(), blockType(caster)), time);
			SOURCE_CASTERS.put(loc.getBlock(), this.caster);
		}
	}

	public double getDayFactor(final double value) {
		return (this.caster != null ? value * getDayFactor(caster.getWorld()) : value);
	}

	public static double getDayFactor() {
		return getConfig().getDouble("Properties.Fire.DayFactor");
	}

	/**
	 * Gets the firebending dayfactor from the config multiplied by a specific
	 * value if it is day.
	 *
	 * @param value The value
	 * @param world The world to pass into {@link #isDay(World)}
	 * @return value DayFactor multiplied by specified value when
	 *         {@link #isDay(World)} is true <br />
	 *         else <br />
	 *         value The specified value in the parameters
	 */
	public static double getDayFactor(final double value, final World world) {
		if (isDay(world)) {
			return value * getDayFactor();
		}
		return value;
	}

	public static double getDayFactor(final World world) {
		return getDayFactor(1, world);
	}

	public static ChatColor getSubChatColor() {
		return ChatColor.valueOf(ConfigManager.getConfig().getString("Properties.Chat.Colors.FireSub"));
	}

	/**
	 * Can fire be placed in the provided block
	 * @param block The block to check
	 * @return True if fire can be placed here
	 */
	public static boolean isIgnitable(final Block block) {
		Block support = block.getRelative(BlockFace.DOWN);
		Location loc = support.getLocation();
		boolean supported = support.getBoundingBox().overlaps(loc.add(0, 0.8, 0).toVector(), loc.add(1, 1, 1).toVector());
		return (!isWater(block) && !block.isLiquid() && GeneralMethods.isTransparent(block)) && ((supported && support.getType().isSolid())
				|| (IGNITE_FACES.stream().map(face -> block.getRelative(face).getType()).anyMatch(FireAbility::isIgnitable)));
	}

	public static boolean isIgnitable(final Material material) {
		return material.isFlammable() || material.isBurnable();
	}

	public static BlockData createFireState(Block position) {
		return createFireState(position, FireParticle.FLAME.blockId);
	}

	/**
	 * Create a fire block with the correct blockstate at the given position
	 * @param position The position to test
	 * @param fireBlockType The fire type to use, a Material or ItemsAdder ID
	 * @return The fire blockstate
	 */
	public static BlockData createFireState(Block position, String fireBlockType) {
		BlockData data = GeneralMethods.blockDataFromId(fireBlockType);
		if (isIgnitable(position) && position.getRelative(BlockFace.DOWN).getType().isSolid())
			return data;

		if (fireBlockType.equals("SOUL_FIRE")) {
			if (isIgnitable(position.getRelative(BlockFace.UP))) {
				return data;
			} else {
				fireBlockType = "fire";
				data = GeneralMethods.blockDataFromId(fireBlockType);
			}
		}

		if (!(data instanceof Fire fire)) {
			PkLang.warning("FireAbility#createFireState: BlockData of type " + fireBlockType + " is not a Fire block");
			return data;
		}

		for (BlockFace face : IGNITE_FACES) {
			fire.setFace(face, false);
			if (isIgnitable(position.getRelative(face))) {
				fire.setFace(face, true);
			}
		}

		return fire;
	}

	/**
	 * This method was used for the old collision detection system. Please see
	 * {@link Collision} for the new system.
	 * <p>
	 * Checks whether a location is within a FireShield.
	 *
	 * @param loc The location to check
	 * @return true If the location is inside a FireShield.
	 */
	@Deprecated
	public static boolean isWithinFireShield(final Location loc) {
		final List<String> list = new ArrayList<>();
		list.add("FireShield");
		return GeneralMethods.blockAbilities(null, list, loc, 0);
	}

	public static void playCombustionSound(final Location loc) {
		if (getConfig().getBoolean("Properties.Fire.PlaySound")) {
			final float volume = (float) getConfig().getDouble("Properties.Fire.CombustionSound.Volume");
			final float pitch = (float) getConfig().getDouble("Properties.Fire.CombustionSound.Pitch");

			Sound sound = Sound.ENTITY_FIREWORK_ROCKET_BLAST;
			try {
				sound = Sound.valueOf(getConfig().getString("Properties.Fire.CombustionSound.Sound"));
			} catch (final IllegalArgumentException exception) {
				PkLang.warning("Your current value for 'Properties.Fire.CombustionSound.Sound' is not valid.");
			} finally {
				loc.getWorld().playSound(loc, sound, volume, pitch);
			}
		}
	}

	public void playFirebendingParticles(final Location loc, final int amount, final double xOffset, final double yOffset, final double zOffset, final double extra) {
		String particle = particleType(this.caster);
		FireParticle fireParticle = FireParticle.byName(particle);
		fireParticle.getEffect().display(loc, amount, xOffset, yOffset, zOffset, extra);
//		if (this.getBendingPlayer().canUseSubElement(SubElement.BLUE_FIRE)) {
//			ParticleEffect.SOUL_FIRE_FLAME.display(loc, amount, xOffset, yOffset, zOffset, extra);
//		} else {
//			ParticleEffect.FLAME.display(loc, amount, xOffset, yOffset, zOffset, extra);
//		}
	}

	public void playFirebendingParticles(final Location loc, final int amount, final double xOffset, final double yOffset, final double zOffset) {
		playFirebendingParticles(loc, amount, xOffset, yOffset, zOffset, 0.025);
	}

	public static String particleType(final LivingEntity caster) {
		String particle = "flame";
		if (caster instanceof Player player && player.isOnline()) {
			String placeholder = PlaceholderAPI.setPlaceholders(player, "%avatarverse_fireparticle%");
			if (!placeholder.isEmpty())
				particle = placeholder;
		}
		return particle;
	}

	public static String blockType(final LivingEntity caster) {
		String block = "fire";
		if (caster instanceof Player player && player.isOnline()) {
			String placeholder = PlaceholderAPI.setPlaceholders(player, "%avatarverse_fireblock%");
			if (!placeholder.isEmpty())
				block = placeholder;
		}
		return block;
	}

	public static void playFirebendingSound(final Location loc) {
		if (getConfig().getBoolean("Properties.Fire.PlaySound")) {
			final float volume = (float) getConfig().getDouble("Properties.Fire.FireSound.Volume");
			final float pitch = (float) getConfig().getDouble("Properties.Fire.FireSound.Pitch");

			Sound sound = Sound.BLOCK_FIRE_AMBIENT;
			try {
				sound = Sound.valueOf(getConfig().getString("Properties.Fire.FireSound.Sound"));
			} catch (final IllegalArgumentException exception) {
				PkLang.warning("Your current value for 'Properties.Fire.FireSound.Sound' is not valid.");
			} finally {
				loc.getWorld().playSound(loc, sound, volume, pitch);
			}
		}
	}

	public static void playLightningbendingParticle(final Location loc) {
		playLightningbendingParticle(loc, Math.random(), Math.random(), Math.random());
	}

	public static void playLightningbendingParticle(final Location loc, final double xOffset, final double yOffset, final double zOffset) {
		ParticleEffect.END_ROD.display(loc, 1, xOffset, yOffset, zOffset, 0);
		ParticleEffect.CRIT_MAGIC.display(loc, 1, xOffset, yOffset, zOffset, 0);
		ParticleEffect.ELECTRIC_SPARK.display(loc, 1, xOffset, yOffset, zOffset, 0);
	}

	public static void playLightningbendingSound(final Location loc) {
		if (getConfig().getBoolean("Properties.Fire.PlaySound")) {
			final float volume = (float) getConfig().getDouble("Properties.Fire.LightningSound.Volume");
			final float pitch = (float) getConfig().getDouble("Properties.Fire.LightningSound.Pitch");

			Sound sound = Sound.ENTITY_CREEPER_HURT;
			try {
				sound = Sound.valueOf(getConfig().getString("Properties.Fire.LightningSound.Sound"));
			} catch (final IllegalArgumentException exception) {
				PkLang.warning("Your current value for 'Properties.Fire.LightningSound.Sound' is not valid.");
			} finally {
				loc.getWorld().playSound(loc, sound, volume, pitch);
			}
		}
	}

	public static void playLightningbendingChargingSound(final Location loc) {
		if (getConfig().getBoolean("Properties.Fire.PlaySound")) {
			final float volume = (float) getConfig().getDouble("Properties.Fire.LightningCharge.Volume");
			final float pitch = (float) getConfig().getDouble("Properties.Fire.LightningCharge.Pitch");

			Sound sound = Sound.BLOCK_BEEHIVE_WORK;
			try {
				sound = Sound.valueOf(getConfig().getString("Properties.Fire.LightningCharge.Sound"));
			} catch (final IllegalArgumentException exception) {
				PkLang.warning("Your current value for 'Properties.Fire.LightningCharge.Sound' is not valid.");
			} finally {
				loc.getWorld().playSound(loc, sound, volume, pitch);
			}
		}
	}

	public static void playLightningbendingHitSound(final Location loc) {
		if (getConfig().getBoolean("Properties.Fire.PlaySound")) {
			final float volume = (float) getConfig().getDouble("Properties.Fire.LightningHit.Volume");
			final float pitch = (float) getConfig().getDouble("Properties.Fire.LightningHit.Pitch");

			Sound sound = Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
			try {
				sound = Sound.valueOf(getConfig().getString("Properties.Fire.LightningHit.Sound"));
			} catch (final IllegalArgumentException exception) {
				PkLang.warning("Your current value for 'Properties.Fire.LightningHit.Sound' is not valid.");
			} finally {
				loc.getWorld().playSound(loc, sound, volume, pitch);
			}
		}
	}

	/**
	 * Apply modifiers to this value. Applies the day factor to it
	 * @param value The value to modify
	 * @return The modified value
	 */
	@Override
	public double applyModifiers(double value) {
		return GeneralMethods.applyModifiers(value, getDayFactor(1.0));
	}

	/**
	 * Apply modifiers to this value. Applies the day factor to it
	 * @param value The value to modify
	 * @return The modified value
	 */
	public double applyInverseModifiers(double value) {
		return GeneralMethods.applyInverseModifiers(value, getDayFactor(1.0));
	}

	/**
	 * Apply modifiers to this value. Applies the day factor and the blue fire factor (for damage)
	 * @param value The value to modify
	 * @return The modified value
	 */
	public double applyModifiersDamage(double value) {
		double fireModifier = 1;
		CoreAbility abil = getAbility("WhiteFlames");
		if (WhiteFireAbility.getBurntOutPlayers().containsKey(caster)) {
			fireModifier = WhiteFireAbility.getDamageFactor(player, true);
		} else if (abil != null && getAbility(caster, abil.getClass()) != null) {
			fireModifier = WhiteFireAbility.getDamageFactor(player, false);
		} else if (bender.hasElement(Element.BLUE_FIRE)) {
			getConfig().getDouble("Properties.Fire.BlueFire.DamageFactor", 1.1);
		}
		return GeneralMethods.applyModifiers(value, getDayFactor(1.0), fireModifier);
	}

	/**
	 * Apply modifiers to this value. Applies the day factor and the blue fire factor (for range)
	 * @param value The value to modify
	 * @return The modified value
	 */
	public double applyModifiersRange(double value) {
		double fireModifier = 1;
		CoreAbility abil = getAbility("WhiteFlames");
		if (abil != null && getAbility(caster, abil.getClass()) != null) {
			fireModifier = WhiteFireAbility.getRangeFactor(player, false);
		} else if (WhiteFireAbility.getBurntOutPlayers().containsKey(caster)) {
			fireModifier = WhiteFireAbility.getRangeFactor(player, true);
		}  else if (bender.hasElement(Element.BLUE_FIRE)) {
			getConfig().getDouble("Properties.Fire.BlueFire.RangeFactor", 1.2);
		}
		return GeneralMethods.applyModifiers(value, getDayFactor(1.0), fireModifier);
	}

	/**
	 * Apply modifiers to this value. Applies the day factor and the blue fire factor (for cooldowns)
	 * @param value The value to modify
	 * @return The modified value
	 */
	public long applyModifiersCooldown(long value) {
		double fireModifier = 1;
		CoreAbility abil = getAbility("WhiteFlames");
		if (abil != null && getAbility(caster, abil.getClass()) != null) {
			fireModifier = WhiteFireAbility.getCooldownFactor(player, false);
		} else if (WhiteFireAbility.getBurntOutPlayers().containsKey(caster)) {
			fireModifier = WhiteFireAbility.getCooldownFactor(player, true);
		}  else if (bender.hasElement(Element.BLUE_FIRE)) {
			getConfig().getDouble("Properties.Fire.BlueFire.CooldownFactor", 0.9);
		}
		return (long) GeneralMethods.applyInverseModifiers(value, getDayFactor(1.0), 1 / fireModifier);
	}

	public long applyModifiersChargeTime(long value) {
		double fireModifier = 1;
		CoreAbility abil = getAbility("WhiteFlames");
		if (abil != null && getAbility(caster, abil.getClass()) != null) {
			fireModifier = WhiteFireAbility.getChargeFactor(player, false);
		} else if (WhiteFireAbility.getBurntOutPlayers().containsKey(caster)) {
			fireModifier = WhiteFireAbility.getChargeFactor(player, true);
		}
		return (long) GeneralMethods.applyInverseModifiers(value, getDayFactor(1.0), 1 / fireModifier);
	}

	public static void stopBending() {
		SOURCE_CASTERS.clear();
	}

	public static Map<Block, LivingEntity> getSourceCasters() {
		return SOURCE_CASTERS;
	}

	public enum FireParticle {
		FLAME(ParticleEffect.FLAME, "FIRE", Material.CANDLE, net.md_5.bungee.api.ChatColor.GOLD),
		BLUE(ParticleEffect.SOUL_FIRE_FLAME, "SOUL_FIRE", Material.BLUE_CANDLE, net.md_5.bungee.api.ChatColor.DARK_AQUA),
		WHITE(ParticleEffect.BUBBLE_POP, "customfire:white_fire", Material.WHITE_CANDLE, net.md_5.bungee.api.ChatColor.WHITE),
		PURPLE(ParticleEffect.DRAGON_BREATH, "customfire:purple_fire", Material.PURPLE_CANDLE, net.md_5.bungee.api.ChatColor.DARK_PURPLE),
		GREEN(ParticleEffect.SCRAPE, "customfire:green_fire", Material.GREEN_CANDLE, net.md_5.bungee.api.ChatColor.GREEN),
		MAGENTA(ParticleEffect.WAX_OFF, "customfire:magenta_fire", Material.MAGENTA_CANDLE, net.md_5.bungee.api.ChatColor.LIGHT_PURPLE),
		RED(ParticleEffect.WAX_ON, "customfire:red_fire", Material.RED_CANDLE, net.md_5.bungee.api.ChatColor.RED),
		BLACK(ParticleEffect.HAPPY_VILLAGER, "customfire:black_fire", Material.BLACK_CANDLE, net.md_5.bungee.api.ChatColor.DARK_GRAY);

		public static FireParticle byName(String name) {
			return switch (name.toLowerCase()) {
				case "blue" -> BLUE;
				case "white" -> WHITE;
				case "purple" -> PURPLE;
				case "green" -> GREEN;
				case "magenta" -> MAGENTA;
				case "red" -> RED;
				case "black" -> BLACK;
				default -> FLAME;
			};
		}

		final ParticleEffect effect;
		final String blockId;
		final Material icon;
		final net.md_5.bungee.api.ChatColor color;

		FireParticle(ParticleEffect effect, String blockId, Material icon, net.md_5.bungee.api.ChatColor color) {
			this.effect = effect;
			this.blockId = blockId;
			this.icon = icon;
			this.color = color;
		}

		public ParticleEffect getEffect() {
			return effect;
		}

		public String getBlockId() {
			return blockId;
		}

		public Material getIcon() {
			return icon;
		}

		public net.md_5.bungee.api.ChatColor getColor() {
			return color;
		}
	}

}