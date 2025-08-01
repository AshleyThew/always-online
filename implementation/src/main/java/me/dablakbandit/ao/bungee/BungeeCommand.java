package me.dablakbandit.ao.bungee;

import me.dablakbandit.ao.hybrid.AlwaysOnline;
import me.dablakbandit.ao.utils.UUIDUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

import java.util.UUID;

public class BungeeCommand extends Command {

	private final BungeeLoader ao;

	public BungeeCommand(BungeeLoader ao) {
		super("alwaysonline", "alwaysonline.usage", "ao");
		this.ao = ao;
	}

	public void execute(CommandSender sender, String[] args) {
		if (args.length <= 0) {
			this.displayHelp(sender);
		} else {
			AlwaysOnline alwaysOnline = ao.getAOInstance();
			switch (args[0].toLowerCase()) {
				case "toggle":
					alwaysOnline.toggleOfflineMode();
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "Mojang offline mode is now " + ((alwaysOnline.getOfflineMode() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled")) + ChatColor.GOLD + "!"));
					if (!alwaysOnline.getOfflineMode()) {
						sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline will now treat the mojang servers as being online."));
					} else {
						sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline will no longer treat the mojang servers as being online."));
					}
					break;
				case "disable":
					alwaysOnline.setCheckSessionStatus(false);
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline has been disabled! AlwaysOnline will no longer check to see if the session server is offline."));
					break;
				case "enable":
					alwaysOnline.setCheckSessionStatus(true);
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline has been enabled! AlwaysOnline will now check to see if the session server is offline."));
					break;
				case "reload":
					this.ao.alwaysOnline.reload();
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline has been reloaded!"));
					break;
				case "debug":
					this.ao.alwaysOnline.printDebugInformation();
					break;
				case "resetcache":
					this.ao.alwaysOnline.database.resetCache();
					sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline cache reset'd"));
					break;
				case "updateip":
					if (args.length >= 3 && args.length <= 4) {
						String username = args[1];
						String ip = args[2];
						UUID uuid = null;

						// Check if UUID is provided as optional 4th parameter
						if (args.length == 4) {
							String uuidString = args[3];
							if (UUIDUtil.isValidUUID(uuidString)) {
								uuid = UUID.fromString(uuidString);
							} else {
								sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline " + uuidString + " is not a valid UUID"));
								return;
							}
						} else {
							// No UUID provided, try to get it from database
							uuid = this.ao.alwaysOnline.database.getUUID(username);
							if (uuid == null) {
								sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline player " + username + " not found in database"));
								return;
							}
						}

						if (!this.ao.alwaysOnline.database.isValidIP(ip)) {
							sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline " + ip + " is not a valid IP"));
							return;
						}

						this.ao.alwaysOnline.database.updatePlayer(username, ip, uuid);
						sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "AlwaysOnline updated " + username + " to " + ip));
					} else {
						sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "Usage: /alwaysonline updateip <username> <ip> [uuid]"));
					}
					break;
				default:
					this.displayHelp(sender);
					break;
			}
			this.ao.alwaysOnline.saveState();

		}

	}

	private void displayHelp(CommandSender sender) {
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "----------" + ChatColor.GOLD + "[" + ChatColor.DARK_GREEN + "AlwaysOnline " + ChatColor.GRAY + ao.getDescription().getVersion() + "" + ChatColor.GOLD + "]" + ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "----------"));
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "/alwaysonline toggle - " + ChatColor.DARK_GREEN + "Toggles between mojang online mode"));
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "/alwaysonline enable - " + ChatColor.DARK_GREEN + "Enables the plugin"));
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "/alwaysonline disable - " + ChatColor.DARK_GREEN + "Disables the plugin"));
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "/alwaysonline reload - " + ChatColor.DARK_GREEN + "Reloads the configuration file"));
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "/alwaysonline resetcache - " + ChatColor.DARK_GREEN + "Clear database cache"));
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "/alwaysonline updateip <username> <ip> [uuid] - " + ChatColor.DARK_GREEN + "Update a users ip in the database"));
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "------------------------------"));
	}

}
