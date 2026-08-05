package com.projectkorra.projectkorra.board;

import java.util.List;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.configuration.ConfigManager;

public class RpgAbilityBoard extends AbstractAbilityBoard {

	private static final int MAX_OBJECTIVE_LINES = 4;

	private final BoardLine dividerLine;
	private final BoardLine objectivesHeaderLine;
	private final BoardLine[] objectiveLines = new BoardLine[MAX_OBJECTIVE_LINES];
	private int activeObjectiveLineCount;

	private final String dividerText;
	private final String objectivesHeaderText;

	public RpgAbilityBoard(final BendingPlayer bendingPlayer) {
		super(BoardType.RPG, bendingPlayer, "rpgboard", "Board.RPG.Title");

		for (int slot = 1; slot <= 9; slot++) {
			registerHotbarLine(slot, new BoardLine(this.scoreboard, this.objective, "rpgslot" + slot, -slot, slot - 1));
		}

		this.dividerLine = new BoardLine(this.scoreboard, this.objective, "rpgdiv", -10, 9);
		this.objectivesHeaderLine = new BoardLine(this.scoreboard, this.objective, "rpgobjhdr", -11, 10);
		this.dividerLine.remove();
		this.objectivesHeaderLine.remove();

		this.dividerText = colorize(ConfigManager.languageConfig.get().getString("Board.RPG.Divider"));
		this.objectivesHeaderText = colorize(ConfigManager.languageConfig.get().getString("Board.RPG.ObjectivesHeader"));
	}

	@Override
	public void updateAll() {
		super.updateAll();
		updateObjectives();
	}

	private void updateObjectives() {
		final List<String> resolvedLines = BendingBoardManager.resolveObjectiveLines(this.player, MAX_OBJECTIVE_LINES);
		final int desiredCount = Math.min(MAX_OBJECTIVE_LINES, resolvedLines.size());

		if (desiredCount == 0) {
			for (int i = 0; i < this.activeObjectiveLineCount; i++) {
				if (this.objectiveLines[i] != null) {
					this.objectiveLines[i].remove();
				}
			}
			this.activeObjectiveLineCount = 0;
			this.objectivesHeaderLine.remove();
			this.dividerLine.remove();
			return;
		}

		this.dividerLine.setText(this.dividerText);
		this.objectivesHeaderLine.setText(this.objectivesHeaderText);

		for (int i = 0; i < desiredCount; i++) {
			if (this.objectiveLines[i] == null) {
				this.objectiveLines[i] = new BoardLine(this.scoreboard, this.objective, "rpgobj" + i, -12 - i, 11 + i);
			}
			this.objectiveLines[i].setText(resolvedLines.get(i));
		}

		for (int i = desiredCount; i < this.activeObjectiveLineCount; i++) {
			if (this.objectiveLines[i] != null) {
				this.objectiveLines[i].remove();
			}
		}

		this.activeObjectiveLineCount = desiredCount;
	}
}