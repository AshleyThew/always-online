package me.dablakbandit.ao.sponge;

import me.dablakbandit.ao.proxy.ProxyListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.event.server.ClientPingServerEvent;
import org.spongepowered.api.scheduler.Task;

import java.util.UUID;

public class SpongeListener extends ProxyListener {

	private final SpongeLoader spongeLoader;
	private Component MOTD;

	public SpongeListener(SpongeLoader spongeLoader) {
		super(spongeLoader);
		this.spongeLoader = spongeLoader;
		this.MOTD = LegacyComponentSerializer.legacyAmpersand().deserialize(this.spongeLoader.getAOInstance().config.getProperty("message-motd-offline", "&eMojang servers are down,\n&ebut you can still connect!"));
	}

	@Listener
	public void onPlayerJoin(ServerSideConnectionEvent.Join event) {
		if (!spongeLoader.getAOInstance().getOfflineMode()) {
			final String username = event.player().name();
			final String ip = event.connection().address().getAddress().getHostAddress();
			final UUID uuid = event.player().uniqueId();
			Task.Builder builder = Task.builder().execute(() -> {
				this.spongeLoader.getAOInstance().getDatabase().updatePlayer(username, ip, uuid);
			}).plugin(spongeLoader.getPluginContainer());
			Sponge.server().scheduler().submit(builder.build());
		}
	}

	@Listener(order = Order.LATE)
	public void onClientPing(ClientPingServerEvent event) {
		if (spongeLoader.getAOInstance().getOfflineMode()) {
			event.response().setDescription(this.MOTD);
		}
	}
}