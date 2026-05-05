package com.projectkorra.projectkorra.board;

import java.util.List;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.configuration.ConfigManager;

public class RpgAbilityBoard extends AbstractAbilityBoard {

	private static final int MAX_OBJECTIVE_LINES = 4;

	private final BoardLine dividerLine;
	private final BoardLine objectivesHeaderLine;
	private final BoardLine[] objectiveLines = new BoardLine[MAX_OBJECTIVE_LINES];

	private final String dividerText;
	private final String objectivesHeaderText;

	public RpgAbilityBoard(final BendingPlayer bendingPlayer) {
		super(BoardType.RPG, bendingPlayer, "rpgboard", "Board.RPG.Title");

		for (int slot = 1; slot <= 9; slot++) {
			registerHotbarLine(slot, new BoardLine(this.scoreboard, this.objective, "rpgslot" + slot, -slot, slot - 1));
		}

		this.dividerLine = new BoardLine(this.scoreboard, this.objective, "rpgdiv", -10, 9);
		this.objectivesHeaderLine = new BoardLine(this.scoreboard, this.objective, "rpgobjhdr", -11, 10);
		for (int i = 0; i < MAX_OBJECTIVE_LINES; i++) {
			this.objectiveLines[i] = new BoardLine(this.scoreboard, this.objective, "rpgobj" + i, -12 - i, 11 + i);
		}

		this.dividerText = colorize(ConfigManager.languageConfig.get().getString("Board.RPG.Divider"));
		this.objectivesHeaderText = colorize(ConfigManager.languageConfig.get().getString("Board.RPG.ObjectivesHeader"));
	}

	@Override
	public void updateAll() {
		super.updateAll();
		updateObjectives();
	}

	private void updateObjectives() {
		this.dividerLine.setText(this.dividerText);
		this.objectivesHeaderLine.setText(this.objectivesHeaderText);

		final List<String> resolvedLines = BendingBoardManager.resolveObjectiveLines(this.player, MAX_OBJECTIVE_LINES);
		for (int i = 0; i < MAX_OBJECTIVE_LINES; i++) {
			this.objectiveLines[i].setText(i < resolvedLines.size() ? resolvedLines.get(i) : "");
		}
	}
}