package me.dablakbandit.ao.config;

import me.dablakbandit.annotateconfig.NamingStrategy;
import me.dablakbandit.annotateconfig.annotation.ConfigComment;
import me.dablakbandit.annotateconfig.annotation.ConfigIgnore;
import me.dablakbandit.annotateconfig.annotation.ConfigRoot;

/**
 * The config.yml schema. AnnotateConfig generates the file from these fields and comments, and on
 * every load rewrites it so options added in newer versions appear with their defaults while the
 * values already set are kept. Settings from the old config.properties are brought across once by
 * {@link LegacyPropertiesImporter}.
 */
@ConfigRoot(header = {"AlwaysOnline configuration", "Options are described above each key. New options are added automatically when the plugin updates."}, naming = NamingStrategy.LOWER_KEBAB_CASE, preserveUnknownFields = true)
public class AlwaysOnlineConfig {

	@ConfigIgnore
	static final String DEFAULT_MESSAGE_OFFLINE = "Mojang servers are now offline! Falling back to AlwaysOnline authentication.";
	@ConfigIgnore
	static final String DEFAULT_MESSAGE_ONLINE = "Mojang servers are back online! Normal authentication restored.";

	@ConfigComment("The delay to wait between checking to see if mojang is online, in seconds.")
	public int checkInterval = 60;

	@ConfigComment("How the plugin decides whether mojang is online.")
	public Checks checks = new Checks();

	@ConfigComment("Messages shown to players. Colour codes use &. Where noted, null disables the message.")
	public Messages messages = new Messages();

	@ConfigComment({"Status change notifications, sent when mojang servers go offline or come back online.", "Every method is optional and disabled until configured."})
	public Notifications notifications = new Notifications();

	@ConfigComment("Where player data is kept. When neither database is enabled, a file in the plugin folder is used.")
	public Storage storage = new Storage();

	public static final class Checks {

		@ConfigComment({"Session server check, using the status information located at https://sessionserver.mojang.com/", "When every enabled check reports the session servers as down, the plugin goes into mojang offline mode."})
		public boolean httpHeadSessionServer = true;

		@ConfigComment({"The lockout duration in minutes when the down detector triggers.", "If the server is detected as down again during this period, the lockout timer resets."})
		public int downDetectorLockoutMinutes = 5;

	}

	public static final class Messages {

		@ConfigComment("The MOTD while mojang servers are offline. Set to null to disable.")
		public String motdOffline = "&eMojang servers are down,\n&ebut you can still connect!";

		@ConfigComment("Shown when a user attempts to login while the mojang servers are offline, but their IP does not match.")
		public String kickIp = "We can not let you join since you are not on the same computer you logged on before!";

		@ConfigComment("Shown when a user is new and logs in while mojang servers are offline.")
		public String kickNew = "We can not let you join because the mojang servers are offline!";

		@ConfigComment("Shown when a user attempts to login with an invalid minecraft username.")
		public String kickInvalid = "Invalid username. Hacking?";

		@ConfigComment("Broadcast when mojang servers go offline. Set to null to disable.")
		public String mojangOffline = "&5[&2AlwaysOnline&5]&a Mojang servers are now offline!";

		@ConfigComment("Broadcast when mojang servers go back online. Set to null to disable.")
		public String mojangOnline = "&5[&2AlwaysOnline&5]&a Mojang servers are now online!";

	}

	public static final class Notifications {

		@ConfigComment({"The message sent by the notification methods below (discord has its own messages).", "Set to null to disable one direction."})
		public String messageOffline = DEFAULT_MESSAGE_OFFLINE;

		public String messageOnline = DEFAULT_MESSAGE_ONLINE;

		@ConfigComment({"Discord webhook. Create one in your Discord server: Channel Settings -> Integrations -> Webhooks.", "Leave webhook-url empty to disable."})
		public Discord discord = new Discord();

		@ConfigComment({"Generic webhook. POSTs {\"plugin\":\"AlwaysOnline\",\"status\":\"offline|online\",\"message\":\"...\",\"timestamp\":\"...\"} as JSON to the URL.", "Works with Slack-compatible endpoints, n8n, Zapier, home automation, custom dashboards etc. Leave empty to disable."})
		public Webhook webhook = new Webhook();

		@ConfigComment({"Telegram bot. Create a bot with @BotFather to get a token, and use @userinfobot to find your chat id.", "Leave either empty to disable."})
		public Telegram telegram = new Telegram();

		@ConfigComment({"ntfy push notifications. Full topic URL, e.g. https://ntfy.sh/your-secret-topic. Leave empty to disable.", "The token is only needed for protected topics."})
		public Ntfy ntfy = new Ntfy();

		@ConfigComment("Pushover. Requires an application token and your user key. Leave either empty to disable.")
		public Pushover pushover = new Pushover();

		@ConfigComment("Gotify. Server base URL, e.g. https://gotify.example.com, and an application token. Leave either empty to disable.")
		public Gotify gotify = new Gotify();

		@ConfigComment("Console commands to run when the status changes, without a leading slash. Leave empty to disable.")
		public Commands commands = new Commands();

		public static final class Discord {

			public String webhookUrl = "";

			@ConfigComment("The username the webhook posts as.")
			public String username = "AlwaysOnline";

			@ConfigComment("Set to null to disable one direction.")
			public String messageOffline = DEFAULT_MESSAGE_OFFLINE;

			public String messageOnline = DEFAULT_MESSAGE_ONLINE;

		}

		public static final class Webhook {

			public String url = "";

		}

		public static final class Telegram {

			public String botToken = "";

			public String chatId = "";

		}

		public static final class Ntfy {

			public String url = "";

			public String token = "";

		}

		public static final class Pushover {

			public String token = "";

			public String user = "";

		}

		public static final class Gotify {

			public String url = "";

			public String token = "";

		}

		public static final class Commands {

			public String offline = "";

			public String online = "";

		}

	}

	public static final class Storage {

		public Mysql mysql = new Mysql();

		public Mongodb mongodb = new Mongodb();

		public static final class Mysql {

			public boolean enabled = false;

			public String host = "127.0.0.1";

			public int port = 3306;

			public String database = "minecraft";

			public String username = "root";

			public String password = "password";

			@ConfigComment("Extra parameters appended to the JDBC connection URL.")
			public String extra = "";

		}

		public static final class Mongodb {

			public boolean enabled = false;

			public String host = "127.0.0.1";

			public int port = 27017;

			public String database = "minecraft";

			public String username = "";

			public String password = "";

			@ConfigComment("When set, used instead of host, port, username and password.")
			public String connectionString = "";

		}

	}

	/**
	 * Restores a default for every option the plugin always needs a value for. YAML lets a key be
	 * written with no value, which loads as null; the old .properties reader could never produce
	 * that, because each read supplied its own default. Options where null means "switched off"
	 * are deliberately left alone.
	 */
	public void applyRequiredDefaults() {
		AlwaysOnlineConfig defaults = new AlwaysOnlineConfig();
		if (this.messages.kickIp == null) this.messages.kickIp = defaults.messages.kickIp;
		if (this.messages.kickNew == null) this.messages.kickNew = defaults.messages.kickNew;
		if (this.messages.kickInvalid == null) this.messages.kickInvalid = defaults.messages.kickInvalid;
		if (this.storage.mysql.host == null) this.storage.mysql.host = defaults.storage.mysql.host;
		if (this.storage.mysql.database == null) this.storage.mysql.database = defaults.storage.mysql.database;
		if (this.storage.mysql.username == null) this.storage.mysql.username = defaults.storage.mysql.username;
		if (this.storage.mysql.password == null) this.storage.mysql.password = defaults.storage.mysql.password;
		if (this.storage.mongodb.host == null) this.storage.mongodb.host = defaults.storage.mongodb.host;
		if (this.storage.mongodb.database == null) this.storage.mongodb.database = defaults.storage.mongodb.database;
	}

	/** True for a message the user has switched off: missing, empty, or the word null. */
	public static boolean disabled(String message) {
		return message == null || message.trim().isEmpty() || message.trim().equalsIgnoreCase("null");
	}

	/** A trimmed value, with a YAML null read as empty so callers can compare without null checks. */
	public static String text(String value) {
		return value == null ? "" : value.trim();
	}

}
