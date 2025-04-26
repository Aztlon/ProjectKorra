package com.projectkorra.projectkorra.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BiPredicate;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;

/**
 * An object to control how an entity moves. <br>
 * Current functions include <b>stopping</b>.
 *
 * @author Simplicitee
 *
 */
public class MovementHandler {

	public static final Set<MovementHandler> HANDLERS = new HashSet<>();

	private final LivingEntity entity;
	private BukkitRunnable runnable, msg;
	private Runnable resetTask = null;
	private final CoreAbility ability;
	private boolean stopped;

	public MovementHandler(final LivingEntity entity, final CoreAbility ability) {
		this.entity = entity;
		this.ability = ability;
		HANDLERS.add(this);
	}

	/**
	 * This stops the movement of the entity once they land on the ground,
	 * acting as a "paralyze" with a duration for how long they should be
	 * stopped
	 *
	 * @param duration how long the entity should be stopped for <b>(in
	 *            ticks)</b>.
	 * @param message the message to send to the stopped entity if they are a
	 *            player
	 */
	public void stopWithDuration(final long duration, final String message) {
		if (this.entity instanceof final Player player) {
			final long start = System.currentTimeMillis();
			this.runnable = new BukkitRunnable() {
				public void run() {
					ActionBar.sendActionBar(message, player);
					if (System.currentTimeMillis() >= start + duration * 50) {
						MovementHandler.this.reset();
					}
				}
			};
			this.runnable.runTaskTimer(ProjectKorra.plugin, 0, 1);
		} else {
			this.runnable = new BukkitRunnable() {
				public void run() {
					MovementHandler.this.reset();
				}
			};
			new BukkitRunnable() {
				public void run() {
					if (MovementHandler.this.entity.isOnGround()) {
						MovementHandler.this.entity.setAI(false);
						this.cancel();
						MovementHandler.this.runnable.runTaskLater(ProjectKorra.plugin, duration);
					}
				}
			}.runTaskTimer(ProjectKorra.plugin, 0, 1);
		}
		stopped = true;
	}

	/**
	 * This stops the movement of the entity once they land on the ground,
	 * acting as a "paralyze"
	 *
	 * @param message the message to send to the stopped entity if they are a
	 *            player
	 */
	public void stop(final String message) {
		if (this.entity instanceof final Player player) {
			this.msg = new BukkitRunnable() {
				public void run() {
					ActionBar.sendActionBar(message, player);
				}
			};
			this.msg.runTaskTimer(ProjectKorra.plugin, 0, 1);
		} else {
			new BukkitRunnable() {
				public void run() {
					if (MovementHandler.this.entity.isOnGround()) {
						MovementHandler.this.entity.setAI(false);
						this.cancel();
					}
				}
			}.runTaskTimer(ProjectKorra.plugin, 0, 1);
		}
		this.runnable = null;
		stopped = true;
	}

	/**
	 * Resets any stopped movements and runs the reset task if able.
	 */
	public void reset() {
		reset(true);
	}

	/**
	 * Resets any stopped movements and runs the reset task if able.
	 */
	public void reset(boolean remove) {
		if (this.runnable != null) {
			try {
				this.runnable.cancel();
			} catch (final IllegalStateException e) { //if a player hasn't landed on the ground yet this runnable wont be scheduled, and will give an error on server shutdown
				this.runnable = null;
			}
		}
		if (this.msg != null)
			this.msg.cancel();
		if (!(this.entity instanceof Player))
			this.entity.setAI(true);
		if (this.resetTask != null)
			this.resetTask.run();
		stopped = false;
		if (remove)
			HANDLERS.remove(this);
	}

	public CoreAbility getAbility() {
		return this.ability;
	}

	public LivingEntity getEntity() {
		return this.entity;
	}

	public void setResetTask(final Runnable resetTask) {
		this.resetTask = resetTask;
	}

	/**
	 * Checks if the entity is stopped by an instance of MovementHandler
	 *
	 * @param entity the entity in question of being stopped
	 * @return false if not stopped by an instance of MovementHandler
	 */
	public static boolean isStopped(final Entity entity) {
		return HANDLERS.stream().anyMatch(h -> h.entity == entity && h.stopped);
	}

	public boolean isStopped() {
		return stopped;
	}

	/**
	 * Resets all instances of MovementHandler
	 */
	public static void resetAll() {
		for (Iterator<MovementHandler> iterator = HANDLERS.iterator(); iterator.hasNext();) {
			MovementHandler handler = iterator.next();
			handler.reset(false);
			iterator.remove();
		}
	}

	/**
	 * Using an entity and ability, the MovementHandler associated with both
	 * will be found.
	 *
	 * @param entity the entity in question of being stopped
	 * @param ability the ability in question of doing the stopping
	 * @return null if no MovementHandler instance with entity and ability
	 *         found.
	 */
	public static MovementHandler getFromEntityAndAbility(final Entity entity, final CoreAbility ability) {
		return get((e, a) -> e == entity && a == ability);
	}

	public static MovementHandler getFromEntity(final Entity entity) {
		return get((e, a) -> e == entity);
	}

	public static MovementHandler get(final BiPredicate<Entity, CoreAbility> predicate) {
		return HANDLERS.stream().filter(h -> predicate.test(h.getEntity(), h.getAbility())).findAny().orElse(null);
	}
}
