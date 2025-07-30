package me.dablakbandit.ao.sponge;

import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.hybrid.AlwaysOnline;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.builtin.jvm.Plugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.spongepowered.api.Sponge.game;

@Plugin("alwaysonline")
public class SpongeLoader implements NativeExecutor {

    private final AlwaysOnline alwaysOnline = new AlwaysOnline(this);
    private final PluginContainer pluginContainer;
    private final Path configDir;

    @Inject
    public SpongeLoader(PluginContainer pluginContainer, @ConfigDir(sharedRoot = false) Path configDir) {
        this.pluginContainer = pluginContainer;
        this.configDir = configDir;
    }

    @Listener
    public void onServerStart(StartedEngineEvent<Server> event) {
        this.alwaysOnline.reload();
        event.game().eventManager().registerListeners(pluginContainer, new SpongeListener(this));
    }

    @Listener
    public void onServerStop(StoppingEngineEvent<Server> event) {
        this.alwaysOnline.disable();
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event){
        event.register(this.pluginContainer, new SpongeCommand(this).build(), "alwaysonline", "ao");
    }

    @Override
    public Object runAsyncRepeating(Runnable runnable, long delay, long period, TimeUnit timeUnit) {
        Task.Builder builder = Task.builder()
                .execute(runnable)
                .delay(delay, timeUnit)
                .interval(period, timeUnit);
        ScheduledTask task = Sponge.server().scheduler().submit(builder.build());
        return task.uniqueId();
    }

    @Override
    public void cancelTask(Object taskID) {
        if (taskID instanceof UUID) {
            UUID uuid = (UUID) taskID;
            game().server().scheduler().findTask(uuid).ifPresent(ScheduledTask::cancel);
        }
    }

    @Override
    public void cancelAllOurTasks() {
        game().server().scheduler().tasks(pluginContainer).forEach(ScheduledTask::cancel);
    }

    @Override
    public void unregisterAllListeners() {
        game().eventManager().unregisterListeners(pluginContainer);
    }

    @Override
    public void log(Level level, String message) {
        // Using Sponge logging system, may need adjustment based on your specific needs
        if (level == Level.SEVERE) {
            pluginContainer.logger().error(message);
        } else if (level == Level.WARNING) {
            pluginContainer.logger().warn(message);
        } else if (level == Level.INFO) {
            pluginContainer.logger().info(message);
        } else {
            pluginContainer.logger().debug(message);
        }
    }

    @Override
    public Path dataFolder() {
        return configDir;
    }

    @Override
    public void disablePlugin() {
        Sponge.server().shutdown();
    }

    @Override
    public void registerListener() {
        game().eventManager().registerListeners(pluginContainer, new SpongeListener(this));
    }

    @Override
    public void broadcastMessage(String message) {
        Sponge.server().broadcastAudience().sendMessage(Component.text(message, NamedTextColor.YELLOW));
    }

    @Override
    public AlwaysOnline getAOInstance() {
        return alwaysOnline;
    }

    @Override
    public String getVersion() {
        return pluginContainer.metadata().version().toString();
    }

    @Override
    public void notifyOfflineMode(boolean offlineMode) {
        // No specific action needed for Sponge
    }

    public PluginContainer getPluginContainer() {
        return pluginContainer;
    }
}