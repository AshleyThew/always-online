package me.dablakbandit.ao.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import me.dablakbandit.ao.hybrid.AlwaysOnline;
import me.dablakbandit.ao.utils.UUIDUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

public class VelocityCommand implements SimpleCommand {

    private final VelocityLoader ao;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacy('&');

    public VelocityCommand(VelocityLoader ao) {
        this.ao = ao;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("alwaysonline.usage");
    }

    @Override
    public void execute(final Invocation invocation) {
        execute(invocation.source(), invocation.arguments());
    }

    public void execute(CommandSource sender, String[] args) {
        if (args.length <= 0) {
            this.displayHelp(sender);
        } else {
            AlwaysOnline alwaysOnline = ao.getAOInstance();
            switch (args[0].toLowerCase()) {
                case "toggle":
                    alwaysOnline.toggleOfflineMode();
                    sendMessage(sender,
                            ChatColor.GOLD + "Mojang offline mode is now "
                                    + ((alwaysOnline.getOfflineMode() ? ChatColor.GREEN + "enabled"
                                            : ChatColor.RED + "disabled"))
                                    + ChatColor.GOLD + "!");
                    if (!alwaysOnline.getOfflineMode()) {
                        sendMessage(sender,
                                ChatColor.GOLD + "AlwaysOnline will now treat the mojang servers as being online.");
                    } else {
                        sendMessage(sender, ChatColor.GOLD
                                + "AlwaysOnline will no longer treat the mojang servers as being online.");
                    }
                    break;
                case "disable":
                    alwaysOnline.setCheckSessionStatus(false);
                    sendMessage(sender, ChatColor.GOLD
                            + "AlwaysOnline has been disabled! AlwaysOnline will no longer check to see if the session server is offline.");
                    break;
                case "enable":
                    alwaysOnline.setCheckSessionStatus(true);
                    sendMessage(sender, ChatColor.GOLD
                            + "AlwaysOnline has been enabled! AlwaysOnline will now check to see if the session server is offline.");
                    break;
                case "reload":
                    this.ao.alwaysOnline.reload();
                    sendMessage(sender, ChatColor.GOLD + "AlwaysOnline has been reloaded!");
                    break;
                case "debug":
                    this.ao.alwaysOnline.printDebugInformation();
                    break;
                case "resetcache":
                    this.ao.alwaysOnline.database.resetCache();
                    sendMessage(sender, ChatColor.GREEN + "AlwaysOnline cache reset'd");
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
                                sendMessage(sender,
                                        ChatColor.GREEN + "AlwaysOnline " + uuidString + " is not a valid UUID");
                                break;
                            }
                        } else {
                            // No UUID provided, try to get it from database
                            uuid = this.ao.alwaysOnline.database.getUUID(username);
                            if (uuid == null) {
                                sendMessage(sender,
                                        ChatColor.GREEN + "AlwaysOnline player " + username + " not found in database");
                                break;
                            }
                        }

                        if (!this.ao.alwaysOnline.database.isValidIP(ip)) {
                            sendMessage(sender, ChatColor.GREEN + "AlwaysOnline " + ip + " is not a valid IP");
                            break;
                        }

                        this.ao.alwaysOnline.database.updatePlayer(username, ip, uuid);
                        sendMessage(sender, ChatColor.GREEN + "AlwaysOnline updated " + username + " to " + ip);
                    } else {
                        sendMessage(sender, ChatColor.GREEN + "Usage: /alwaysonline updateip <username> <ip> [uuid]");
                    }
                    break;
                default:
                    this.displayHelp(sender);
                    break;
            }
            this.ao.alwaysOnline.saveState();

        }

    }

    private void displayHelp(CommandSource source) {

        sendMessage(source,
                ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "----------" + ChatColor.GOLD + "["
                        + ChatColor.DARK_GREEN + "AlwaysOnline " + ChatColor.GRAY + ao.getVersion() + ChatColor.GOLD
                        + "]" + ChatColor.GOLD + ""
                        + ChatColor.STRIKETHROUGH + "----------");
        sendMessage(source, ChatColor.GOLD + "/alwaysonline toggle - " + ChatColor.DARK_GREEN
                + "Toggles between mojang online mode");
        sendMessage(source, ChatColor.GOLD + "/alwaysonline enable - " + ChatColor.DARK_GREEN + "Enables the plugin");
        sendMessage(source, ChatColor.GOLD + "/alwaysonline disable - " + ChatColor.DARK_GREEN + "Disables the plugin");
        sendMessage(source,
                ChatColor.GOLD + "/alwaysonline reload - " + ChatColor.DARK_GREEN + "Reloads the configuration file");
        sendMessage(source,
                ChatColor.GOLD + "/alwaysonline resetcache - " + ChatColor.DARK_GREEN + "Clear database cache");
        sendMessage(source, ChatColor.GOLD + "/alwaysonline updateip <username> <ip> [uuid] - " + ChatColor.DARK_GREEN
                + "Update a users ip in the database");
        sendMessage(source, ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "------------------------------");
    }

    private void sendMessage(CommandSource source, String message) {
        source.sendMessage(legacy.deserialize(message));
    }

}
