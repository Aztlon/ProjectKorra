package com.projectkorra.projectkorra.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projectkorra.projectkorra.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.Element.ElementType;
import com.projectkorra.projectkorra.Element.SubElement;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.logging.PkLang;

/**
 * Executor for /bending who. Extends {@link PKCommand}.
 */
public class WhoCommand extends PKCommand {
	/**
	 * Map storage of all ProjectKorra staffs' UUIDs and titles
	 */
	final Map<String, String> staff = new HashMap<String, String>(), playerInfoWords = new HashMap<String, String>();

	private final String databaseOverload, noPlayersOnline, playerOffline, playerUnknown;

	public WhoCommand() {
		super("who", "/bending who [Page/Player]", ConfigManager.languageConfig.get().getString("Commands.Who.Description"), new String[] { "who", "w" });

		this.databaseOverload = ConfigManager.languageConfig.get().getString("Commands.Who.DatabaseOverload");
		this.noPlayersOnline = ConfigManager.languageConfig.get().getString("Commands.Who.NoPlayersOnline");
		this.playerOffline = ConfigManager.languageConfig.get().getString("Commands.Who.PlayerOffline");
		this.playerUnknown = ConfigManager.languageConfig.get().getString("Commands.Who.PlayerUnknown");

		new BukkitRunnable() {
			@Override
			public void run() {
				final Map<String, String> updatedstaff = new HashMap<String, String>();
				try {

					// Create a URL for the desired page.
					final URLConnection url = new URL("https://raw.githubusercontent.com/ProjectKorra/ProjectKorra/master/src/staff.txt").openConnection();
					url.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");

					// Read all the text returned by the server.
					final BufferedReader in = new BufferedReader(new InputStreamReader(url.getInputStream(), StandardCharsets.UTF_8));
					String unparsed;
					while ((unparsed = in.readLine()) != null) {
						final String[] staffEntry = unparsed.split("/");
						if (staffEntry.length >= 2) {
							updatedstaff.put(staffEntry[0], ChatColor.translateAlternateColorCodes('&', staffEntry[1]));
						}
					}
					in.close();
					WhoCommand.this.staff.clear();
					WhoCommand.this.staff.putAll(updatedstaff);
				} catch (final SocketException e) {
					PkLang.info("Could not update staff list.");
				} catch (final IOException e) {
					e.printStackTrace();
				}
			}
		}.runTaskTimerAsynchronously(ProjectKorra.plugin, 0, 20 * 60 * 60);
	}

	@Override
	public void execute(final CommandSender sender, final List<String> args) {
		if (!this.hasPermission(sender) || !this.correctLength(sender, args.size(), 0, 1)) {
			return;
		} else if (args.size() == 1 && args.get(0).length() > 2) {
			this.whoPlayer(sender, args.get(0));
		} else if (args.size() == 0 || args.size() == 1) {
			int page = 1;
			if (args.size() == 1 && this.isNumeric(args.get(0))) {
				page = Integer.valueOf(args.get(0));
			}
			final List<String> players = new ArrayList<>();
			for (final Player player : Bukkit.getOnlinePlayers()) {
				if (sender instanceof Player && !((Player) sender).canSee(player)) {
					continue;
				}
				
				final String playerName = player.getName();
				String result = "";
				BendingPlayer bp = BendingPlayer.getBendingPlayer(playerName);

				for (final Element element : bp.getElements()) {
					if (result == "") {
						result = ChatColor.WHITE + playerName + " - " + (((!bp.isElementToggled(element) || !bp.isToggled()) ? element.getColor() + "" + ChatColor.STRIKETHROUGH : element.getColor()) + element.getName().substring(0, 1));
					} else {
						result = result + ChatColor.WHITE + " | " + (((!bp.isElementToggled(element) || !bp.isToggled()) ? element.getColor() + "" + ChatColor.STRIKETHROUGH : element.getColor()) + element.getName().substring(0, 1));
					}
				}
				if (this.staff.containsKey(player.getUniqueId().toString())) {
					if (result == "") {
						result = ChatColor.WHITE + playerName + " | " + this.staff.get(player.getUniqueId().toString());
					} else {
						result = result + ChatColor.WHITE + " | " + this.staff.get(player.getUniqueId().toString());
					}
				}
				if (result == "") {
					result = ChatColor.WHITE + playerName;
				}
				players.add(result);
			}
			if (players.isEmpty()) {
				ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.noPlayersOnline);
			} else {
				boolean firstMessage = true;

				for (final String s : this.getPage(players, ChatColor.GOLD + "Players:", page, true)) {
					if (firstMessage) {
						ChatUtil.sendBrandingMessage(sender, s);
						firstMessage = false;
					} else {
						sender.sendMessage(s);
					}
				}
			}
		}
	}

	/**
	 * Sends information on the given player to the CommandSender.
	 *
	 * @param sender The CommandSender to display the information to
	 * @param playerName The Player to look up
	 */
	private void whoPlayer(final CommandSender sender, final String playerName) {
		final OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
		if (!player.isOnline() && !player.hasPlayedBefore()) {
			ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.playerUnknown.replace("{target}", playerName));
			return;
		}

		//If they are actually offline OR they are vanished
		boolean offline = !player.isOnline() || (sender instanceof Player && player instanceof Player && !((Player) sender).canSee((Player) player));
		if (offline) {
			ChatUtil.sendBrandingMessage(sender, ChatColor.RED + this.playerOffline.replace("{target}", playerName));
		}

		BendingPlayer.getOrLoadOfflineAsync(player).thenAccept(bPlayer -> {
			if (!(bPlayer instanceof BendingPlayer)) { //Uncache after 30s
				bPlayer.uncacheAfter(30_000);
			}

			sender.sendMessage(player.getName() + (offline ? ChatColor.RESET + " (Offline)" : ""));
			if (bPlayer.hasElement(Element.AIR)) {
				if (bPlayer.isElementToggled(Element.AIR)) {
					sender.sendMessage(Element.AIR.getColor() + "- Airbender");
				} else {
					sender.sendMessage(Element.AIR.getColor() + "" + ChatColor.STRIKETHROUGH + "- Airbender");
				}

				if (bPlayer.hasSubElement(Element.FLIGHT)) {
					sender.sendMessage(bPlayer.canUseFlight() ? Element.FLIGHT.getColor() + "    Can Fly" : Element.FLIGHT.getColor() + "    Learning Flight");
				}
				if (bPlayer.hasSubElement(Element.SPIRITUAL)) {
					sender.sendMessage(bPlayer.canUseSpiritualProjection() ? Element.SPIRITUAL.getColor() + "    Can use Spiritual Projection" : Element.SPIRITUAL.getColor() + "    Learning Spiritual Projection");
				}
				if (bPlayer.hasSubElement(Element.SUFFOCATION)) {
					sender.sendMessage(bPlayer.canUseSuffocation() ? Element.SUFFOCATION.getColor() + "    Can Suffocate" : Element.SUFFOCATION.getColor() + "    Learning Suffocation");
				}
				for (final SubElement se : Element.getAddonSubElements(Element.AIR)) {
					if (bPlayer.hasSubElement(se)) {
						sender.sendMessage(bPlayer.canUseSubElement(se) ? se.getColor() + "    Can " + (!se.getType().equals(ElementType.NO_SUFFIX) ? "" : "use ") + se.getName() + se.getType().getBend() : se.getColor() + "    Learning " + se.getName() + se.getType().getBend());
					}
				}
			}

			if (bPlayer.hasElement(Element.WATER)) {
				if (bPlayer.isElementToggled(Element.WATER)) {
					sender.sendMessage(Element.WATER.getColor() + "- Waterbender");
				} else {
					sender.sendMessage(Element.WATER.getColor() + "" + ChatColor.STRIKETHROUGH + "- Waterbender");
				}

				if (bPlayer.hasSubElement(Element.PLANT)) {
					sender.sendMessage(bPlayer.canPlantbend() ? Element.PLANT.getColor() + "    Can Plantbend" : Element.PLANT.getColor() + "    Learning Plantbending");
				}
				if (bPlayer.hasSubElement(Element.BLOOD)) {
					if (bPlayer.hasSubElement(Element.DAY_BLOOD)) {
						sender.sendMessage(bPlayer.canBloodbendAtAnytime() ? Element.BLOOD.getColor() + "    Can Bloodbend anytime" : Element.BLOOD.getColor() + "    Learning Daytime Bloodbending");
					} else {
						sender.sendMessage(bPlayer.canBloodbend() ? Element.BLOOD.getColor() + "    Can Bloodbend" : Element.BLOOD.getColor() + "    Learning Bloodbending");
					}
				}
				if (bPlayer.hasSubElement(Element.ICE)) {
					sender.sendMessage(bPlayer.canIcebend() ? Element.ICE.getColor() + "    Can Icebend" : Element.ICE.getColor() + "    Learning Icebending");
				}
				if (bPlayer.hasSubElement(Element.HEALING)) {
					sender.sendMessage(bPlayer.canWaterHeal() ? Element.HEALING.getColor() + "    Can Heal" : Element.HEALING.getColor() + "    Learning Healing");
				}
				for (final SubElement se : Element.getAddonSubElements(Element.WATER)) {
					if (bPlayer.hasSubElement(se)) {
						sender.sendMessage(bPlayer.canUseSubElement(se) ? se.getColor() + "    Can " + (!se.getType().equals(ElementType.NO_SUFFIX) ? "" : "use ") + se.getName() + se.getType().getBend() : se.getColor() + "    Learning " + se.getName() + se.getType().getBend());
					}
				}
			}

			if (bPlayer.hasElement(Element.EARTH)) {
				if (bPlayer.isElementToggled(Element.EARTH)) {
					sender.sendMessage(Element.EARTH.getColor() + "- Earthbender");
				} else {
					sender.sendMessage(Element.EARTH.getColor() + "" + ChatColor.STRIKETHROUGH + "- Earthbender");
				}

				if (bPlayer.hasSubElement(Element.METAL)) {
					sender.sendMessage(bPlayer.canMetalbend() ? Element.METAL.getColor() + "    Can Metalbend" : Element.METAL.getColor() + "    Learning Metalbending");
				}
				if (bPlayer.hasSubElement(Element.LAVA)) {
					sender.sendMessage(bPlayer.canLavabend() ? Element.LAVA.getColor() + "    Can Lavabend" : Element.LAVA.getColor() + "    Learning Lavabending");
				}
				if (bPlayer.hasSubElement(Element.SAND)) {
					sender.sendMessage(bPlayer.canSandbend() ? Element.SAND.getColor() + "    Can Sandbend" : Element.SAND.getColor() + "    Learning Sandbending");
				}
				for (final SubElement se : Element.getAddonSubElements(Element.EARTH)) {
					if (bPlayer.hasSubElement(se)) {
						sender.sendMessage(bPlayer.canUseSubElement(se) ? se.getColor() + "    Can " + (!se.getType().equals(ElementType.NO_SUFFIX) ? "" : "use ") + se.getName() + se.getType().getBend() : se.getColor() + "    Learning " + se.getName() + se.getType().getBend());
					}
				}
			}

			if (bPlayer.hasElement(Element.FIRE)) {
				if (bPlayer.isElementToggled(Element.FIRE)) {
					sender.sendMessage(Element.FIRE.getColor() + "- Firebender");
				} else {
					sender.sendMessage(Element.FIRE.getColor() + "" + ChatColor.STRIKETHROUGH + "- Firebender");
				}

				if (bPlayer.hasSubElement(Element.COMBUSTION)) {
					sender.sendMessage(bPlayer.canCombustionbend() ? Element.COMBUSTION.getColor() + "    Can Combustionbend" : Element.COMBUSTION.getColor() + "    Learning Combustionbending");
				}
				if (bPlayer.hasSubElement(Element.LIGHTNING)) {
					sender.sendMessage(bPlayer.canLightningbend() ? Element.LIGHTNING.getColor() + "    Can Lightningbend" : Element.LIGHTNING.getColor() + "    Learning Lightningbending");
				}
				if (bPlayer.hasSubElement(Element.BLUE_FIRE)) {
					sender.sendMessage(bPlayer.canUseBlueFire() ? Element.BLUE_FIRE.getColor() + "    Can use Blue Fire" : Element.BLUE_FIRE.getColor() + "    Learning Blue Fire");
				}
				for (final SubElement se : Element.getAddonSubElements(Element.FIRE)) {
					if (bPlayer.hasSubElement(se)) {
						sender.sendMessage(bPlayer.canUseSubElement(se) ? se.getColor() + "    Can " + (!se.getType().equals(ElementType.NO_SUFFIX) ? "" : "use ") + se.getName() + se.getType().getBend() : se.getColor() + "    Learning " + se.getName() + se.getType().getBend());
					}
				}
			}

			if (bPlayer.hasElement(Element.NON)) {
				if (bPlayer.isElementToggled(Element.NON)) {
					sender.sendMessage(Element.NON.getColor() + "- Nonbender");
				} else {
					sender.sendMessage(Element.NON.getColor() + "" + ChatColor.STRIKETHROUGH + "- Nonbender");
				}

				if (bPlayer.hasSubElement(Element.CHI)) {
					sender.sendMessage(bPlayer.canUseSubElement(Element.CHI) ? Element.CHI.getColor() + "    Chiblocker" : Element.CHI.getColor() + "    Learning Chiblocking");
				}
				if (bPlayer.hasSubElement(Element.WARRIOR)) {
					sender.sendMessage(bPlayer.canUseSubElement(Element.WARRIOR) ? Element.WARRIOR.getColor() + "    Warrior" : Element.WARRIOR.getColor() + "    Learning Warriorship");
				}
				if (bPlayer.hasSubElement(Element.ARCHER)) {
					sender.sendMessage(bPlayer.canUseSubElement(Element.ARCHER) ? Element.ARCHER.getColor() + "    Archer" : Element.ARCHER.getColor() + "    Learning Archery");
				}
				for (final SubElement se : Element.getAddonSubElements(Element.CHI)) {
					if (bPlayer.hasSubElement(se)) {
						sender.sendMessage(bPlayer.canUseSubElement(se) ? se.getColor() + "    Can " + (!se.getType().equals(ElementType.NO_SUFFIX) ? "" : "use ") + se.getName() + se.getType().getBend() : se.getColor() + "    Learning " + se.getName() + se.getType().getBend());
					}
				}
			}

			for (final Element element : Element.getAddonElements()) {
				if (bPlayer.hasElement(element)) {
					sender.sendMessage(element.getColor() + "" + (bPlayer.isElementToggled(element) ? "" : ChatColor.STRIKETHROUGH) + "- " + element.getName() + (element.getType() != null ? element.getType().getBender() : ""));

					for (final SubElement subelement : Element.getSubElements(element)) {
						if (bPlayer.hasSubElement(subelement)) {
							sender.sendMessage(bPlayer.canUseSubElement(subelement) ? subelement.getColor() + "    Can " + (!subelement.getType().equals(ElementType.NO_SUFFIX) ? "" : "use ") + subelement.getName() + subelement.getType().getBend() : subelement.getColor() + "    Learning " + subelement.getName() + subelement.getType().getBend());
						}
					}
				}
			}

			final UUID uuid = player.getUniqueId();

			sender.sendMessage("Abilities: ");
			for (int i = 1; i <= 9; i++) {
				final String ability = bPlayer.getAbilities().get(i);
				final CoreAbility coreAbil = CoreAbility.getAbility(ability);
				if (coreAbil == null) continue;

				sender.sendMessage(i + " - " + coreAbil.getElement().getColor() + ability);

			}

			if (playerName.equalsIgnoreCase("Aztl")) {
				sender.sendMessage(ChatColor.BLUE + "Avatarverse Owner");
			}
			if (this.staff.containsKey(uuid.toString())) {
				sender.sendMessage(this.staff.get(uuid.toString()));
			}
		});
	}

	@Override
	protected List<String> getTabCompletion(final CommandSender sender, final List<String> args) {
		if (args.size() >= 1 || !sender.hasPermission("bending.command.who")) {
			return new ArrayList<String>();
		}

		return getOnlinePlayerNames(sender);
	}
}
