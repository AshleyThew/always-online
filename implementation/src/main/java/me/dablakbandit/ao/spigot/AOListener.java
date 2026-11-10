package me.dablakbandit.ao.spigot;

import me.dablakbandit.ao.config.AlwaysOnlineConfig;
import me.dablakbandit.ao.databases.Skin;
import me.dablakbandit.ao.utils.Placeholders;
import me.dablakbandit.ao.utils.UUIDUtil;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class AOListener implements Listener {

	private final Pattern pat = Pattern.compile("^[a-zA-Z0-9_-]{3,16}$");    // The regex to verify usernames;

	private final SpigotLoader spigotLoader;
	private String MOTD;

	public AOListener(SpigotLoader spigotLoader) {
		this.spigotLoader = spigotLoader;
		String motd = this.spigotLoader.alwaysOnline.config.messages.motdOffline;
		this.MOTD = AlwaysOnlineConfig.disabled(motd) ? null : ChatColor.translateAlternateColorCodes('&', motd);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onMOTD(ServerListPingEvent event) {
		if (spigotLoader.getAOInstance().getOfflineMode() && this.MOTD != null) event.setMotd(this.MOTD);
	}

	// Low priority so that we can go first. ignoreCancelled is set to false to prevent some security concern.
	@EventHandler(priority = EventPriority.LOWEST)
	public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
		if (spigotLoader.getAOInstance().getOfflineMode()) {
			String username = event.getName();
			String ip = event.getAddress().getHostAddress();
			// Floodgate (Bedrock) players sign in through Xbox, not mojang, so the outage does not affect them.
			if (UUIDUtil.isFloodgateId(event.getUniqueId())) {
				return;
			}
			if (!this.validate(username)) {
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.kickMessage(this.spigotLoader.alwaysOnline.config.messages.kickInvalid, username, ip, null));
				return;
			}
			String lastIP = this.spigotLoader.alwaysOnline.database.getIP(username);
			if (lastIP == null) {
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.kickMessage(this.spigotLoader.alwaysOnline.config.messages.kickNew, username, ip, null));
			} else {
				if (!lastIP.equals(ip)) {
					event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, this.kickMessage(this.spigotLoader.alwaysOnline.config.messages.kickIp, username, ip, lastIP));
				} else {
					this.spigotLoader.log(Level.INFO, username + " was successfully authenticated while mojang servers were offline. Connecting IP is " + ip + " and the last authenticated known IP was " + lastIP);
					// Make sure they log in as themselves, not as a fresh offline-mode player with an empty
					// inventory (the network hook usually has done this already, but it can miss a connection),
					// and with the skin saved on their last normal join instead of none.
					UUID stored = this.spigotLoader.alwaysOnline.database.getUUID(username);
					UUID newId = stored != null && !stored.equals(event.getUniqueId()) ? stored : null;
					Skin skin = this.spigotLoader.alwaysOnline.database.getSkin(username);
					if (PaperProfiles.apply(event, newId, skin) && this.spigotLoader.alwaysOnline.isDebug()) {
						this.spigotLoader.log(Level.INFO, "Restored the login profile for " + username + (newId != null ? ": UUID " + newId + " (was " + event.getUniqueId() + ")" : "") + (skin != null ? ", with saved skin" : ""));
					}
				}
			}
		}
	}

	@EventHandler
	public void onPostLogin(PlayerJoinEvent event) {
		if (!spigotLoader.getAOInstance().getOfflineMode()) {
			final String username = event.getPlayer().getName();
			final String ip = event.getPlayer().getAddress().getAddress().getHostAddress();
			final UUID uuid = event.getPlayer().getUniqueId();
			final Skin skin = PaperProfiles.skinOf(event.getPlayer());
			this.spigotLoader.getServer().getScheduler().runTaskAsynchronously(this.spigotLoader, new Runnable() {
				@Override
				public void run() {
					AOListener.this.spigotLoader.alwaysOnline.database.updatePlayer(username, ip, uuid, skin);
				}
			});
		}
	}

	private String kickMessage(String message, String username, String ip, String lastIp) {
		return ChatColor.translateAlternateColorCodes('&', Placeholders.apply(message, username, ip, lastIp));
	}

	/**
	 * Validate username with regular expression
	 *
	 * @param username username for validation
	 * @return true valid username, false invalid username
	 */
	public boolean validate(String username) {
		return username != null && pat.matcher(username).matches();
	}

}
