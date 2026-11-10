package me.dablakbandit.ao.sponge;

import me.dablakbandit.ao.config.AlwaysOnlineConfig;
import me.dablakbandit.ao.databases.Database;
import me.dablakbandit.ao.databases.Skin;
import me.dablakbandit.ao.hybrid.AlwaysOnline;
import me.dablakbandit.ao.proxy.ProxyListener;
import me.dablakbandit.ao.sponge.session.SpongeSessionInjector;
import me.dablakbandit.ao.utils.Placeholders;
import me.dablakbandit.ao.utils.UUIDUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.event.server.ClientPingServerEvent;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.profile.property.ProfileProperty;
import org.spongepowered.api.scheduler.Task;

import java.util.UUID;
import java.util.logging.Level;

public class SpongeListener extends ProxyListener {

	private final SpongeLoader spongeLoader;
	private final Component MOTD;

	public SpongeListener(SpongeLoader spongeLoader) {
		super(spongeLoader);
		this.spongeLoader = spongeLoader;
		String motd = this.spongeLoader.getAOInstance().config.messages.motdOffline;
		this.MOTD = AlwaysOnlineConfig.disabled(motd) ? null : LegacyComponentSerializer.legacyAmpersand().deserialize(motd);
	}

	/**
	 * While mojang is offline the server lets everyone in without asking mojang, so this is where players are
	 * checked, as on every other platform: the name must be one seen before, from the same address. Players
	 * who pass get their real UUID and saved skin instead of a made-up offline profile.
	 */
	@Listener(order = Order.EARLY)
	public void onAuth(ServerSideConnectionEvent.Auth event) {
		AlwaysOnline alwaysOnline = this.spongeLoader.getAOInstance();
		if (event.isCancelled() || !alwaysOnline.getOfflineMode()) return;
		GameProfile profile = event.profile();
		String username = profile.name().orElse(null);
		String ip = event.connection().address().getAddress().getHostAddress();
		if (UUIDUtil.isFloodgateId(profile.uuid())) return;
		if (!this.validate(username)) {
			this.deny(event, alwaysOnline.config.messages.kickInvalid, username, ip, null);
			return;
		}
		Database database = alwaysOnline.getDatabase();
		String lastIP = database.getIP(username);
		if (lastIP == null) {
			this.deny(event, alwaysOnline.config.messages.kickNew, username, ip, null);
			this.spongeLoader.log(Level.INFO, "Denied " + username + " from logging in cause their ip [" + ip + "] has never connected to this server before!");
			return;
		}
		if (!lastIP.equals(ip)) {
			this.deny(event, alwaysOnline.config.messages.kickIp, username, ip, lastIP);
			this.spongeLoader.log(Level.INFO, "Denied " + username + " from logging in cause their ip [" + ip + "] does not match their last ip!");
			return;
		}
		this.spongeLoader.log(Level.INFO, username + " was successfully authenticated while mojang servers were offline. Connecting IP is " + ip + " and the last authenticated known IP was " + lastIP);
		UUID uuid = database.getUUID(username);
		if (uuid == null) return;
		try {
			Object loginListener = event.cause().root();
			if (!SpongeSessionInjector.getInstance().replaceLoginProfile(loginListener, uuid, username, database.getSkin(username))) {
				this.spongeLoader.log(Level.WARNING, "Could not give " + username + " their own UUID, so they join as a new offline player");
			} else if (alwaysOnline.isDebug()) {
				this.spongeLoader.log(Level.INFO, "Restored the login profile for " + username + ": UUID " + uuid);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			this.spongeLoader.log(Level.WARNING, "Could not give " + username + " their own UUID (" + e + "), so they join as a new offline player");
		}
	}

	private void deny(ServerSideConnectionEvent.Auth event, String message, String username, String ip, String lastIp) {
		event.setMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(Placeholders.apply(message, username, ip, lastIp)));
		event.setCancelled(true);
	}

	@Listener
	public void onPlayerJoin(ServerSideConnectionEvent.Join event) {
		if (!spongeLoader.getAOInstance().getOfflineMode()) {
			final String username = event.player().name();
			final String ip = event.connection().address().getAddress().getHostAddress();
			final UUID uuid = event.player().uniqueId();
			Skin skin = null;
			for (ProfileProperty property : event.player().profile().properties()) {
				if (Skin.PROPERTY.equals(property.name())) skin = Skin.of(property.value(), property.signature().orElse(null));
			}
			final Skin joinSkin = skin;
			Task.Builder builder = Task.builder().execute(() -> {
				this.spongeLoader.getAOInstance().getDatabase().updatePlayer(username, ip, uuid, joinSkin);
			}).plugin(spongeLoader.getPluginContainer());
			Sponge.server().scheduler().submit(builder.build());
		}
	}

	@Listener(order = Order.LATE)
	public void onClientPing(ClientPingServerEvent event) {
		if (spongeLoader.getAOInstance().getOfflineMode() && this.MOTD != null) {
			event.response().setDescription(this.MOTD);
		}
	}
}
