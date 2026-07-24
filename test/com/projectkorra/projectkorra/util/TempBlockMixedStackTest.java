package com.projectkorra.projectkorra.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TempBlockMixedStackTest {

	@AfterEach
	void clearTempBlocks() throws Exception {
		instances().clear();
		TempBlock.REVERT_QUEUE.clear();
	}

	@Test
	void physicalLayerRevertsBeforeOverlayLayerExpires() throws Exception {
		final FakeBlock fakeBlock = new FakeBlock(Material.DIRT);
		final TempBlock physical = new TempBlock(fakeBlock.block, blockData(Material.STONE));
		final TempBlock overlay = new TempBlock(fakeBlock.block, blockData(Material.SAND));

		setViewerOverlayOnly(overlay, true);
		fakeBlock.currentData.set(physical.getBlockData());

		remove(physical);
		applyNext(physical, overlay, null, true);
		assertEquals(Material.DIRT, fakeBlock.currentData.get().getMaterial(),
				"expiring the physical layer must restore the real block beneath the overlay");

		remove(overlay);
		applyOriginal(overlay, false, true);
		assertEquals(Material.DIRT, fakeBlock.currentData.get().getMaterial(),
				"clearing the final overlay must not resurrect its expired physical layer");
	}

	@SuppressWarnings("unchecked")
	private static Map<Block, ?> instances() throws Exception {
		final Field field = TempBlock.class.getDeclaredField("instances_");
		field.setAccessible(true);
		return (Map<Block, ?>) field.get(null);
	}

	private static void setViewerOverlayOnly(final TempBlock tempBlock, final boolean value) throws Exception {
		final Field field = TempBlock.class.getDeclaredField("viewerOverlayOnly");
		field.setAccessible(true);
		field.setBoolean(tempBlock, value);
	}

	private static void remove(final TempBlock tempBlock) throws Exception {
		final Method method = TempBlock.class.getDeclaredMethod("remove", TempBlock.class);
		method.setAccessible(true);
		method.invoke(null, tempBlock);
	}

	private static void applyNext(final TempBlock owner, final TempBlock logical, final TempBlock physical,
			final boolean restorePhysical) throws Exception {
		final Method method = TempBlock.class.getDeclaredMethod("applyNextTempBlock", TempBlock.class, TempBlock.class, boolean.class);
		method.setAccessible(true);
		method.invoke(owner, logical, physical, restorePhysical);
	}

	private static void applyOriginal(final TempBlock owner, final boolean restorePhysical, final boolean clearOverlay) throws Exception {
		final Method method = TempBlock.class.getDeclaredMethod("applyOriginalState", boolean.class, boolean.class);
		method.setAccessible(true);
		method.invoke(owner, restorePhysical, clearOverlay);
	}

	private static BlockData blockData(final Material material) {
		return proxy(BlockData.class, (method, args) -> switch (method.getName()) {
			case "getMaterial" -> material;
			case "clone" -> blockData(material);
			case "toString", "getAsString" -> material.name();
			case "hashCode" -> material.hashCode();
			case "equals" -> args != null && args.length == 1 && args[0] instanceof BlockData
					&& ((BlockData) args[0]).getMaterial() == material;
			default -> defaultValue(method.getReturnType());
		});
	}

	private static final class FakeBlock {
		private final AtomicReference<BlockData> currentData;
		private final Block block;

		private FakeBlock(final Material originalMaterial) {
			final BlockData originalData = blockData(originalMaterial);
			this.currentData = new AtomicReference<>(originalData);
			final World world = proxy(World.class, (method, args) -> switch (method.getName()) {
				case "getUID" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
				default -> defaultValue(method.getReturnType());
			});
			final AtomicReference<Block> blockReference = new AtomicReference<>();
			final BlockState originalState = proxy(BlockState.class, (method, args) -> switch (method.getName()) {
				case "getBlock" -> blockReference.get();
				case "getType" -> originalMaterial;
				case "getBlockData" -> originalData;
				case "update" -> {
					this.currentData.set(originalData);
					yield true;
				}
				default -> defaultValue(method.getReturnType());
			});
			this.block = proxy(Block.class, (method, args) -> switch (method.getName()) {
				case "getState" -> originalState;
				case "getType" -> this.currentData.get().getMaterial();
				case "getBlockData" -> this.currentData.get();
				case "getLocation" -> new Location(world, 1, 64, 1);
				case "setBlockData" -> {
					this.currentData.set((BlockData) args[0]);
					yield null;
				}
				default -> defaultValue(method.getReturnType());
			});
			blockReference.set(this.block);
		}
	}

	private interface Invocation {
		Object invoke(Method method, Object[] args);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(final Class<T> type, final Invocation invocation) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
				(proxy, method, args) -> invocation.invoke(method, args));
	}

	private static Object defaultValue(final Class<?> type) {
		if (!type.isPrimitive()) return null;
		if (type == boolean.class) return false;
		if (type == char.class) return '\0';
		if (type == byte.class) return (byte) 0;
		if (type == short.class) return (short) 0;
		if (type == int.class) return 0;
		if (type == long.class) return 0L;
		if (type == float.class) return 0F;
		return 0D;
	}
}
