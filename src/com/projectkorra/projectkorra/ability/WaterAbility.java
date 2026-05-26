package com.projectkorra.projectkorra.ability;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.projectkorra.projectkorra.Bender;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.util.Collision;
import com.projectkorra.projectkorra.region.RegionProtection;
import com.projectkorra.projectkorra.util.BlockSource;
import com.projectkorra.projectkorra.util.ParticleEffect;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.util.logging.PkLang;
import com.projectkorra.projectkorra.waterbending.SurgeWall;
import com.projectkorra.projectkorra.waterbending.SurgeWave;
import com.projectkorra.projectkorra.waterbending.WaterSpout;
import com.projectkorra.projectkorra.waterbending.ice.PhaseChange;
import com.projectkorra.projectkorra.waterbending.multiabilities.WaterArms;
import com.projectkorra.projectkorra.waterbending.plant.PlantRegrowth;
import com.projectkorra.projectkorra.waterbending.util.IWaterAbilityTransformer;

import me.clip.placeholderapi.PlaceholderAPI;

public abstract class WaterAbility extends ElementalAbility {

	public static final Map<String, String> BLOCK_DATA_CUSTOM_ICE = new HashMap<>(); // <block data string, namespaced id>
	public static IWaterAbilityTransformer transformer;

	public WaterAbility(final LivingEntity caster) {
		super(caster);
	}

	public boolean canAutoSource() {
		return getConfig().getBoolean("Abilities." + this.getElement() + "." + this.getName() + ".CanAutoSource");
	}

	public boolean canDynamicSource() {
		return getConfig().getBoolean("Abilities." + this.getElement() + "." + this.getName() + ".CanDynamicSource");
	}

	public int getCarriedWaterCost() {
		return 1;
	}

	public int getDeterministicReturnAmount(final int consumedAmount) {
		return consumedAmount;
	}

	@Override
	public Element getElement() {
		return Element.WATER;
	}

	public @Nullable Block getIceSourceBlock(final double range) {
		return getIceSourceBlock(this.caster, range);
	}

	public @Nullable Block getPlantSourceBlock(final double range) {
		return this.getPlantSourceBlock(range, false, true);
	}

	public @Nullable Block getPlantSourceBlock(final double range, final boolean onlyLeaves, final boolean allowDecayBlocks) {
		return getPlantSourceBlock(this.caster, range, onlyLeaves, allowDecayBlocks);
	}

	@Override
	public boolean isExplosiveAbility() {
		return false;
	}

	@Override
	public boolean isIgniteAbility() {
		return false;
	}

	@Override
	public void handleCollision(final Collision collision) {
		super.handleCollision(collision);
		if (collision.isRemovingFirst()) {
			ParticleEffect.BLOCK_CRACK.display(collision.getLocationFirst(), 10, 1, 1, 1, 0.1, collision.getLocationFirst().getBlock().getBlockData());
		}
	}

	public double getNightFactor(final double value) {
		return this.caster != null ? value * getNightFactor(caster.getWorld()) : 1;
	}

	public static boolean isBendableWaterTempBlock(final Block block) {
		return Optional.ofNullable(TempBlock.get(block)).map(WaterAbility::isBendableWaterTempBlock).orElse(false);
	}

	public static boolean isBendableWaterTempBlock(final TempBlock tempBlock) {
		return tempBlock.isBendableSource();
//		return WATERBENDABLE_TEMPBLOCKS.contains(tempBlock)
//				|| PhaseChange.getFrozenBlocksMap().containsKey(tempBlock)
//				|| HeatControl.getMeltedBlocks().contains(tempBlock)
//				|| SurgeWall.SOURCE_BLOCKS.contains(tempBlock)
//				|| Torrent.getFrozenBlocks().containsKey(tempBlock);
	}

	public boolean isIcebendable(final Block block) {
		if (this.isIcebendable(block.getType())) return true;
		if (block.getType().name().endsWith("STAINED_GLASS") && TempBlock.isTempBlock(block)) return true;
		return block.getType() == Material.NOTE_BLOCK && BLOCK_DATA_CUSTOM_ICE.containsKey(block.getBlockData().getAsString());
	}

	public boolean isIcebendable(final Material material) {
		return this.isIcebendable(this.caster, material);
	}

	public boolean isIcebendable(final LivingEntity caster, final Material material) {
		return isIcebendable(caster, material, false);
	}

	public boolean isPlantbendable(final Block block) {
		return this.isPlantbendable(block.getType());
	}

	public boolean isPlantbendable(final Material material) {
		return this.isPlantbendable(this.caster, material);
	}

	public boolean isPlantbendable(final LivingEntity caster, final Material material) {
		return isPlantbendable(caster, material, false, true);
	}

	public boolean isWaterbendable(final Block block) {
		return this.isWaterbendable(this.caster, block);
	}

	public boolean isWaterbendable(final LivingEntity caster, final Block block) {
		return isWaterbendable(caster, null, block);
	}

	public boolean allowBreakPlants() {
		return true;
	}

	public static boolean isWaterbendable(final Material material) {
		return isWater(material) || isIce(material) || isPlant(material) || isSnow(material) || isCauldron(material);
	}

	public static @Nullable Block getIceSourceBlock(final LivingEntity caster, final double range) {
		final Location location = caster.getEyeLocation();
		final Vector vector = location.getDirection().clone().normalize();
		for (double i = 0; i <= range; i++) {
			final Block block = location.clone().add(vector.clone().multiply(i)).getBlock();
			if (RegionProtection.isRegionProtected(caster, location,"IceBlast")) {
				continue;
			}
			if (isIcebendable(caster, block.getType(), false)) {
				if (TempBlock.isTempBlock(block) && !isBendableWaterTempBlock(block)) {
					continue;
				}
				return block;
			}
		}
		return null;
	}

	public static double getNightFactor() {
		return getConfig().getDouble("Properties.Water.NightFactor");
	}

	public static double getNightFactor(final double value, final World world) {
		if (isNight(world)) {
			return value * getNightFactor();
		}

		return value;
	}

	public static double getNightFactor(final World world) {
		return getNightFactor(1, world);
	}

	public static @Nullable Block getPlantSourceBlock(final LivingEntity caster, final double range, final boolean onlyLeaves, final boolean allowDecayBlocks) {
		final Location location = caster.getEyeLocation();
		final Vector vector = location.getDirection().clone().normalize();

		for (double i = 0; i <= range; i++) {
			final Block block = location.clone().add(vector.clone().multiply(i)).getBlock();
			if (RegionProtection.isRegionProtected(caster, location, "PlantDisc")) {
				continue;
			} else if (isPlantbendable(caster, block.getType(), onlyLeaves, allowDecayBlocks)) {
				if (TempBlock.isTempBlock(block) && !isBendableWaterTempBlock(block)) {
					continue;
				}
				return block;
			}
		}
		return null;
	}

	/**
	 * Finds a valid Water source for a Player. To use dynamic source selection,
	 * use BlockSource.getWaterSourceBlock() instead of this method. Dynamic
	 * source selection saves the user's previous source for future use.
	 * {@link BlockSource#getWaterSourceBlock(LivingEntity, double)}
	 *
	 * @param caster the caster that is attempting to Waterbend.
	 * @param range the maximum block selection range.
	 * @param plantbending true if the caster can bend plants.
	 * @return a valid Water source block, or null if one could not be found.
	 */
	public static @Nullable Block getWaterSourceBlock(final LivingEntity caster, final double range, final boolean plantbending) {
		final Location location = caster.getEyeLocation();
		final Vector vector = location.getDirection().clone().normalize();

		final Bender bender = Bender.get(caster);
		if (bender == null) return null;

		final Set<Material> trans = getTransparentMaterialSet();
		if (plantbending) {
			final Set<Material> remove = new HashSet<>();
			for (final Material m : trans) {
				if (isPlant(m) || isSnow(m)) {
					remove.add(m);
				}
			}
			trans.removeAll(remove);
		}

		boolean npc = !(caster instanceof Player player) || !player.isOnline();
		final Block testBlock = npc ? getNearestWaterBlock(caster, range) : caster.getTargetBlock(trans, Math.max(1, Math.min(3, (int)range)));
		if (testBlock != null && isWaterbendable(caster, null, testBlock) && (plantbending || (!isPlant(testBlock) && !isDecayablePlant(testBlock)))) {
			return testBlock;
		}

		for (double i = 0; i <= range; i++) {
			final Block block = location.clone().add(vector.clone().multiply(i)).getBlock();
			if ((!isTransparent(caster, block) && !isIce(block) && !isPlant(block) && !isDecayablePlant(block) && !isSnow(block)) || RegionProtection.isRegionProtected(caster, location, "WaterManipulation")) {
				continue;
			}
			if (isWaterbendable(caster, null, block) && (plantbending || (!isPlant(block) && !isDecayablePlant(block)))) {
				if (TempBlock.isTempBlock(block) && !isBendableWaterTempBlock(block)) {
					continue;
				}
				return block;
			}
		}
		return null;
	}

	public static @Nullable Block getNearestWaterBlock(final LivingEntity caster, final double range) {
		return GeneralMethods.getBlocksAroundPoint(caster.getEyeLocation(), range).stream()
				.filter(b -> isWaterbendable(caster, null, b))
				.min(Comparator.comparingDouble(b -> b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(caster.getEyeLocation())))
				.orElse(null);
	}

	public static boolean isAdjacentToFrozenBlock(final Block block) {
		final BlockFace[] faces = { BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH };
		boolean adjacent = false;
		for (final BlockFace face : faces) {
			if (PhaseChange.getFrozenBlocksAsBlock().contains((block.getRelative(face)))) {
				adjacent = true;
			}
		}
		return adjacent;
	}

	public static boolean isIcebendable(final LivingEntity caster, final Material material, final boolean onlyIce) {
		final Bender bender = Bender.get(caster);
		return bender != null && isIce(material) && bender.canIcebend() && (!onlyIce || material == Material.ICE);
	}

	public static boolean isPlantbendable(final LivingEntity caster, final Material material, final boolean onlyLeaves, final boolean allowDecayBlocks) {
		final Bender bender = Bender.get(caster);
		if (bender == null) return false;
		if (onlyLeaves) {
			return isPlant(material) && bender.canPlantbend() && isLeaves(material);
		} else if (allowDecayBlocks) {
			return (isPlant(material) || isDecayablePlant(material)) && bender.canPlantbend();
		} else {
			return isPlant(material) && bender.canPlantbend();
		}
	}

	public static boolean isLeaves(final Block block) {
		return block != null ? isLeaves(block.getType()) : false;
	}

	public static boolean isLeaves(final Material material) {
		return Tag.LEAVES.isTagged(material);
	}

	public static boolean isSnow(final Block block) {
		return block != null ? isSnow(block.getType()) : false;
	}

	public static boolean isSnow(final Material material) {
		return material == Material.SNOW || material == Material.SNOW_BLOCK;
	}
	
	public static boolean isCauldron(final Block block) {
		return isCauldron(block.getType()) ? isCauldron(block.getType()) : GeneralMethods.getMCVersion() < 1170 && block.getType() == Material.CAULDRON && ((Levelled) block.getBlockData()).getLevel() >= 1;
	}
	
	public static boolean isCauldron(final Material material) {
		return GeneralMethods.getMCVersion() >= 1170 && (material == Material.getMaterial("WATER_CAULDRON") || material == Material.getMaterial("POWDER_SNOW_CAULDRON"));
	}

	public static boolean isWaterbendable(final LivingEntity caster, final String abilityName, final Block block) {
		final Bender bender = Bender.get(caster);
		if (bender == null) return false;

		boolean waterbendable = isWater(block) || isIce(block) || isPlant(block) || isSnow(block) || isCauldron(block);
		if (!waterbendable) return false;

		if (TempBlock.isTempBlock(block) && !isBendableWaterTempBlock(block)) {
			return false;
		} else if (isWater(block) && block.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0) {
			return true;
		} else if (isIce(block) && !bender.canIcebend()) {
			return false;
		} else return (!isPlant(block) && !isDecayablePlant(block)) || bender.canPlantbend();
	}

	public static void playFocusWaterEffect(final Block block) {
		Location focusLoc = block.getLocation();
		if (isDecayablePlant(block)) {
			focusLoc = block.getRelative(BlockFace.UP).getLocation();
		}
		ParticleEffect.SMOKE_NORMAL.display(focusLoc.add(0.5, 0.5, 0.5), 4);
	}

	public static String iceMaterialName(LivingEntity caster) {
		if (caster instanceof Player player) {
			String cosmeticIceMaterial = PlaceholderAPI.setPlaceholders(player, "%avatarverse_icematerial%");
			if (!cosmeticIceMaterial.isEmpty()) {
				var custom = GeneralMethods.customBlockFromId(cosmeticIceMaterial);
				if (custom != null && custom.id().toString().startsWith("customice:")) {
					return GeneralMethods.blockDataFromCustomBlock(custom).getAsString();
				}
				Material mat = Material.getMaterial(cosmeticIceMaterial);
				if (mat != null) {
					return mat.name();
				}
			}
		}
		return Material.ICE.name();
	}

	public static BlockData iceMaterial(LivingEntity caster) {
		if (caster instanceof Player player) {
			String cosmeticIceMaterial = PlaceholderAPI.setPlaceholders(player, "%avatarverse_icematerial%");
			if (!cosmeticIceMaterial.isEmpty()) {
				var custom = GeneralMethods.customBlockFromId(cosmeticIceMaterial);
				if (custom != null && custom.id().toString().startsWith("customice:")) {
					return GeneralMethods.blockDataFromCustomBlock(custom);
				}
				Material mat = Material.getMaterial(cosmeticIceMaterial);
				if (mat != null) {
					return mat.createBlockData();
				}
			}
		}
		return Material.ICE.createBlockData();
	}

	public static void playIcebendingSound(final Location loc) {
		if (getConfig().getBoolean("Properties.Water.PlaySound")) {
			final float volume = (float) getConfig().getDouble("Properties.Water.IceSound.Volume");
			final float pitch = (float) getConfig().getDouble("Properties.Water.IceSound.Pitch");

			Sound sound = Sound.ITEM_FLINTANDSTEEL_USE;

			try {
				sound = Sound.valueOf(getConfig().getString("Properties.Water.IceSound.Sound"));
			} catch (final IllegalArgumentException exception) {
				PkLang.warning("Your current value for 'Properties.Water.IceSound.Sound' is not valid.");
			} finally {
				loc.getWorld().playSound(loc, sound, volume, pitch);
			}
		}
	}

	public static void playPlantbendingSound(final Location loc) {
		if (getConfig().getBoolean("Properties.Water.PlaySound")) {
			final float volume = (float) getConfig().getDouble("Properties.Water.PlantSound.Volume");
			final float pitch = (float) getConfig().getDouble("Properties.Water.PlantSound.Pitch");

			Sound sound = Sound.BLOCK_GRASS_STEP;

			try {
				sound = Sound.valueOf(getConfig().getString("Properties.Water.PlantSound.Sound"));
			} catch (final IllegalArgumentException exception) {
				PkLang.warning("Your current value for 'Properties.Water.PlantSound.Sound' is not valid.");
			} finally {
				loc.getWorld().playSound(loc, sound, volume, pitch);
			}
		}
	}

	public static void playWaterbendingSound(final Location loc) {
		if (getConfig().getBoolean("Properties.Water.PlaySound")) {
			final float volume = (float) getConfig().getDouble("Properties.Water.WaterSound.Volume");
			final float pitch = (float) getConfig().getDouble("Properties.Water.WaterSound.Pitch");

			Sound sound = Sound.BLOCK_WATER_AMBIENT;

			try {
				sound = Sound.valueOf(getConfig().getString("Properties.Water.WaterSound.Sound"));
			} catch (final IllegalArgumentException exception) {
				PkLang.warning("Your current value for 'Properties.Water.WaterSound.Sound' is not valid.");
			} finally {
				loc.getWorld().playSound(loc, sound, volume, pitch);
			}
		}
	}

	/**
	 * This method was used for the old collision detection system. Please see
	 * {@link Collision} for the new system.
	 * <p>
	 * Removes all water spouts in a location within a certain radius.
	 *
	 * @param loc The location to use
	 * @param radius The radius around the location to remove spouts in
	 * @param source The player causing the removal
	 */
	@Deprecated
	public static void removeWaterSpouts(final Location loc, final double radius, final LivingEntity source) {
		WaterSpout.removeSpouts(loc, radius, source);
	}

	/**
	 * This method was used for the old collision detection system. Please see
	 * {@link Collision} for the new system.
	 * <p>
	 * Removes all water spouts in a location with a radius of 1.5.
	 *
	 * @param loc The location to use
	 * @param source The player causing the removal
	 */
	@Deprecated
	public static void removeWaterSpouts(final Location loc, final LivingEntity source) {
		removeWaterSpouts(loc, 1.5, source);
	}

	/**
	 * Apply modifiers to this value. Applies the night factor to it
	 * @param value The value to modify
	 * @return The modified value
	 */
	@Override
	public double applyModifiers(double value) {
		return GeneralMethods.applyModifiers(value, getNightFactor(1.0));
	}

	/**
	 * Apply modifiers to this value. Applies the night factor to it
	 * @param value The value to modify
	 * @return The modified value
	 */
	public long applyModifiers(long value) {
		return GeneralMethods.applyModifiers(value, getNightFactor(1.0));
	}

	/**
	 * Apply modifiers to this value inversely (makes it smaller). Applies the night factor to it
	 * @param value The value to modify
	 * @return The modified value
	 */
	public double applyInverseModifiers(double value) {
		return GeneralMethods.applyInverseModifiers(value, getNightFactor(1.0));
	}

	/**
	 * Apply modifiers to this value inversely (makes it smaller). Applies the night factor to it
	 * @param value The value to modify
	 * @return The modified value
	 */
	public long applyInverseModifiers(long value) {
		return GeneralMethods.applyInverseModifiers(value, getNightFactor(1.0));
	}

	public static void stopBending() {
		SurgeWall.removeAllCleanup();
		SurgeWave.removeAllCleanup();
		WaterArms.removeAllCleanup();
		PlantRegrowth.removeAllCleanup();
	}
}
