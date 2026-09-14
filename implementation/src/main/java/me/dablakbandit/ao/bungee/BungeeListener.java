package me.dablakbandit.ao.bungee;

import me.dablakbandit.ao.config.AlwaysOnlineConfig;
import me.dablakbandit.ao.proxy.ProxyListener;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.UUID;

public class BungeeListener extends ProxyListener implements Listener {

	private final BungeeLoader bungeeLoader;

	public BungeeListener(BungeeLoader bungeeLoader) {
		super(bungeeLoader);
		this.bungeeLoader = bungeeLoader;
		String motd = this.bungeeLoader.getAOInstance().config.messages.motdOffline;
		this.MOTD = AlwaysOnlineConfig.disabled(motd) ? null : ChatColor.translateAlternateColorCodes('&', motd);
	}

	private void debug(String message) {
		if (bungeeLoader.getAOInstance().isDebug()) {
			bungeeLoader.getLogger().info("[AlwaysOnline-Debug] " + message);
		}
	}

	// A high priority to allow other plugins to go first
	@EventHandler(priority = 65)
	public void onPreLogin(PreLoginEvent event) {
		// Make sure it is not canceled
		if (event.isCancelled()) return;
		debug("PreLogin for " + event.getConnection().getName() + ", offlineMode=" + bungeeLoader.getAOInstance().getOfflineMode());
		if (bungeeLoader.getAOInstance().getOfflineMode()) {// Make sure we are in mojang offline mode
			// Verify if the name attempting to connect is even verified
			if (!this.validate(event.getConnection().getName())) {
				debug("Invalid username rejected at pre-login: " + event.getConnection().getName());
				event.setCancelReason(this.bungeeLoader.alwaysOnline.config.messages.kickInvalid);
				event.setCancelled(true);
				return;

			}
			// Initialize our hacky stuff
			InitialHandler handler = (InitialHandler) event.getConnection();
			// Get the connecting ip
			final String ip = handler.getAddress().getAddress().getHostAddress();
			// Get last known ip
			final String lastip = this.bungeeLoader.alwaysOnline.database.getIP(event.getConnection().getName());
			debug("PreLogin IP check for " + event.getConnection().getName() + ": current=" + ip + ", last=" + lastip);
			if (lastip == null) {// If null the player connecting is new
				event.setCancelReason(this.bungeeLoader.alwaysOnline.config.messages.kickNew);
				event.setCancelled(true);
				this.bungeeLoader.getLogger().info("Denied " + event.getConnection().getName() + " from logging in cause their ip [" + ip + "] has never connected to this server before!");
			} else {
				if (ip.equals(lastip)) {// If it matches set handler to offline mode, so it does not authenticate player
					// with mojang
					boolean onlineMode = handler.isOnlineMode();
					this.bungeeLoader.getLogger().info("Skipping session login for player " + event.getConnection().getName() + " [Connected ip: " + ip + ", Last ip: " + lastip + "]!");
					handler.setOnlineMode(false);
					UUID uuid = this.bungeeLoader.alwaysOnline.database.getUUID(event.getConnection().getName());
					debug("Setting offline UUID for " + event.getConnection().getName() + " to " + uuid);
					handler.setUniqueId(uuid);
					handler.setOnlineMode(onlineMode);
				} else {// Deny the player from joining
					this.bungeeLoader.getLogger().info("Denied " + event.getConnection().getName() + " from logging in cause their ip [" + ip + "] does not match their last ip!");
					handler.setOnlineMode(true);
					event.setCancelReason(this.bungeeLoader.alwaysOnline.config.messages.kickIp);
					event.setCancelled(true);
				}
			}
		}
	}

	@EventHandler(priority = -65)
	public void onLogin(LoginEvent event) {
		if (event.isCancelled()) return;
		debug("Login event for " + event.getConnection().getName() + ", offlineMode=" + bungeeLoader.getAOInstance().getOfflineMode());
		if (bungeeLoader.getAOInstance().getOfflineMode()) {// Make sure we are in mojang offline mode
			InitialHandler handler = (InitialHandler) event.getConnection();
			UUID uuid = this.bungeeLoader.alwaysOnline.database.getUUID(event.getConnection().getName());
			if (!uuid.equals(handler.getUniqueId())) {
				this.bungeeLoader.getLogger().info("Updating Login UUID for " + event.getConnection().getName() + " to " + uuid + "!");
				boolean onlineMode = handler.isOnlineMode();
				handler.setOnlineMode(false);
				handler.setUniqueId(uuid);
				handler.setOnlineMode(onlineMode);
			} else {
				this.bungeeLoader.getLogger().info("Login UUID for " + event.getConnection().getName() + " is already set to " + uuid + "!");
			}
		}
	}

	// Set priority to highest to almost guaranteed to have our MOTD displayed
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPing(ProxyPingEvent event) {
		debug("Proxy ping received, offlineMode=" + bungeeLoader.getAOInstance().getOfflineMode() + ", customMotd=" + (this.MOTD != null));
		if (bungeeLoader.getAOInstance().getOfflineMode() && this.MOTD != null) {
			ServerPing sp = event.getResponse();
			sp.setDescription(this.MOTD);
			event.setResponse(sp);
		}
	}

	@SuppressWarnings("deprecation")
	// Set priority to lowest since we'll be needing to go first
	@EventHandler(priority = -65)
	public void onPost(PostLoginEvent event) {
		debug("PostLogin for " + event.getPlayer().getName() + ", offlineMode=" + bungeeLoader.getAOInstance().getOfflineMode());
		if (!bungeeLoader.getAOInstance().getOfflineMode()) {
			// If we are not in mojang offline mode, update the player data
			final String username = event.getPlayer().getName();
			final String ip = event.getPlayer().getAddress().getAddress().getHostAddress();
			final UUID uuid = event.getPlayer().getUniqueId();
			this.bungeeLoader.getProxy().getScheduler().runAsync(this.bungeeLoader, new Runnable() {
				@Override
				public void run() {
					BungeeListener.this.bungeeLoader.alwaysOnline.database.updatePlayer(username, ip, uuid);
				}
			});
		}
	}

}
