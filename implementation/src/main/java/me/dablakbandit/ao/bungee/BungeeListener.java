package me.dablakbandit.ao.bungee;

import me.dablakbandit.ao.config.AlwaysOnlineConfig;
import me.dablakbandit.ao.databases.Skin;
import me.dablakbandit.ao.proxy.ProxyListener;
import me.dablakbandit.ao.utils.Placeholders;
import me.dablakbandit.ao.utils.UUIDUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ServerPing;
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
			InitialHandler handler = (InitialHandler) event.getConnection();
			// Something earlier already took this player off mojang login. That is Floodgate for Bedrock
			// players (they sign in through Xbox), or the whole proxy running in offline mode. Either way
			// mojang being down does not affect them, so there is nothing for AlwaysOnline to do.
			if (!handler.isOnlineMode() || UUIDUtil.isFloodgateId(handler.getUniqueId())) {
				debug("PreLogin for " + event.getConnection().getName() + " does not use mojang login, leaving it alone");
				return;
			}
			// Verify if the name attempting to connect is even verified
			if (!this.validate(event.getConnection().getName())) {
				debug("Invalid username rejected at pre-login: " + event.getConnection().getName());
				event.setCancelReason(this.kickMessage(this.bungeeLoader.alwaysOnline.config.messages.kickInvalid, event.getConnection().getName(), event.getConnection().getAddress().getAddress().getHostAddress(), null));
				event.setCancelled(true);
				return;

			}
			// Get the connecting ip
			final String ip = handler.getAddress().getAddress().getHostAddress();
			// Get last known ip
			final String lastip = this.bungeeLoader.alwaysOnline.database.getIP(event.getConnection().getName());
			debug("PreLogin IP check for " + event.getConnection().getName() + ": current=" + ip + ", last=" + lastip);
			if (lastip == null) {// If null the player connecting is new
				event.setCancelReason(this.kickMessage(this.bungeeLoader.alwaysOnline.config.messages.kickNew, event.getConnection().getName(), ip, null));
				event.setCancelled(true);
				this.bungeeLoader.getLogger().info("Denied " + event.getConnection().getName() + " from logging in cause their ip [" + ip + "] has never connected to this server before!");
			} else {
				if (ip.equals(lastip)) {// If it matches set handler to offline mode, so it does not authenticate player
					// with mojang
					this.bungeeLoader.getLogger().info("Skipping session login for player " + event.getConnection().getName() + " [Connected ip: " + ip + ", Last ip: " + lastip + "]!");
					// Online mode must stay off: once this event finishes, BungeeCord reads it to decide whether
					// to send the player through mojang login, and that is exactly what is down.
					handler.setOnlineMode(false);
					UUID uuid = this.bungeeLoader.alwaysOnline.database.getUUID(event.getConnection().getName());
					debug("Setting offline UUID for " + event.getConnection().getName() + " to " + uuid);
					if (uuid != null) {
						handler.setUniqueId(uuid);
						// Mojang normally supplies the skin; without it the player would have none.
						Skin skin = this.bungeeLoader.alwaysOnline.database.getSkin(event.getConnection().getName());
						if (BungeeProfiles.applySkin(handler, uuid, event.getConnection().getName(), skin)) {
							debug("Restored saved skin for " + event.getConnection().getName());
						}
					}
				} else {// Deny the player from joining
					this.bungeeLoader.getLogger().info("Denied " + event.getConnection().getName() + " from logging in cause their ip [" + ip + "] does not match their last ip!");
					event.setCancelReason(this.kickMessage(this.bungeeLoader.alwaysOnline.config.messages.kickIp, event.getConnection().getName(), ip, lastip));
					event.setCancelled(true);
				}
			}
		}
	}

	private String kickMessage(String message, String username, String ip, String lastIp) {
		return ChatColor.translateAlternateColorCodes('&', Placeholders.apply(message, username, ip, lastIp));
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
			final Skin skin = event.getPlayer().getPendingConnection() instanceof InitialHandler ? BungeeProfiles.skinOf((InitialHandler) event.getPlayer().getPendingConnection()) : null;
			this.bungeeLoader.getProxy().getScheduler().runAsync(this.bungeeLoader, new Runnable() {
				@Override
				public void run() {
					BungeeListener.this.bungeeLoader.alwaysOnline.database.updatePlayer(username, ip, uuid, skin);
				}
			});
		}
	}

}
