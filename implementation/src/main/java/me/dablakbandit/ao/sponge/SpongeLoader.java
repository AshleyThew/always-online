package me.dablakbandit.ao.sponge;

import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.hybrid.AlwaysOnline;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStoppingEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Plugin(id = "alwaysonline", name = "Always Online", version = "1.0", description = "Keep your server running while mojang is offline, Supports all server versions!", authors = "Dablakbandit")
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
    public void onServerStart(GameInitializationEvent event) {
        this.alwaysOnline.reload();
        Sponge.getEventManager().registerListeners(this, new SpongeListener(this));
    }

    @Listener
    public void onServerStop(GameStoppingEvent event) {
        this.alwaysOnline.disable();
    }

    @Override
    public int runAsyncRepeating(Runnable runnable, long delay, long period, TimeUnit timeUnit) {
        Task task = Task.builder()
                .execute(runnable)
                .delay(delay, timeUnit)
                .interval(period, timeUnit)
                .submit(this);
        return task.getUniqueId().hashCode();
    }

    @Override
    public void cancelTask(int taskID) {
        Sponge.getScheduler().getTaskById(taskID).ifPresent(Task::cancel);
    }

    @Override
    public void cancelAllOurTasks() {
        Sponge.getScheduler().getTasksByPlugin(this).forEach(Task::cancel);
    }

    @Override
    public void unregisterAllListeners() {
        Sponge.getEventManager().unregisterListeners(this);
    }

    @Override
    public void log(Level level, String message) {
        pluginContainer.getLogger().log(level, message);
    }

    @Override
    public Path dataFolder() {
        return configDir;
    }

    @Override
    public void disablePlugin() {
        Sponge.getServer().shutdown();
    }

    @Override
    public void registerListener() {
        Sponge.getEventManager().registerListeners(this, new SpongeListener(this));
    }

    @Override
    public void broadcastMessage(String message) {
        Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.YELLOW, message));
    }

    @Override
    public AlwaysOnline getAOInstance() {
        return alwaysOnline;
    }

    @Override
    public String getVersion() {
        return pluginContainer.getVersion().orElse("unknown");
    }

    @Override
    public void notifyOfflineMode(boolean offlineMode) {
        // No specific action needed for Sponge
    }
}
