package com.projectkorra.projectkorra.util;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.phasing.GateStage;
import com.projectkorra.projectkorra.phasing.PhasedIntegrationManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TempFallingBlock {
    public enum CreationResult {
        CREATED,
        DENIED_BY_PHASED_BLOCK_GATE
    }

    public static ConcurrentHashMap<FallingBlock, TempFallingBlock> instances = new ConcurrentHashMap<>();

    private FallingBlock fallingblock;
    private CoreAbility ability;
    private long creation;
    private boolean expire;
    private boolean immuneToBending;
    private final CreationResult creationResult;
    private final Set<UUID> hiddenViewers = new HashSet<>();
    private Consumer<TempFallingBlock> onPlace, onTick;

    public TempFallingBlock(Location location, BlockData data, Vector velocity, CoreAbility ability) {
        this(location, data, velocity, ability, false);
    }

    public TempFallingBlock(Location location, BlockData data, Vector velocity, CoreAbility ability, boolean expire) {
        this.ability = ability;
        this.creation = System.currentTimeMillis();
        this.expire = expire;

        // A source-aware falling block needs to exist so its ability can continue processing it.
        // Its audience is filtered per viewer below, while placement remains gated in tryPlace().
        if (ability == null && !PhasedIntegrationManager.shouldAllow(
                PhasedIntegrationManager.requestFromAbility(ability, null, GateStage.BLOCK, location, null))) {
            this.creationResult = CreationResult.DENIED_BY_PHASED_BLOCK_GATE;
            return;
        }

        this.fallingblock = location.getWorld().spawnFallingBlock(location, data.clone());
        this.fallingblock.setVelocity(velocity);
        this.fallingblock.setDropItem(false);
        this.creationResult = CreationResult.CREATED;
        instances.put(this.fallingblock, this);
        this.refreshViewerVisibility();
    }

    public static void manage() {
        long time = System.currentTimeMillis();

        for (TempFallingBlock tfb : instances.values()) {
            tfb.refreshViewerVisibility();

            Consumer<TempFallingBlock> onTick = tfb.getOnTick();
            if (onTick != null) {
                onTick.accept(tfb);
            }

            if (tfb.getFallingBlock().isDead()) {
                tfb.remove();
            } else if (tfb.canExpire() && time > tfb.getCreationTime() + 5000) {
                tfb.remove();
            } else if (time > tfb.getCreationTime() + 120000) { // Add a hard timeout for any abilities that misuse this.
                tfb.remove();
            }
        }
    }

    public static TempFallingBlock get(FallingBlock fallingblock) {
        if (isTempFallingBlock(fallingblock)) {
            return instances.get(fallingblock);
        }
        return null;
    }

    public static boolean isTempFallingBlock(FallingBlock fallingblock) {
        return instances.containsKey(fallingblock);
    }

    public static void removeFallingBlock(FallingBlock fallingblock) {
        if (isTempFallingBlock(fallingblock)) {
            fallingblock.remove();
            instances.remove(fallingblock);
        }
    }

    public static void removeAllFallingBlocks() {
        for (FallingBlock fallingblock : instances.keySet()) {
            fallingblock.remove();
            instances.remove(fallingblock);
        }
    }

    public static List<TempFallingBlock> getFromAbility(CoreAbility ability) {
        List<TempFallingBlock> tfbs = new ArrayList<TempFallingBlock>();
        for (TempFallingBlock tfb : instances.values()) {
            if (Objects.equals(tfb.getAbility(), ability)) {
                tfbs.add(tfb);
            }
        }
        return tfbs;
    }

    public void remove() {
        if (fallingblock == null) {
            return;
        }
        fallingblock.remove();
        instances.remove(fallingblock);
    }

    public FallingBlock getFallingBlock() {
        return fallingblock;
    }

    public CoreAbility getAbility() {
        return ability;
    }

    public Material getMaterial() {
        if (fallingblock == null) {
            return Material.AIR;
        }
        return fallingblock.getBlockData().getMaterial();
    }

    public BlockData getMaterialData() {
        if (fallingblock == null) {
            return Material.AIR.createBlockData();
        }
        return fallingblock.getBlockData();
    }

    public BlockData getData() {
        if (fallingblock == null) {
            return Material.AIR.createBlockData();
        }
        return fallingblock.getBlockData();
    }

    public Location getLocation() {
        return fallingblock == null ? null : fallingblock.getLocation();
    }

    public long getCreationTime() {
        return creation;
    }

    public boolean canExpire() {
        return expire;
    }

    /**
     * Returns the synchronous outcome of this wrapper's construction. Addons should check this
     * before using {@link #getFallingBlock()} when constructing a source-less falling block.
     */
    public CreationResult getCreationResult() {
        return this.creationResult;
    }

    public boolean wasCreated() {
        return this.creationResult == CreationResult.CREATED;
    }

    public boolean wasDenied() {
        return this.creationResult == CreationResult.DENIED_BY_PHASED_BLOCK_GATE;
    }

    public void tryPlace() {
        if (!this.wasCreated() || this.fallingblock == null) {
            return;
        }
        if (!PhasedIntegrationManager.shouldAllow(
                PhasedIntegrationManager.requestFromAbility(this.ability, null, GateStage.BLOCK, this.fallingblock.getLocation(), null))) {
            return;
        }
        if (onPlace != null) {
            onPlace.accept(this);
        }
    }

    private void refreshViewerVisibility() {
        if (this.ability == null || this.fallingblock == null || this.fallingblock.isDead()) {
            return;
        }

        final Location location = this.fallingblock.getLocation();
        for (final Player viewer : this.fallingblock.getWorld().getPlayers()) {
            final boolean isCaster = this.ability.getCaster() != null
                    && this.ability.getCaster().getUniqueId().equals(viewer.getUniqueId());
            final boolean visible = isCaster || PhasedIntegrationManager.shouldViewerObserve(
                    PhasedIntegrationManager.requestFromAbility(this.ability, null, GateStage.BLOCK, location, viewer.getUniqueId()));
            if (visible) {
                if (this.hiddenViewers.remove(viewer.getUniqueId())) {
                    viewer.showEntity(ProjectKorra.plugin, this.fallingblock);
                }
            } else if (this.hiddenViewers.add(viewer.getUniqueId())) {
                viewer.hideEntity(ProjectKorra.plugin, this.fallingblock);
            }
        }
    }

    public Consumer<TempFallingBlock> getOnPlace() {
        return onPlace;
    }

    public TempFallingBlock setOnPlace(Consumer<TempFallingBlock> onPlace) {
        this.onPlace = onPlace;
        return this;
    }

    public Consumer<TempFallingBlock> getOnTick() {
        return onTick;
    }

    public TempFallingBlock setOnTick(Consumer<TempFallingBlock> onTick) {
        this.onTick = onTick;
        return this;
    }

    public boolean isImmuneToBending() {
        return immuneToBending;
    }

    public TempFallingBlock setImmuneToBending(boolean immuneToBending) {
        this.immuneToBending = immuneToBending;
        return this;
    }
}
