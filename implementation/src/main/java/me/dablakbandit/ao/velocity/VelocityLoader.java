package me.dablakbandit.ao.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.databases.Database;
import me.dablakbandit.ao.databases.MongoDatabase;
import me.dablakbandit.ao.databases.MySQLDatabase;
import me.dablakbandit.ao.hybrid.AlwaysOnline;
import me.dablakbandit.ao.velocity.metrics.Metrics;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

@Plugin(id = "alwaysonline", name = "Always Online", version = "@version@", url = "https://www.spigotmc.org/resources/alwaysonline.66591/", description = "Keep your server running while mojang is offline, Supports all server versions!", authors = "Dablakbandit")
public class VelocityLoader implements NativeExecutor {

	private static final String MYSQL_DRIVER_FILE_NAME = "mysql-connector-j-8.0.33.jar";
	private static final String MYSQL_DRIVER_RESOURCE_PATH = "lib/" + MYSQL_DRIVER_FILE_NAME;

	public final AlwaysOnline alwaysOnline = new AlwaysOnline(this);
	public final ProxyServer server;
	private final Logger logger;
	private final Path dataDirectory;
	private final Metrics.Factory metricsFactory;

	@Inject
	public VelocityLoader(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory,
			Metrics.Factory metricsFactory) {
		this.server = server;
		this.logger = logger;
		this.dataDirectory = dataDirectory;
		this.metricsFactory = metricsFactory;
	}

	public Logger getLogger() {
		return logger;
	}

	@Subscribe
	public void onProxyInitialization(ProxyInitializeEvent event) {
		loadLibs();
		this.alwaysOnline.reload();
		CommandMeta meta = this.server.getCommandManager().metaBuilder("alwaysonline").aliases("ao").build();
		this.server.getCommandManager().register(meta, new VelocityCommand(this));

		Metrics metrics = metricsFactory.make(this, 15202);
		Database database = alwaysOnline.getDatabase();
		String databaseType = "FlatFile";
		if (database instanceof MySQLDatabase) {
			databaseType = "MySQL";
		} else if (database instanceof MongoDatabase) {
			databaseType = "MongoDB";
		}
		String finalDatabaseType = databaseType;
		metrics.addCustomChart(new Metrics.SimplePie("database_type", () -> finalDatabaseType));
	}

	private void loadLibs() {
		File libsFolder = new File(this.dataFolder().toFile(), "/libs/");
		if (!libsFolder.exists()) {
			libsFolder.mkdirs();
		}
		extractEmbeddedMysqlDriver(libsFolder);
		File[] libFiles = libsFolder.listFiles();
		if (libFiles == null) {
			return;
		}
		for (File file : libFiles) {
			if (file.getName().endsWith(".jar")) {
				logger.info(file.getName());
				server.getPluginManager().addToClasspath(this, file.toPath());
			}
		}
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (Exception | Error ignored) {
		}
	}

	private final AtomicInteger taskCounter = new AtomicInteger(0);
	private final Map<Integer, ScheduledTask> scheduledTasks = new HashMap<>();

	@Override
	public Object runAsyncRepeating(Runnable runnable, long delay, long period, TimeUnit timeUnit) {
		int taskId = taskCounter.getAndIncrement();
		ScheduledTask task = server.getScheduler().buildTask(this, () -> {
			scheduledTasks.remove(taskId);
			runnable.run();
		}).delay(delay, timeUnit).repeat(period, timeUnit).schedule();
		scheduledTasks.put(taskId, task);
		return taskId;
	}

	@Override
	public void cancelTask(Object taskID) {
		ScheduledTask task = scheduledTasks.remove(taskID);
		if (task != null) {
			task.cancel();
		}
	}

	@Override
	public void cancelAllOurTasks() {
		for (Map.Entry<Integer, ScheduledTask> taskEntry : scheduledTasks.entrySet()) {
			taskEntry.getValue().cancel();
		}
		scheduledTasks.clear();
	}

	@Override
	public void unregisterAllListeners() {
		server.getEventManager().unregisterListeners(this);
	}

	@Override
	public void log(Level level, String message) {
		if (level == Level.WARNING) {
			logger.warn(message);
		} else {
			logger.info(message);
		}
	}

	@Override
	public Path dataFolder() {
		return dataDirectory;
	}

	@Override
	public void disablePlugin() {

	}

	@Override
	public void registerListener() {
		server.getEventManager().register(this, new VelocityListener(this));
	}

	@Override
	public void broadcastMessage(String message) {
		this.server.sendMessage(LegacyComponentSerializer.legacy('&').deserialize(message));
	}

	@Override
	public AlwaysOnline getAOInstance() {
		return alwaysOnline;
	}

	@Override
	public String getVersion() {
		return server.getPluginManager().getPlugin("alwaysonline").get().getDescription().getVersion().get();
	}

	@Override
	public void notifyOfflineMode(boolean offlineMode) {

	}

	@Override
	public void dispatchConsoleCommand(String command) {
		this.server.getCommandManager().executeAsync(this.server.getConsoleCommandSource(), command);
	}

	@Override
	public void initMySQL() {
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			return;
		} catch (Exception | Error ignored) {
		}
		loadLibs();
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			return;
		} catch (Exception | Error ignored) {
		}
		logger.error("Failed to load embedded mysql driver " + MYSQL_DRIVER_FILE_NAME);
	}

	private void extractEmbeddedMysqlDriver(File libsFolder) {
		File libFile = new File(libsFolder, MYSQL_DRIVER_FILE_NAME);
		if (libFile.exists()) {
			return;
		}
		try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(MYSQL_DRIVER_RESOURCE_PATH)) {
			if (inputStream == null) {
				logger.warn("Embedded mysql driver resource missing: {}", MYSQL_DRIVER_RESOURCE_PATH);
				return;
			}
			try (OutputStream outputStream = new FileOutputStream(libFile)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}
			}
			logger.info("Extracted {}", MYSQL_DRIVER_FILE_NAME);
		} catch (IOException e) {
			logger.error("Failed to extract embedded mysql driver {}", MYSQL_DRIVER_FILE_NAME, e);
		}
	}
}
