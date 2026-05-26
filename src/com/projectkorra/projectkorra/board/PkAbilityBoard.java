package com.projectkorra.projectkorra.board;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.configuration.ConfigManager;

import net.md_5.bungee.api.ChatColor;

public class PkAbilityBoard extends AbstractAbilityBoard {

	private static final int HOTBAR_SLOT_COUNT = 9;
	private static final int MISC_SEPARATOR_SCORE = -(HOTBAR_SLOT_COUNT + 1);
	private static final int FIRST_MISC_SCORE = MISC_SEPARATOR_SCORE - 1;

	private static final class MiscEntry {

		private final int slotId;
		private final BoardLine line;
		private Optional<MiscEntry> next = Optional.empty();
		private Optional<MiscEntry> prev = Optional.empty();

		private MiscEntry(final int slotId, final BoardLine line) {
			this.slotId = slotId;
			this.line = line;
		}
	}

	private final Map<String, MiscEntry> misc = new HashMap<>();
	private final Queue<Integer> miscSlotIds = new LinkedList<>();
	private final BoardLine miscSeparatorLine;
	private final String miscSeparator;
	private MiscEntry miscTail;

	public PkAbilityBoard(final BendingPlayer bendingPlayer) {
		super(BoardType.PK, bendingPlayer, "pkboard", "Board.Title");

		for (int slot = 1; slot <= HOTBAR_SLOT_COUNT; slot++) {
			registerHotbarLine(slot, new BoardLine(this.scoreboard, this.objective, "pkslot" + slot, -slot, slot - 1));
			this.miscSlotIds.add(slot - 1);
		}

		this.miscSeparator = colorize(ConfigManager.languageConfig.get().getString("Board.MiscSeparator"));
		this.miscSeparatorLine = new BoardLine(this.scoreboard, this.objective, "pkmiscsep", MISC_SEPARATOR_SCORE, HOTBAR_SLOT_COUNT);
		this.miscSeparatorLine.remove();
	}

	@Override
	public void updateMisc(final String name, final ChatColor color, final boolean cooldown) {
		if (!cooldown) {
			removeMisc(name);
			return;
		}

		if (this.misc.containsKey(name)) {
			return;
		}

		final Integer slotId = this.miscSlotIds.poll();
		if (slotId == null) {
			return;
		}

		final BoardLine line = new BoardLine(this.scoreboard, this.objective, "pkmisc" + slotId, FIRST_MISC_SCORE - slotId, HOTBAR_SLOT_COUNT + 1 + slotId);
		line.update(getMiscPadding(), color + "" + ChatColor.STRIKETHROUGH + name);

		final MiscEntry miscEntry = new MiscEntry(slotId, line);
		if (this.miscTail != null) {
			this.miscTail.next = Optional.of(miscEntry);
			miscEntry.prev = Optional.of(this.miscTail);
		}

		this.miscTail = miscEntry;
		this.misc.put(name, miscEntry);
		this.miscSeparatorLine.setText(this.miscSeparator);
	}

	private void removeMisc(final String name) {
		final MiscEntry removed = this.misc.remove(name);
		if (removed == null) {
			return;
		}

		removed.next.ifPresent(next -> next.prev = removed.prev);
		removed.prev.ifPresent(prev -> prev.next = removed.next);
		if (removed == this.miscTail) {
			this.miscTail = removed.prev.orElse(null);
		}

		removed.line.remove();
		this.miscSlotIds.add(removed.slotId);

		if (this.misc.isEmpty()) {
			this.miscSeparatorLine.remove();
		}
	}
}