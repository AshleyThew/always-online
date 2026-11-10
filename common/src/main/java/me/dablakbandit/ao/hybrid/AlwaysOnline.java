package me.dablakbandit.ao.hybrid;

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

	private volatile boolean MOJANG_OFFLINE_MODE = false, CHECK_SESSION_STATUS = true, DEBUG = false;
	// Set by /alwaysonline toggle: the automatic checks stop changing the mode (they still send alerts)
	// until the mode is toggled again.
	private volatile boolean MANUAL_OVERRIDE = false;
	// Bumped on every manual toggle, so the checks know a new hand-set mode started and compare against it.
	private volatile int overrideGeneration = 0;
	// True when the configured MySQL/MongoDB could not be reached and the file database is standing in.
	private volatile String fallbackDatabase = null;
	// The latest automatic check, for /alwaysonline status. 0 until the first one has run.
	private volatile long lastCheckMillis = 0;
	private volatile String lastCheckProblem = null;

	private static final String UNREADABLE_FILE = "config.yml.unreadable";
	private static final String ENCODING_CHECKED_FILE = ".config-encoding-checked";

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
			// characters in messages were saved garbled. The repair cannot tell that damage apart from
			// text someone typed on purpose, so it must not run on configs that were never exposed to
			// it. Only a 6.4.0 import can have caused it: that left config.properties.old behind, and
			// nothing has recorded the check yet. It runs once, and an import done by this version is
			// already correct, so that path records the check without repairing anything.
			Path renamedFile = dataFolder.resolve(LegacyPropertiesImporter.RENAMED_FILE);
			Path encodingChecked = dataFolder.resolve(ENCODING_CHECKED_FILE);
			if (imported == null && Files.exists(renamedFile) && Files.notExists(encodingChecked)) {
				int repaired = this.config.repairMisreadMessages();
				if (repaired > 0) {
					handle.save();
					this.nativeExecutor.log(Level.INFO, "Repaired " + repaired + " message(s) in config.yml that 6.4.0 imported with the wrong character encoding.");
				}
				recordEncodingChecked(encodingChecked);
			}

			if (imported != null) {
				Files.move(legacyFile, renamedFile, StandardCopyOption.REPLACE_EXISTING);
				recordEncodingChecked(encodingChecked);
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
					String[] d = data.trim().split(Pattern.quote(":"));
					CHECK_SESSION_STATUS = Boolean.parseBoolean(d[0]);
					MOJANG_OFFLINE_MODE = Boolean.parseBoolean(d[1]);
					MANUAL_OVERRIDE = d.length > 2 && Boolean.parseBoolean(d[2]);
					if (MANUAL_OVERRIDE) {
						this.nativeExecutor.log(Level.WARNING, "Mojang offline mode was set by hand with /alwaysonline toggle and is still " + (MOJANG_OFFLINE_MODE ? "on" : "off") + ". The automatic checks will only send alerts until it is toggled again.");
					}
					this.nativeExecutor.log(Level.INFO, "Successfully loaded previous state variables!");
				}
			}
			if (!CHECK_SESSION_STATUS) {
				// Saved by /alwaysonline disable, possibly long ago, and easy to forget.
				this.nativeExecutor.log(Level.WARNING, "The automatic mojang checks are switched off (someone ran /alwaysonline disable). AlwaysOnline will not notice a mojang outage until you run /alwaysonline enable.");
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
		this.fallbackDatabase = null;
		if (this.config.storage.mysql.enabled) {
			this.nativeExecutor.log(Level.INFO, "Loading MySQL database...");
			this.nativeExecutor.initMySQL();
			try {
				AlwaysOnlineConfig.Storage.Mysql mysql = this.config.storage.mysql;
				this.database = new MySQLDatabase(this.nativeExecutor, mysql.host, mysql.port, mysql.database, mysql.username, mysql.password, AlwaysOnlineConfig.exact(mysql.extra));
			} catch (SQLException e) {
				e.printStackTrace();
				this.useFallbackDatabase("MySQL", e);
				this.database = new FileDatabase(dataFolder.resolve("playerData.txt"));
			}
		} else if (this.config.storage.mongodb.enabled) {
			this.nativeExecutor.log(Level.INFO, "Loading MongoDB database...");
			try {
				AlwaysOnlineConfig.Storage.Mongodb mongodb = this.config.storage.mongodb;
				this.database = new MongoDatabase(this.nativeExecutor, mongodb.host, mongodb.port, mongodb.database, AlwaysOnlineConfig.exact(mongodb.username), AlwaysOnlineConfig.exact(mongodb.password), AlwaysOnlineConfig.exact(mongodb.connectionString));
			} catch (Exception e) {
				e.printStackTrace();
				this.useFallbackDatabase("MongoDB", e);
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

	private void useFallbackDatabase(String configured, Exception cause) {
		this.fallbackDatabase = configured;
		this.nativeExecutor.log(Level.SEVERE, "==================================================================");
		this.nativeExecutor.log(Level.SEVERE, "Could not connect to the " + configured + " database (" + cause.getMessage() + ").");
		this.nativeExecutor.log(Level.SEVERE, "AlwaysOnline is using the local file database (playerData.txt) instead.");
		this.nativeExecutor.log(Level.SEVERE, "Players recorded in " + configured + " are NOT known to it, so if mojang goes down");
		this.nativeExecutor.log(Level.SEVERE, "they will be refused as new players. Fix the connection and restart or reload.");
		this.nativeExecutor.log(Level.SEVERE, "==================================================================");
	}

	/**
	 * Repeats the fallback warning at the moment it starts to matter: when offline mode switches on.
	 */
	public void warnIfFallbackDatabase() {
		String configured = this.fallbackDatabase;
		if (configured != null) {
			this.nativeExecutor.log(Level.SEVERE, "Offline mode is on but the " + configured + " database could not be reached at startup, so only players saved in the local file database can join until mojang is back.");
		}
	}

	private void recordEncodingChecked(Path marker) {
		try {
			Files.write(marker, ("AlwaysOnline has checked config.yml for the character encoding problem in the 6.4.0 config.properties import.\n"
					+ "Delete this file to run that check again on the next start.\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			// Not being able to record it only means the check may run again; not worth stopping for.
			this.nativeExecutor.log(Level.WARNING, "Could not write " + marker.getFileName() + ", so the config encoding check may run again next start. [" + e.getMessage() + "]");
		}
	}

	public void saveState() {
		try {
			Files.write(this.stateFile, (CHECK_SESSION_STATUS + ":" + MOJANG_OFFLINE_MODE + ":" + MANUAL_OVERRIDE).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			this.nativeExecutor.log(Level.WARNING, "Failed to save state. This error can be safely ignored. [" + e.getMessage() + "]");
		}
	}

	public void printDebugInformation() {
		String problem = CheckMethods.sessionServerProblem(this);
		this.nativeExecutor.log(Level.INFO, "Session server login check: " + (problem == null ? "ok" : problem));
		this.nativeExecutor.log(Level.INFO, "Mojang offline mode: " + MOJANG_OFFLINE_MODE + (MANUAL_OVERRIDE ? " (set by hand)" : ""));
		if (this.fallbackDatabase != null) {
			this.nativeExecutor.log(Level.WARNING, "Database: using the local file database because " + this.fallbackDatabase + " could not be reached");
		}
		this.nativeExecutor.log(Level.INFO, "Check status: " + CHECK_SESSION_STATUS);
		this.DEBUG = !DEBUG;
		if (DEBUG) {
			this.nativeExecutor.log(Level.INFO, "Debug mode enabled!");
		}
	}

	public Database getDatabase() {
		return database;
	}

	/**
	 * The /alwaysonline toggle command. The first toggle flips the mode and holds it there, so the automatic
	 * checks cannot undo it (they keep sending alerts); toggling again flips it back and hands control back
	 * to the checks.
	 */
	public synchronized void toggleOfflineMode() {
		MANUAL_OVERRIDE = !MANUAL_OVERRIDE;
		overrideGeneration++;
		MOJANG_OFFLINE_MODE = !MOJANG_OFFLINE_MODE;
		this.nativeExecutor.notifyOfflineMode(MOJANG_OFFLINE_MODE);
		this.saveState();
	}

	/**
	 * Used by the automatic checks. Does nothing while the mode is set by hand.
	 *
	 * @return whether the mode changed
	 */
	public synchronized boolean setOfflineMode(boolean offline) {
		if (MANUAL_OVERRIDE || MOJANG_OFFLINE_MODE == offline) return false;
		MOJANG_OFFLINE_MODE = offline;
		this.nativeExecutor.notifyOfflineMode(MOJANG_OFFLINE_MODE);
		return true;
	}

	public void recordCheck(String problem) {
		this.lastCheckProblem = problem;
		this.lastCheckMillis = System.currentTimeMillis();
	}

	/**
	 * The lines /alwaysonline status shows, with &amp; colour codes.
	 */
	public java.util.List<String> statusLines() {
		java.util.List<String> lines = new java.util.ArrayList<>();
		lines.add("&6AlwaysOnline " + this.nativeExecutor.getVersion() + " status");
		lines.add("&7Mojang offline mode: " + (MOJANG_OFFLINE_MODE ? "&aon" : "&coff") + (MANUAL_OVERRIDE ? " &e(set by hand, run /alwaysonline toggle to hand it back to the checks)" : " &7(automatic)"));
		if (CHECK_SESSION_STATUS) {
			lines.add("&7Automatic checks: &aon&7, every " + Math.max(0, this.config.checkInterval) + "s");
		} else {
			lines.add("&7Automatic checks: &coff &7(run /alwaysonline enable, or outages will not be noticed)");
		}
		long last = this.lastCheckMillis;
		if (last == 0) {
			lines.add("&7Last check: &7not run yet");
		} else {
			String when = ((System.currentTimeMillis() - last) / 1000) + "s ago";
			String problem = this.lastCheckProblem;
			lines.add("&7Last check: " + (problem == null ? "&amojang answered normally" : "&c" + problem) + " &7(" + when + ")");
		}
		Database db = this.database;
		lines.add("&7Database: &f" + (db == null ? "none" : db.describe()));
		if (this.fallbackDatabase != null) {
			lines.add("&c" + this.fallbackDatabase + " could not be reached at startup, so only players in the local file are known");
		}
		return lines;
	}

	public int getOverrideGeneration() {
		return overrideGeneration;
	}

	public boolean isManualOverride() {
		return MANUAL_OVERRIDE;
	}

	/**
	 * The line the toggle commands print after the new mode.
	 */
	public String toggleExplanation() {
		if (MANUAL_OVERRIDE) {
			return "This stays in place until you run /alwaysonline toggle again. The automatic checks will still send alerts when mojang goes down or comes back, but will not change it.";
		}
		return "The automatic checks are back in control and will switch offline mode on and off by themselves.";
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
