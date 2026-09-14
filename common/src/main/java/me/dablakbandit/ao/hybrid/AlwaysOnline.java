package me.dablakbandit.ao.hybrid;

import com.google.gson.Gson;
import me.dablakbandit.annotateconfig.AnnotateConfig;
import me.dablakbandit.annotateconfig.ConfigHandle;
import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.config.AlwaysOnlineConfig;
import me.dablakbandit.ao.config.LegacyPropertiesImporter;
import me.dablakbandit.ao.databases.Database;
import me.dablakbandit.ao.databases.FileDatabase;
import me.dablakbandit.ao.databases.MongoDatabase;
import me.dablakbandit.ao.databases.MySQLDatabase;
import me.dablakbandit.ao.update.UpdateChecker;
import me.dablakbandit.ao.utils.CheckMethods;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class AlwaysOnline implements IAlwaysOnline {

	private boolean MOJANG_OFFLINE_MODE = false, CHECK_SESSION_STATUS = true, DEBUG = false;

	private static final String UNREADABLE_FILE = "config.yml.unreadable";

	public Database database = null;
	public AlwaysOnlineConfig config = new AlwaysOnlineConfig();

	public final NativeExecutor nativeExecutor;
	private Path stateFile;

	public AlwaysOnline(NativeExecutor nativeExecutor) {
		this.nativeExecutor = nativeExecutor;
	}

	public void disable() {
		if (this.database != null) {
			this.nativeExecutor.log(Level.INFO, "Saving data...");
			this.nativeExecutor.cancelAllOurTasks();
			try {
				this.database.save();
				this.nativeExecutor.log(Level.INFO, "Closing database connections/streams...");
				this.database.close();
				this.database = null;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		this.nativeExecutor.cancelTask(UpdateChecker.getInstance().getSchedule());
	}

	public void reload() {
		// Close the database
		this.disable();
		this.nativeExecutor.log(Level.INFO, "Loading configuration...");

		Path dataFolder = this.nativeExecutor.dataFolder();
		Path configFile = dataFolder.resolve("config.yml");
		Path legacyFile = dataFolder.resolve(LegacyPropertiesImporter.LEGACY_FILE);
		try {
			if (Files.notExists(dataFolder)) Files.createDirectories(dataFolder);
			this.config = new AlwaysOnlineConfig();

			// Upgrading from 6.3.x: seed the schema from config.properties before the first load
			// writes config.yml, so every setting carries across and the old file is kept aside.
			LegacyPropertiesImporter.Result imported = null;
			if (Files.notExists(configFile) && Files.exists(legacyFile)) {
				imported = LegacyPropertiesImporter.importInto(this.config, legacyFile);
			} else if (Files.exists(legacyFile)) {
				this.nativeExecutor.log(Level.WARNING, "Both config.yml and " + LegacyPropertiesImporter.LEGACY_FILE + " are present. config.yml is the one being used; nothing in " + LegacyPropertiesImporter.LEGACY_FILE + " is read. Delete or rename it once you have moved anything you still need.");
			}

			// Loading also rewrites config.yml, adding any options introduced since it was written.
			// A file this version cannot read - the config.yml much older builds used, or one edited
			// into an unparseable state - is moved aside and replaced rather than taken as a reason
			// to stop, since a server with no AlwaysOnline is exactly what this plugin exists to avoid.
			ConfigHandle handle;
			try {
				handle = AnnotateConfig.builder(this.config, configFile).build();
				handle.load();
			} catch (IOException | RuntimeException broken) {
				Path aside = dataFolder.resolve(UNREADABLE_FILE);
				Files.move(configFile, aside, StandardCopyOption.REPLACE_EXISTING);
				this.nativeExecutor.log(Level.WARNING, "config.yml could not be read (" + broken + "). It has been moved to " + UNREADABLE_FILE + " and a fresh one generated with the default settings. Copy anything you need back across, then run /alwaysonline reload.");
				this.config = new AlwaysOnlineConfig();
				handle = AnnotateConfig.builder(this.config, configFile).build();
				handle.load();
			}
			this.config.applyRequiredDefaults();

			// 6.4.0 imported config.properties with the wrong character encoding, so decorative
			// characters in messages were saved garbled. Fix them once and write the file back.
			int repaired = this.config.repairMisreadMessages();
			if (repaired > 0) {
				handle.save();
				this.nativeExecutor.log(Level.INFO, "Repaired " + repaired + " message(s) in config.yml that 6.4.0 imported with the wrong character encoding.");
			}

			if (imported != null) {
				Files.move(legacyFile, dataFolder.resolve(LegacyPropertiesImporter.RENAMED_FILE), StandardCopyOption.REPLACE_EXISTING);
				this.nativeExecutor.log(Level.INFO, "Imported " + imported.imported.size() + " setting(s) from " + LegacyPropertiesImporter.LEGACY_FILE + " into config.yml. The old file was renamed to " + LegacyPropertiesImporter.RENAMED_FILE + ".");
				for (String warning : imported.warnings) {
					this.nativeExecutor.log(Level.WARNING, warning);
				}
				if (!imported.unknown.isEmpty()) {
					this.nativeExecutor.log(Level.WARNING, "These options in " + LegacyPropertiesImporter.LEGACY_FILE + " are not used by this version and were not carried over: " + String.join(", ", imported.unknown));
				}
			}

			// Read the state.txt file and assign variables
			this.stateFile = dataFolder.resolve("state.txt");
			if (Files.isReadable(this.stateFile)) {
				String data = new String(Files.readAllBytes(this.stateFile), StandardCharsets.UTF_8);
				if (data.contains(":")) {
					String[] d = data.split(Pattern.quote(":"));
					CHECK_SESSION_STATUS = Boolean.parseBoolean(d[0]);
					MOJANG_OFFLINE_MODE = Boolean.parseBoolean(d[1]);
					this.nativeExecutor.log(Level.INFO, "Successfully loaded previous state variables!");
				}
			}
		} catch (IOException | RuntimeException e) {
			e.printStackTrace();
			this.nativeExecutor.log(Level.INFO, "Failed to load configuration file. Aborting...");
			this.nativeExecutor.disablePlugin();
			return;
		}

		// No negative numbers.
		int checkInterval = Math.max(0, this.config.checkInterval);
		if (checkInterval < 15) {
			this.nativeExecutor.log(Level.WARNING, "Your check-interval is less than 15 seconds." + " This may cause issues and is recommended to be set to a higher number.");
		}

		// Kill any existing threads or listeners in case of a re-load
		this.nativeExecutor.cancelAllOurTasks();
		this.nativeExecutor.unregisterAllListeners();
		if (this.config.storage.mysql.enabled) {
			this.nativeExecutor.log(Level.INFO, "Loading MySQL database...");
			this.nativeExecutor.initMySQL();
			try {
				AlwaysOnlineConfig.Storage.Mysql mysql = this.config.storage.mysql;
				this.database = new MySQLDatabase(this.nativeExecutor, mysql.host, mysql.port, mysql.database, mysql.username, mysql.password, AlwaysOnlineConfig.exact(mysql.extra));
			} catch (SQLException e) {
				this.nativeExecutor.log(Level.WARNING, "Failed to load the MySQL database, falling back to file database.");
				e.printStackTrace();
				this.database = new FileDatabase(dataFolder.resolve("playerData.txt"));
			}
		} else if (this.config.storage.mongodb.enabled) {
			this.nativeExecutor.log(Level.INFO, "Loading MongoDB database...");
			try {
				AlwaysOnlineConfig.Storage.Mongodb mongodb = this.config.storage.mongodb;
				this.database = new MongoDatabase(this.nativeExecutor, mongodb.host, mongodb.port, mongodb.database, AlwaysOnlineConfig.exact(mongodb.username), AlwaysOnlineConfig.exact(mongodb.password), AlwaysOnlineConfig.exact(mongodb.connectionString));
			} catch (Exception e) {
				this.nativeExecutor.log(Level.WARNING, "Failed to load the MongoDB database, falling back to file database.");
				e.printStackTrace();
				this.database = new FileDatabase(dataFolder.resolve("playerData.txt"));
			}
		} else {
			this.nativeExecutor.log(Level.INFO, "Loading file database...");
			this.database = new FileDatabase(dataFolder.resolve("playerData.txt"));
		}
		this.nativeExecutor.log(Level.INFO, "Database is ready to go!");
		this.nativeExecutor.registerListener();
		this.nativeExecutor.runAsyncRepeating(new MojangSessionCheck(this), 0, checkInterval, TimeUnit.SECONDS);

		this.nativeExecutor.notifyOfflineMode(MOJANG_OFFLINE_MODE);
		UpdateChecker.getInstance().start(nativeExecutor);
	}

	public void saveState() {
		try {
			Files.write(this.stateFile, (CHECK_SESSION_STATUS + ":" + MOJANG_OFFLINE_MODE).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			this.nativeExecutor.log(Level.WARNING, "Failed to save state. This error can be safely ignored. [" + e.getMessage() + "]");
		}
	}

	public void printDebugInformation() {
		this.nativeExecutor.log(Level.INFO, "Session HEAD check: " + CheckMethods.directSessionServerStatus(this, new Gson()));
		this.nativeExecutor.log(Level.INFO, "Mojang offline mode: " + MOJANG_OFFLINE_MODE);
		this.nativeExecutor.log(Level.INFO, "Check status: " + CHECK_SESSION_STATUS);
		this.DEBUG = !DEBUG;
		if (DEBUG) {
			this.nativeExecutor.log(Level.INFO, "Debug mode enabled!");
		}
	}

	public Database getDatabase() {
		return database;
	}

	public void toggleOfflineMode() {
		MOJANG_OFFLINE_MODE = !MOJANG_OFFLINE_MODE;
		this.nativeExecutor.notifyOfflineMode(MOJANG_OFFLINE_MODE);
	}

	public boolean getOfflineMode() {
		return MOJANG_OFFLINE_MODE;
	}

	public void setCheckSessionStatus(boolean value) {
		CHECK_SESSION_STATUS = value;
	}

	public boolean getCheckSessionStatus() {
		return CHECK_SESSION_STATUS;
	}

	public NativeExecutor getNativeExecutor() {
		return nativeExecutor;
	}

	public boolean isDebug() {
		return DEBUG;
	}

	public void setDebug(boolean debug) {
		DEBUG = debug;
	}
}
