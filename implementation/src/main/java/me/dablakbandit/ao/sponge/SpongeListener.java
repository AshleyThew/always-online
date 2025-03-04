package me.dablakbandit.ao.sponge;

import me.dablakbandit.ao.proxy.ProxyListener;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.event.server.ClientPingServerEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.util.UUID;

public class SpongeListener extends ProxyListener {

    private final SpongeLoader spongeLoader;

    public SpongeListener(SpongeLoader spongeLoader) {
        super(spongeLoader);
        this.spongeLoader = spongeLoader;
        this.MOTD = Text.of(TextColors.YELLOW, this.spongeLoader.getAOInstance().config.getProperty("message-motd-offline", "&eMojang servers are down,\\n&ebut you can still connect!"));
        if ("null".equals(this.MOTD.toPlain())) {
            this.MOTD = null;
        }
    }

    @Listener
    public void onPlayerLogin(ClientConnectionEvent.Auth event) {
        if (spongeLoader.getAOInstance().getOfflineMode()) {
            String username = event.getProfile().getName().orElse("");
            if (!this.validate(username)) {
                event.setMessage(Text.of(TextColors.RED, this.spongeLoader.getAOInstance().config.getProperty("message-kick-invalid", "Invalid username. Hacking?")));
                event.setCancelled(true);
                return;
            }
            String ip = event.getConnection().getAddress().getAddress().getHostAddress();
            String lastIP = this.spongeLoader.getAOInstance().getDatabase().getIP(username);
            if (lastIP == null) {
                event.setMessage(Text.of(TextColors.RED, this.spongeLoader.getAOInstance().config.getProperty("message-kick-new", "We can not let you join because the mojang servers are offline!")));
                event.setCancelled(true);
            } else {
                if (!lastIP.equals(ip)) {
                    event.setMessage(Text.of(TextColors.RED, this.spongeLoader.getAOInstance().config.getProperty("message-kick-ip", "We can not let you join since you are not on the same computer you logged on before!")));
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
            event.getResponse().setDescription(this.MOTD);
        }
    }

    @Listener
    public void onPlayerJoin(ClientConnectionEvent.Join event) {
        if (!spongeLoader.getAOInstance().getOfflineMode()) {
            final String username = event.getTargetEntity().getName();
            final String ip = event.getTargetEntity().getConnection().getAddress().getAddress().getHostAddress();
            final UUID uuid = event.getTargetEntity().getUniqueId();
            org.spongepowered.api.Sponge.getScheduler().createTaskBuilder().async().execute(() -> {
                this.spongeLoader.getAOInstance().getDatabase().updatePlayer(username, ip, uuid);
            }).submit(this.spongeLoader);
        }
    }
}
