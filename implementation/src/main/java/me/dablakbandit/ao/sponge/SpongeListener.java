package me.dablakbandit.ao.sponge;

import me.dablakbandit.ao.proxy.ProxyListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.event.server.ClientPingServerEvent;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;

import java.util.Optional;
import java.util.UUID;

public class SpongeListener extends ProxyListener {

    private final SpongeLoader spongeLoader;
    private Component MOTD;

    public SpongeListener(SpongeLoader spongeLoader) {
        super(spongeLoader);
        this.spongeLoader = spongeLoader;
        this.MOTD = LegacyComponentSerializer.legacyAmpersand().deserialize(
                this.spongeLoader.getAOInstance().config.getProperty(
                        "message-motd-offline",
                        "&eMojang servers are down,\n&ebut you can still connect!"
                )
        );
    }

    @Listener
    public void onPlayerLogin(ServerSideConnectionEvent.Login event) {
        if (spongeLoader.getAOInstance().getOfflineMode()) {
            String username = event.user().profile().name().orElse("");
            if (!this.validate(username)) {
                event.setMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        this.spongeLoader.getAOInstance().config.getProperty("message-kick-invalid", "&cInvalid username. Hacking?")
                ));
                event.setCancelled(true);
                return;
            }
            String ip = event.user().player().flatMap(player -> Optional.ofNullable(player.connection().address().getAddress().getHostAddress())).orElse("unknown");
            String lastIP = this.spongeLoader.getAOInstance().getDatabase().getIP(username);
            if (lastIP == null) {
                event.setMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                        this.spongeLoader.getAOInstance().config.getProperty("message-kick-new", "&cWe can not let you join because the mojang servers are offline!")
                ));
                event.setCancelled(true);
            } else {
                if (!lastIP.equals(ip)) {
                    event.setMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            this.spongeLoader.getAOInstance().config.getProperty("message-kick-ip", "&cWe can not let you join since you are not on the same computer you logged on before!")
                    ));
                    event.setCancelled(true);
                } else {
                    this.spongeLoader.log(java.util.logging.Level.INFO, username + " was successfully authenticated while mojang servers were offline. Connecting IP is " + ip + " and the last authenticated known IP was " + lastIP);
                }
            }
        }
    }

    @Listener
    public void onServerPing(ClientPingServerEvent event) {
        if (spongeLoader.getAOInstance().getOfflineMode() && this.MOTD != null) {
            event.response().setDescription(this.MOTD);
        }
    }

    @Listener
    public void onPlayerJoin(ServerSideConnectionEvent.Join event) {
        if (!spongeLoader.getAOInstance().getOfflineMode()) {
            final String username = event.player().name();
            final String ip = event.player().connection().address().getAddress().getHostAddress();
            final UUID uuid = event.player().uniqueId();
            Task.Builder builder = Task.builder().execute(() -> {
                this.spongeLoader.getAOInstance().getDatabase().updatePlayer(username, ip, uuid);
            });
            Sponge.server().scheduler().submit(builder.build());
        }
    }
}