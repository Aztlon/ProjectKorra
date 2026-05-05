package com.projectkorra.projectkorra.board;

import java.util.List;

import org.bukkit.entity.Player;

public interface AbilityBoardResolver {

	boolean shouldUseRpgBoard(Player player);

	List<String> resolveObjectiveLines(Player player, int maxLines);
}