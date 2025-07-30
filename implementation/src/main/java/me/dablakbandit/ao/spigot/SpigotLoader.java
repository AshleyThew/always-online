package me.dablakbandit.ao.spigot;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.databases.Database;
import me.dablakbandit.ao.databases.MySQLDatabase;
import me.dablakbandit.ao.spigot.authservices.NMSAuthSetup;
import me.dablakbandit.ao.spigot.metrics.Metrics;
import me.dablakbandit.ao.utils.UUIDUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import me.dablakbandit.ao.hybrid.AlwaysOnline;

public class SpigotLoader extends JavaPlugin implements NativeExecutor {

	public final AlwaysOnline alwaysOnline = new AlwaysOnline(this);

	@Override
	public void onEnable() {

		if (!this.getServer().getOnlineMode()) {
			this.getLogger()
					.info("This server is running in offline mode, so this plugin will have no use on this server!");
			this.getLogger().info(
					"If you are running bungeecord, please put AlwaysOnline in the bungeecord plugins directory.");
			this.getLogger().info("If you are not running bungeecord, then please remove AlwaysOnline.");
			this.getPluginLoader().disablePlugin(this);
			return;
		}

		this.alwaysOnline.reload();

		try {
			this.getLogger().info("Setting up NMS authentication service...");
			NMSAuthSetup.setUp(this);
		} catch (Exception e) {
			e.printStackTrace();
			this.getLogger().severe(
					"Failed to override the authentication handler. Due to possible security risks, the server will now shut down.");
			this.getLogger()
					.severe("If this issue persists, please contact the author (" + this.getDescription().getAuthors()
							+ ") and remove " + this.getDescription().getName() + " from your server temporarily.");
			this.getServer().shutdown();
		}

		Metrics metrics = new Metrics(this, 15201);
		Database database = alwaysOnline.getDatabase();
		String databaseType = "FlatFile";
		if (database instanceof MySQLDatabase) {
			databaseType = "MySQL";
		}
		String finalDatabaseType = databaseType;
		metrics.addCustomChart(new Metrics.SimplePie("database_type", () -> finalDatabaseType));
	}

	@Override
	public void onDisable() {
		this.alwaysOnline.disable();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (args.length == 0) {
			this.displayHelp(sender);
		} else {
			String pluginName = this.getDescription().getName();
			switch (args[0].toLowerCase()) {
				case "toggle":
					alwaysOnline.toggleOfflineMode();
					sender.sendMessage(ChatColor.GOLD + "Mojang offline mode is now "
							+ ((alwaysOnline.getOfflineMode() ? ChatColor.GREEN + "enabled"
									: ChatColor.RED + "disabled"))
							+ ChatColor.GOLD + "!");
					if (!alwaysOnline.getOfflineMode()) {
						sender.sendMessage(
								ChatColor.GOLD + pluginName + " will now treat the mojang servers as being online.");
					} else {
						sender.sendMessage(ChatColor.GOLD + pluginName
								+ " will no longer treat the mojang servers as being online.");
					}
					break;
				case "disable":
					alwaysOnline.setCheckSessionStatus(false);
					sender.sendMessage(ChatColor.GOLD + pluginName + " has been disabled! " + pluginName
							+ " will no longer check to see if the session server is offline.");
					break;
				case "enable":
					alwaysOnline.setCheckSessionStatus(true);
					sender.sendMessage(ChatColor.GOLD + pluginName + " has been enabled! " + pluginName
							+ " will now check to see if the session server is offline.");
					break;
				case "reload":
					// TODO Add support?
					sender.sendMessage(ChatColor.RED + "The reload command is not supported when running " + pluginName
							+ " with spigot.");
					break;
				case "debug":
					if (sender instanceof Player)
						sender.sendMessage(ChatColor.GREEN + "Check console for debug information");
					this.alwaysOnline.printDebugInformation();
					break;
				case "resetcache":
					this.alwaysOnline.database.resetCache();
					sender.sendMessage(ChatColor.GREEN + "AlwaysOnline cache reset'd");
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
								sender.sendMessage(
										ChatColor.GREEN + "AlwaysOnline " + uuidString + " is not a valid UUID");
								break;
							}
						} else {
							// No UUID provided, try to get it from database
							uuid = this.alwaysOnline.database.getUUID(username);
							if (uuid == null) {
								sender.sendMessage(
										ChatColor.GREEN + "AlwaysOnline player " + username + " not found in database");
								break;
							}
						}

						if (!this.alwaysOnline.database.isValidIP(ip)) {
							sender.sendMessage(ChatColor.GREEN + "AlwaysOnline " + ip + " is not a valid IP");
							break;
						}

						this.alwaysOnline.database.updatePlayer(username, ip, uuid);
						sender.sendMessage(ChatColor.GREEN + "AlwaysOnline updated " + username + " to " + ip);
					} else {
						sender.sendMessage(ChatColor.GREEN + "Usage: /alwaysonline updateip <username> <ip> [uuid]");
					}
					break;
				default:
					this.displayHelp(sender);
					break;
			}
			this.alwaysOnline.saveState();
		}
		return true;

	}

	private void displayHelp(CommandSender sender) {
		sender.sendMessage(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "----------" + ChatColor.GOLD + "["
				+ ChatColor.DARK_GREEN + "AlwaysOnline " + ChatColor.GRAY + this.getDescription().getVersion() + ""
				+ ChatColor.GOLD + "]" + ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "----------");
		sender.sendMessage(ChatColor.GOLD + "/alwaysonline toggle - " + ChatColor.DARK_GREEN
				+ "Toggles between mojang online mode");
		sender.sendMessage(ChatColor.GOLD + "/alwaysonline enable - " + ChatColor.DARK_GREEN + "Enables the plugin");
		sender.sendMessage(ChatColor.GOLD + "/alwaysonline disable - " + ChatColor.DARK_GREEN + "Disables the plugin");
		sender.sendMessage(
				ChatColor.GOLD + "/alwaysonline reload - " + ChatColor.DARK_GREEN + "Reloads the configuration file");
		sender.sendMessage(
				ChatColor.GOLD + "/alwaysonline resetcache - " + ChatColor.DARK_GREEN + "Clear database cache");
		sender.sendMessage(ChatColor.GOLD + "/alwaysonline updateip <username> <ip> [uuid] - " + ChatColor.DARK_GREEN
				+ "Update a users ip in the database");
		sender.sendMessage(ChatColor.GOLD + "" + ChatColor.STRIKETHROUGH + "------------------------------");
	}

	@Override
	public Object runAsyncRepeating(Runnable runnable, long delay, long period, TimeUnit timeUnit) {
		return this.getServer().getScheduler().runTaskTimerAsynchronously(this, runnable,
				(timeUnit.toSeconds(delay) * 20), (timeUnit.toSeconds(period) * 20)).getTaskId();
	}

	@Override
	public void cancelTask(Object taskID) {
		if (taskID instanceof Integer) {
			int id = (int) taskID;
			if (id != -1) {
				try {
					this.getServer().getScheduler().cancelTask(id);
				} catch (Exception ignored) {

				}
			}
		}
	}

	@Override
	public void cancelAllOurTasks() {
		this.getServer().getScheduler().cancelTasks(this);
	}

	@Override
	public void unregisterAllListeners() {
		HandlerList.unregisterAll(this);
	}

	@Override
	public void log(Level level, String message) {
		this.getLogger().log(level, message);
	}

	@Override
	public Path dataFolder() {
		return this.getDataFolder().toPath();
	}

	@Override
	public void disablePlugin() {
		this.getServer().getPluginManager().disablePlugin(this);
	}

	@Override
	public void registerListener() {
		this.getServer().getPluginManager().registerEvents(new AOListener(this), this);
	}

	@Override
	public void broadcastMessage(String message) {
		this.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
	}

	@Override
	public AlwaysOnline getAOInstance() {
		return this.alwaysOnline;
	}

	@Override
	public String getVersion() {
		return getDescription().getVersion();
	}

	@Override
	public void notifyOfflineMode(boolean offlineMode) {
		NMSAuthSetup.setOnlineMode(!offlineMode);
	}

}
