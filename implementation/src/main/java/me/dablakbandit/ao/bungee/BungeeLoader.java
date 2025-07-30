package me.dablakbandit.ao.bungee;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.bungee.metrics.Metrics;
import me.dablakbandit.ao.databases.Database;
import me.dablakbandit.ao.databases.MySQLDatabase;
import me.dablakbandit.ao.hybrid.AlwaysOnline;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Plugin;

public class BungeeLoader extends Plugin implements NativeExecutor {
	
	public final AlwaysOnline alwaysOnline = new AlwaysOnline(this);
	
	@Override
	public void onEnable(){
		this.alwaysOnline.reload();
		// Execute native setup
		this.getProxy().getPluginManager().registerCommand(this, new BungeeCommand(this));

		Metrics metrics = new Metrics(this, 15200);
		Database database = alwaysOnline.getDatabase();
		String databaseType = "FlatFile";
		if(database instanceof MySQLDatabase){
			databaseType = "MySQL";
		}
		String finalDatabaseType = databaseType;
		metrics.addCustomChart(new Metrics.SimplePie("database_type", () -> finalDatabaseType));
	}
	
	@Override
	public void onDisable(){
		this.alwaysOnline.disable();
	}
	
	@Override
	public Object runAsyncRepeating(Runnable runnable, long delay, long period, TimeUnit timeUnit){
		return this.getProxy().getScheduler().schedule(this, runnable, delay, period, timeUnit).getId();
	}

	@Override
	public void cancelTask(Object taskID) {
		if (taskID instanceof Integer) {
			int id = (int) taskID;
			if(id != -1) {
				try {
					this.getProxy().getScheduler().cancel(id);
				} catch (Exception e) {

				}
			}
		}
	}
	
	@Override
	public void cancelAllOurTasks(){
		this.getProxy().getScheduler().cancel(this);
	}
	
	@Override
	public void unregisterAllListeners(){
		this.getProxy().getPluginManager().unregisterListeners(this);
	}
	
	@Override
	public void log(Level level, String message){
		this.getLogger().log(level, message);
	}
	
	@Override
	public Path dataFolder(){
		return this.getDataFolder().toPath();
	}
	
	@Override
	public void disablePlugin(){
		// Bungeecord not supported...
	}
	
	@Override
	public void registerListener(){
		this.getProxy().getPluginManager().registerListener(this, new BungeeListener(this));
	}
	
	@Override
	public void broadcastMessage(String message){
		this.getProxy().broadcast(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));
	}
	
	@Override
	public AlwaysOnline getAOInstance(){
		return this.alwaysOnline;
	}

	@Override
	public String getVersion() {
		return getDescription().getVersion();
	}

	@Override
	public void notifyOfflineMode(boolean offlineMode) {

	}
}
