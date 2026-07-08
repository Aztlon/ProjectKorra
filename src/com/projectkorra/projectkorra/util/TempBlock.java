package com.projectkorra.projectkorra.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.FireAbility;
import com.projectkorra.projectkorra.phasing.PhasedBlockSource;
import com.projectkorra.projectkorra.phasing.PhasedBlockVisibilityManager;
import com.projectkorra.projectkorra.phasing.GateStage;
import com.projectkorra.projectkorra.phasing.PhasedIntegrationManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Snowable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;

import io.papermc.lib.PaperLib;

public class TempBlock {

	private static final Map<Block, LinkedList<TempBlock>> instances_ = new HashMap<>();
	/**
	 * Marked for removal. Doesn't do anything right now
	 */
	@Deprecated
	public static Map<Block, TempBlock> instances = new ConcurrentHashMap<>();
	public static final PriorityQueue<TempBlock> REVERT_QUEUE = new PriorityQueue<>(100, (t1, t2) -> (int) (t1.revertTime - t2.revertTime));
	private static boolean REVERT_TASK_RUNNING;

	private final Block block;
	private BlockData newData;
	private BlockState state;
	private Set<TempBlock> attachedTempBlocks; //Temp Block states that should be reverted as well when the temp block expires (e.g. double blocks)
	private long revertTime;
	private boolean inRevertQueue;
	private boolean droppable;
	private boolean reverted;
	private Runnable revertTask = null;
	private Optional<CoreAbility> ability = Optional.empty(); // If we want this TempBlock to have an assigned ability created from it
	private PhasedBlockSource blockSource = PhasedBlockSource.none();
	private final boolean viewerOverlayOnly;
	private boolean isBendableSource = false;
	private boolean suffocate = true;

	public TempBlock(final Block block, final Material newtype) {
		this(block, newtype.createBlockData(), 0, Optional.empty(), PhasedBlockSource.current(), true);
	}

	public TempBlock(final Block block, final Material newtype, final CoreAbility ability) {
		this(block, newtype.createBlockData(), 0, ability);
	}

	public TempBlock(final Block block, final Material newtype, final long revertTime, final CoreAbility ability) {
		this(block, newtype.createBlockData(), revertTime, ability);
	}

	public TempBlock(final Block block, final Material newtype, final LivingEntity source, final String abilityId) {
		this(block, newtype.createBlockData(), 0, source, abilityId);
	}

	public TempBlock(final Block block, final Material newtype, final long revertTime, final LivingEntity source, final String abilityId) {
		this(block, newtype.createBlockData(), revertTime, source, abilityId);
	}

	/**
	 * Deprecated. Using the newType here is pointless.
	 */
	@Deprecated
	public TempBlock(final Block block, final Material newtype, final BlockData newData) {
		this(block, newData, 0, Optional.empty(), PhasedBlockSource.current(), true);
	}
	
	public TempBlock(final Block block, final BlockData newData) {
		this(block, newData, 0, Optional.empty(), PhasedBlockSource.current(), true);
	}
	
	public TempBlock(final Block block, final BlockData newData, final long revertTime, final CoreAbility ability) {
		this(block, newData, revertTime, ability == null ? Optional.empty() : Optional.of(ability), PhasedBlockSource.fromAbility(ability), true);
	}
	
	public TempBlock(final Block block, final BlockData newData, final CoreAbility ability) {
		this(block, newData, 0, ability);
	}

	public TempBlock(final Block block, final BlockData newData, final LivingEntity source, final String abilityId) {
		this(block, newData, 0, source, abilityId);
	}

	public TempBlock(final Block block, final BlockData newData, final long revertTime, final LivingEntity source, final String abilityId) {
		this(block, newData, revertTime, Optional.empty(), PhasedBlockSource.fromEntity(source, abilityId), true);
	}

	public TempBlock(final Block block, BlockData newData, final long revertTime) {
		this(block, newData, revertTime, Optional.empty(), PhasedBlockSource.current(), true);
	}

	private TempBlock(final Block block, BlockData newData, final long revertTime, final Optional<CoreAbility> sourceAbility,
			final PhasedBlockSource blockSource, final boolean internalCtor) {
		this.block = block;
		this.newData = newData;
		this.ability = sourceAbility;
		this.blockSource = blockSource == null ? PhasedBlockSource.none() : blockSource;
		this.attachedTempBlocks = new HashSet<>(0);
		this.state = block.getState();
		this.viewerOverlayOnly = this.shouldUseViewerOverlay(block.getLocation());

		if (!this.viewerOverlayOnly && !this.shouldAllowBlockMutation(block.getLocation())) {
			this.reverted = true;
			return;
		}

		//Fire griefing will make the state update on its own, so we don't need to update it ourselves
//		if (!FireAbility.canFireGrief() && (newData.getMaterial() == Material.FIRE || newData.getMaterial() == Material.SOUL_FIRE)) {
//			newData = FireAbility.createFireState(block, newData.getMaterial() == Material.SOUL_FIRE ? "SOUL_FIRE" : "FIRE"); //Fix the blockstate looking incorrect
//		}
		if (block.getType() == Material.SNOW) {
			if (newData.getMaterial() == Material.AIR) {
				updateSnowableBlock(block.getRelative(BlockFace.DOWN),false);
			}
		}

		if (instances_.containsKey(block)) {
			final TempBlock temp = instances_.get(block).getFirst();
			this.state = temp.state; //Set the original blockstate of the tempblock
		} else {
			if (this.state instanceof Container || this.state.getType() == Material.JUKEBOX) {
				return;
			}
		}
		put(block, this);
		this.applyInitialState(newData);

		this.setRevertTime(revertTime);
	}

	private void applyInitialState(final BlockData newBlockData) {
		if (this.viewerOverlayOnly) {
			PhasedBlockVisibilityManager.applyOverlay(this.block.getLocation(), newBlockData, this.blockSource);
			return;
		}

		this.block.setBlockData(newBlockData, applyPhysics(newBlockData.getMaterial()));
	}

	/**
	 * Get a TempBlock at a location
	 * @param block The block location
	 * @return The topmost TempBlock
	 */
	public static TempBlock get(final Block block) {
		if (isTempBlock(block)) {
			return instances_.get(block).getLast();
		}
		return null;
	}

	/**
	 * Get all TempBlocks at the given location
	 * @param block The block location
	 * @return The list of TempBlocks
	 */
	public static LinkedList<TempBlock> getAll(Block block) {
		return instances_.get(block);
	}

	/**
	 * Place a TempBlock in the system
	 * @param block The block location
	 * @param tempBlock The TempBlock
	 */
	private static void put(Block block, TempBlock tempBlock) {
		if (!instances_.containsKey(block)) {
			instances_.put(block, new LinkedList<>());
		}
		instances_.get(block).add(tempBlock);
	}

	public static boolean isTempBlock(final Block block) {
		return block != null && instances_.containsKey(block);
	}

	/**
	 * Is the specified block touching a TempBlock? Used to prevent physics updates
	 * for things like Water
	 * @param block The block location
	 * @return True if there is a TempBlock beside it
	 */
	public static boolean isTouchingTempBlock(final Block block) {
		final BlockFace[] faces = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN };
		for (final BlockFace face : faces) {
			if (instances_.containsKey(block.getRelative(face))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Remove and revert all TempBlocks on the server. Done at server shutdown or PK reload.
	 */
	public static void removeAll() {
		for (final Block block : new HashSet<>(instances_.keySet())) {
			revertBlock(block, Material.AIR);
		}
		for (final TempBlock tempblock : REVERT_QUEUE) {
			if (tempblock.viewerOverlayOnly) {
				PhasedBlockVisibilityManager.clearOverlay(tempblock.block.getLocation());
			} else {
				tempblock.state.update(true, applyPhysics(tempblock.state.getType()));
			}
			if (tempblock.revertTask != null) {
				tempblock.revertTask.run();
			}
		}
		REVERT_QUEUE.clear();
	}

	/**
	 * Remove all TempBlocks at this location. Used for when a player places a block inside a TempBlock
	 * @param block The block location
	 */
	public static void removeBlock(final Block block) {
		if (!instances_.containsKey(block)) {
			return;
		}

		final List<TempBlock> tempBlocks = new ArrayList<>(instances_.get(block));
		tempBlocks.forEach(t -> {
			REVERT_QUEUE.remove(t);
			remove(t);
		});
	}

	/**
	 * Remove this instance from the system
	 * @param tempBlock The TempBlock to remove
	 */
	private static void remove(TempBlock tempBlock) {
		if (instances_.containsKey(tempBlock.block)) {
			instances_.get(tempBlock.block).remove(tempBlock);
			if (instances_.get(tempBlock.block).isEmpty()) {
				instances_.remove(tempBlock.block);
			}
		}
	}

	/**
	 * Revert all TempBlocks at this location
	 * @param block The block location
	 * @param defaulttype The default material to revert to if it can't
	 */
	public static void revertBlock(final Block block, final Material defaulttype) {
		if (instances_.containsKey(block)) {
			final List<TempBlock> tempBlocks = new ArrayList<>(instances_.get(block));
			final TempBlock finalTempBlock = tempBlocks.get(tempBlocks.size() - 1);

			tempBlocks.forEach(TempBlock::remove);
			tempBlocks.forEach(TempBlock::prepareForRevert);
			finalTempBlock.applyOriginalStateWhenReady();
			tempBlocks.forEach(TempBlock::runRevertActions);
		} else {
			if ((defaulttype == Material.LAVA) && GeneralMethods.isAdjacentToThreeOrMoreSources(block, true)) {
				final BlockData data = Material.LAVA.createBlockData();

				if (data instanceof Levelled) {
					((Levelled) data).setLevel(0);
				}

				block.setBlockData(data, applyPhysics(data.getMaterial()));
			} else if ((defaulttype == Material.WATER) && GeneralMethods.isAdjacentToThreeOrMoreSources(block)) {
				final BlockData data = Material.WATER.createBlockData();

				if (data instanceof Levelled) {
					((Levelled) data).setLevel(0);
				}

				block.setBlockData(data, applyPhysics(data.getMaterial()));
			} else {
				block.setType(defaulttype, applyPhysics(defaulttype));
			}
		}
	}

	public boolean isDroppable() {
		return this.droppable;
	}

	public void setDroppable(final boolean droppable) {
		this.droppable = droppable;
	}

	public Block getBlock() {
		return this.block;
	}

	public BlockData getBlockData() {
		return this.newData;
	}

	public Location getLocation() {
		return this.block.getLocation();
	}

	public BlockState getState() {
		return this.state;
	}
	
	public Optional<CoreAbility> getAbility() {
		return this.ability;
	}

	public Runnable getRevertTask() {
		return this.revertTask;
	}

	public void setRevertTask(final Runnable task) {
		this.revertTask = task;
	}

	public long getRevertTime() {
		return this.revertTime;
	}

	/**
	 * Make this TempBlock revert automatically after the specified amount of time
	 * @param revertTime The time it takes to revert. In milliseconds.
	 */
	public void setRevertTime(final long revertTime) {
		if (revertTime <= 0 || state instanceof Container) {
			return;
		}
		
		if (this.inRevertQueue) {
			REVERT_QUEUE.remove(this);
		}
		this.inRevertQueue = true;
		this.revertTime = revertTime + System.currentTimeMillis();
		if (!REVERT_QUEUE.contains(this)) {
			REVERT_QUEUE.add(this);
		}
	}

	/**
	 * Revert this TempBlock
	 */
	public void revertBlock() {
		if (!this.reverted) {
			remove(this);
			trueRevertBlock(get(this.block));
		}
	}

	/**
	 * This is used to revert the block without removing the instances from memory. Used when multiple tempblocks are to be reverted at once
	 */
	private void trueRevertBlock(final TempBlock nextTempBlock) {
		prepareForRevert();
		if (nextTempBlock != null) {
			applyNextTempBlockWhenReady(nextTempBlock);
		} else {
			applyOriginalStateWhenReady();
		}

		runRevertActions();
	}

	private void prepareForRevert() {
		this.reverted = true;
		this.inRevertQueue = false;
		REVERT_QUEUE.remove(this);
	}

	private void runRevertActions() {
		if (this.revertTask != null) {
			this.revertTask.run();
		}

		for (final TempBlock attached : new HashSet<>(this.attachedTempBlocks)) {
			attached.revertBlock();
		}
	}

	private void applyNextTempBlockWhenReady(final TempBlock nextTempBlock) {
		PaperLib.getChunkAtAsync(this.block.getLocation()).thenAccept(result -> {
			if (TempBlock.get(this.block) == nextTempBlock && (nextTempBlock.viewerOverlayOnly || this.shouldAllowBlockMutation(this.block.getLocation()))) {
				if (nextTempBlock.viewerOverlayOnly) {
					PhasedBlockVisibilityManager.applyOverlay(this.block.getLocation(), nextTempBlock.newData, nextTempBlock.ability.orElse(null));
				} else {
					PhasedBlockVisibilityManager.clearOverlay(this.block.getLocation());
					this.block.setBlockData(nextTempBlock.newData, applyPhysics(nextTempBlock.newData.getMaterial()));
				}
			}
		});
	}

	private void applyOriginalStateWhenReady() {
		PaperLib.getChunkAtAsync(this.block.getLocation()).thenAccept(result -> {
			if (!instances_.containsKey(this.block)) {
				if (this.viewerOverlayOnly) {
					PhasedBlockVisibilityManager.clearOverlay(this.block.getLocation());
				} else if (this.shouldAllowBlockMutation(this.block.getLocation())) {
					revertState();
				}
			}
		});
	}

	/**
	 * Revert the TempBlock to the proper BlockState it should be
	 */
	private void revertState() {
		if (this.viewerOverlayOnly) {
			PhasedBlockVisibilityManager.clearOverlay(this.block.getLocation());
			return;
		}

		Block block = this.state.getBlock();
		//If the block has been changed by the time we revert (e.g. block place). Also, we ignore fire since it isn't worth the time
		if (block.getType() != this.newData.getMaterial() && block.getType() != Material.FIRE && block.getType() != Material.SOUL_FIRE) {
			//Get the drops of the original block and drop them in the world
			GeneralMethods.dropItems(block, GeneralMethods.getDrops(block, this.state.getType(), this.state.getBlockData()));
		} else {
			//Previous Material was SNOW
			if (this.state.getType() == Material.SNOW){
				updateSnowableBlock(block.getRelative(BlockFace.DOWN), true);
			}

			//Revert the original blockstate
			this.state.update(true, applyPhysics(state.getType())
					&& !(state.getBlockData() instanceof Bisected));
		}
	}

	/**
	 * Make the provided tempblock revert at the same time as the current tempblock
	 * @param tempBlock The tempblock to attach to the current tempblock
	 */
	public void addAttachedBlock(TempBlock tempBlock) {
		this.attachedTempBlocks.add(tempBlock);
		tempBlock.attachedTempBlocks.add(this);
	}

	/**
	 * @return The list of attached tempblocks
	 */
	public Set<TempBlock> getAttachedTempBlocks() {
		return attachedTempBlocks;
	}

	/**
	 * <b>Not yet implemented. For future use.</b>
	 * @return Can this TempBlock be used as a source block
	 */
	@Experimental
	public boolean isBendableSource() {
		return isBendableSource;
	}

	/**
	 * <b>Not yet implemented. For future use.</b>
	 * Set if the TempBlock can be used as a source block
	 * @param bool If it can be used as a source block
	 */
	@Experimental
	public TempBlock setBendableSource(boolean bool) {
		this.isBendableSource = bool;
		return this;
	}

	/**
	 * @return True if the block will suffocate entities inside it
	 */
	public boolean canSuffocate() {
		return suffocate;
	}

	/**
	 * Set if the TempBlock will suffocate entities inside of it
	 * @param suffocate True if they will suffocate, false if they won't
	 */
	public TempBlock setCanSuffocate(boolean suffocate) {
		this.suffocate = suffocate;
		return this;
	}

	public void setState(final BlockState newstate) {
		this.state = newstate;
	}

	public void setType(final Material material) {
		this.setType(material.createBlockData());
	}

	@Deprecated
	public void setType(final Material material, final BlockData data) {
		this.setType(data);
	}

	public void setType(final BlockData data) {
		if (isReverted())
			return;
		if (!this.viewerOverlayOnly && !this.shouldAllowBlockMutation(this.block.getLocation())) {
			return;
		}
		this.newData = data;
		if (this.viewerOverlayOnly) {
			PhasedBlockVisibilityManager.applyOverlay(this.block.getLocation(), data, this.blockSource);
		} else {
			this.block.setBlockData(data, applyPhysics(data.getMaterial()));
		}
	}

	private boolean shouldUseViewerOverlay(final Location location) {
		return PhasedBlockVisibilityManager.shouldUseViewerOverlay(this.blockSource, location);
	}

	private boolean shouldAllowBlockMutation(final Location location) {
		return PhasedIntegrationManager.shouldAllow(this.blockSource.request(GateStage.BLOCK, location, null));
	}

	public static void startReversion() {
		new BukkitRunnable() {
			@Override
			public void run() {
				final long currentTime = System.currentTimeMillis();
				while (!REVERT_QUEUE.isEmpty()) {
					final TempBlock tempBlock = REVERT_QUEUE.peek();
					if (currentTime >= tempBlock.revertTime) {
						REVERT_QUEUE.poll();
						tempBlock.revertBlock();
					} else {
						break;
					}
				}
			}
		}.runTaskTimer(ProjectKorra.plugin, 0, 1);
	}

	/**
	 * @return If the TempBlock has reverted
	 */
	public boolean isReverted() {
		return reverted;
	}

	public TempBlock setAbility(CoreAbility ability) {
		this.ability = ability == null ? Optional.empty() : Optional.of(ability);
		this.blockSource = PhasedBlockSource.fromAbility(ability);
		return this;
	}

	@Deprecated
	/**
	 * Will be removed in future. Exactly the same as a Runnable so no point having a unique class for it
	 */
	public interface RevertTask extends Runnable { }

	/**
	 * Whether the physics should be updated or not. Fire should be updated so it can burn and spread IF
	 * FireGrief is on
	 * @param material The material to check
	 * @return True if physics should be applied
	 */
	public static boolean applyPhysics(Material material) {
		return GeneralMethods.isLightEmitting(material) || (material == Material.FIRE && FireAbility.canFireGrief());
	}

	/**
	 * Update grass blocks
	 * @param b The block
	 * @param snowy If its snowy
	 */
	public void updateSnowableBlock(Block b, boolean snowy){
		if (b.getBlockData() instanceof Snowable){
			final Snowable snowable = (Snowable) b.getBlockData();
			snowable.setSnowy(snowy);
			b.setBlockData(snowable);
		}
	}

}
