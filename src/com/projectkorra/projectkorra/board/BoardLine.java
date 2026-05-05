package com.projectkorra.projectkorra.board;

import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class BoardLine {

	private static final char[] CHAT_CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private final Scoreboard board;
	private final Objective objective;
	private final Team team;
	private final String entry;
	private int score;

	public BoardLine(final Scoreboard board, final Objective objective, final String teamName, final int score, final int entryIndex) {
		this.board = board;
		this.objective = objective;
		this.score = score;
		this.team = board.getTeam(teamName) == null ? board.registerNewTeam(teamName) : board.getTeam(teamName);
		this.entry = createEntry(entryIndex);
		if (!this.team.getEntries().contains(this.entry)) {
			this.team.addEntry(this.entry);
		}
	}

	private static String createEntry(final int entryIndex) {
		final char primary = CHAT_CHARS[Math.floorMod(entryIndex, CHAT_CHARS.length)];
		final char secondary = CHAT_CHARS[Math.floorMod(entryIndex / CHAT_CHARS.length, CHAT_CHARS.length)];
		return "§" + primary + "§" + secondary;
	}

	public void update(final String prefix, final String suffix) {
		this.team.setPrefix(prefix);
		this.team.setSuffix(suffix);
		this.objective.getScore(this.entry).setScore(this.score);
	}

	public void setText(final String text) {
		update("", text);
	}

	public void clearText() {
		update("", "");
	}

	public void remove() {
		clearText();
		this.board.resetScores(this.entry);
	}

	public void setScore(final int score) {
		this.score = score;
		this.objective.getScore(this.entry).setScore(this.score);
	}
}