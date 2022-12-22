package com.projectkorra.projectkorra.waterbending.plant;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.EarthAbility;
import com.projectkorra.projectkorra.ability.PlantAbility;
import com.projectkorra.projectkorra.util.TempBlock;

public class PlantRegrowth extends PlantAbility {

	private BlockData data;
	private long time;
	private long regrowTime;
	private Material type;
	private Block block;
	private ConcurrentHashMap<Block, TempBlock> affectedBlocks = new ConcurrentHashMap<>();
	private static ArrayList<TempBlock> decayedBlocks = new ArrayList<>();
	private boolean willRevertNearbyPlants;

	public PlantRegrowth(final Player player, final Block block, final double radius) {
		super(player);

		this.regrowTime = getConfig().getLong("Abilities.Water.Plantbending.RegrowTime");
		if (this.regrowTime != 0) {
			this.block = block;
			this.type = block.getType();
			this.data = block.getBlockData();

			if (isBendableWaterTempBlock(block)) {
				return;
			}

			if (isDecayablePlant(type)) {
				this.willRevertNearbyPlants = true;

				for (Block b : GeneralMethods.getBlocksAroundPoint(block.getLocation(), radius)) {
					if (b.getType() != type) {
						continue;
					}

					if (!GeneralMethods.isTransparent(b.getRelative(BlockFace.UP))) {
						continue;
					}

					Material newMaterial;

					switch (b.getType()) {
					case CRIMSON_NYLIUM:
					case WARPED_NYLIUM:
						newMaterial = Material.NETHERRACK;
						break;
					case OAK_LOG:
						newMaterial = Material.STRIPPED_OAK_LOG;
						break;
					case SPRUCE_LOG:
						newMaterial = Material.STRIPPED_SPRUCE_LOG;
						break;
					case BIRCH_LOG:
						newMaterial = Material.STRIPPED_BIRCH_LOG;
						break;
					case JUNGLE_LOG:
						newMaterial = Material.STRIPPED_JUNGLE_LOG;
						break;
					case DARK_OAK_LOG:
						newMaterial = Material.STRIPPED_DARK_OAK_LOG;
						break;
					case ACACIA_LOG:
						newMaterial = Material.STRIPPED_ACACIA_LOG;
						break;
					case OAK_WOOD:
						newMaterial = Material.STRIPPED_OAK_WOOD;
						break;
					case SPRUCE_WOOD:
						newMaterial = Material.STRIPPED_SPRUCE_WOOD;
						break;
					case BIRCH_WOOD:
						newMaterial = Material.STRIPPED_BIRCH_WOOD;
						break;
					case JUNGLE_WOOD:
						newMaterial = Material.STRIPPED_JUNGLE_WOOD;
						break;
					case DARK_OAK_WOOD:
						newMaterial = Material.STRIPPED_DARK_OAK_WOOD;
						break;
					case ACACIA_WOOD:
						newMaterial = Material.STRIPPED_ACACIA_WOOD;
						break;
					case CRIMSON_STEM:
						newMaterial = Material.STRIPPED_CRIMSON_STEM;
						break;
					case WARPED_STEM:
						newMaterial = Material.STRIPPED_WARPED_STEM;
						break;
					case CRIMSON_HYPHAE:
						newMaterial = Material.STRIPPED_CRIMSON_HYPHAE;
						break;
					case WARPED_HYPHAE:
						newMaterial = Material.STRIPPED_WARPED_HYPHAE;
						break;
					default:
						newMaterial = Material.PODZOL;
						break;
					}

					TempBlock tb = new TempBlock(b, newMaterial);
					affectedBlocks.put(b, tb);
					decayedBlocks.add(tb);
					EarthAbility.addEarthbendableTempBlock(tb);
					tb.setRevertTask(() -> EarthAbility.removeEarthbendableTempBlock(tb));
				}
			}

			if (block.getType() == Material.TALL_GRASS) {
				if (block.getRelative(BlockFace.DOWN).getType() == Material.TALL_GRASS) {
					this.block = block.getRelative(BlockFace.DOWN);
					this.data = block.getRelative(BlockFace.DOWN).getBlockData();

					block.getRelative(BlockFace.DOWN).setType(Material.AIR);
					block.setType(Material.AIR);
				} else {
					block.setType(Material.AIR);
					block.getRelative(BlockFace.UP).setType(Material.AIR);
				}
			}

			this.time = System.currentTimeMillis() + this.regrowTime / 2 + (long) (Math.random() * this.regrowTime) / 2;
			this.start();
		}
	}

	public PlantRegrowth(final Player player, final Block block) {
		this(player, block, 0);
	}

	@Override
	public void remove() {
		super.remove();
		if (this.willRevertNearbyPlants) {
			this.revertBlocks();
			affectedBlocks.clear();
		} else if (isAir(this.block.getType())) {
			this.block.setType(this.type);
			this.block.setBlockData(this.data);
			if (this.type == Material.TALL_GRASS) {
				this.block.getRelative(BlockFace.UP).setType(Material.TALL_GRASS);
			}
		} else {
			GeneralMethods.dropItems(this.block, GeneralMethods.getDrops(this.block, this.type, this.data));
		}
	}

	public void revertBlocks() {
		Enumeration<Block> keys = affectedBlocks.keys();
		while (keys.hasMoreElements()) {
			Block block = keys.nextElement();
			affectedBlocks.get(block).revertBlock();
			affectedBlocks.remove(block);
		}
	}

	public static ArrayList<TempBlock> getDecayedBlocks() {
		return decayedBlocks;
	}

	public static void removeAllCleanup() {
		decayedBlocks.clear();
	}

	@Override
	public void progress() {
		if (this.time < System.currentTimeMillis()) {
			this.remove();
		}
	}

	@Override
	public String getName() {
		return "PlantRegrowth";
	}

	@Override
	public Location getLocation() {
		return this.block != null ? this.block.getLocation() : null;
	}

	@Override
	public boolean isHiddenAbility() {
		return true;
	}

	@Override
	public long getCooldown() {
		return 0;
	}

	@Override
	public boolean isSneakAbility() {
		return false;
	}

	@Override
	public boolean isHarmlessAbility() {
		return true;
	}

	public BlockData getData() {
		return this.data;
	}

	public void setData(final BlockData data) {
		this.data = data;
	}

	public long getTime() {
		return this.time;
	}

	public void setTime(final long time) {
		this.time = time;
	}

	public long getRegrowTime() {
		return this.regrowTime;
	}

	public void setRegrowTime(final long regrowTime) {
		this.regrowTime = regrowTime;
	}

	public Material getType() {
		return this.type;
	}

	public void setType(final Material type) {
		this.type = type;
	}

	public Block getBlock() {
		return this.block;
	}

	public void setBlock(final Block block) {
		this.block = block;
	}

}