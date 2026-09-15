package me.dablakbandit.ao.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

/**
 * One-time import of the flat config.properties used up to 6.3.x into an {@link AlwaysOnlineConfig}.
 * Every old key maps onto its new home in the YAML schema; keys the old plugin never read are
 * reported so nothing disappears silently.
 */
public final class LegacyPropertiesImporter {

	public static final String LEGACY_FILE = "config.properties";
	public static final String RENAMED_FILE = "config.properties.old";

	public static final class Result {

		/** Old keys whose values were copied into the schema, in file order. */
		public final List<String> imported = new ArrayList<>();
		/** Old keys the schema has no place for. */
		public final List<String> unknown = new ArrayList<>();
		/** Values that could not be converted; the default was kept for those. */
		public final List<String> warnings = new ArrayList<>();

	}

	private interface Setter {

		void apply(AlwaysOnlineConfig config, String value, Result result);

	}

	private static final Map<String, Setter> KEYS = new LinkedHashMap<>();

	static {
		KEYS.put("check-interval", (c, v, r) -> c.checkInterval = integer("check-interval", v, c.checkInterval, r));
		KEYS.put("message-motd-offline", (c, v, r) -> c.messages.motdOffline = v);
		KEYS.put("message-kick-ip", (c, v, r) -> c.messages.kickIp = v);
		KEYS.put("message-kick-new", (c, v, r) -> c.messages.kickNew = v);
		KEYS.put("message-kick-invalid", (c, v, r) -> c.messages.kickInvalid = v);
		KEYS.put("message-mojang-offline", (c, v, r) -> c.messages.mojangOffline = v);
		KEYS.put("message-mojang-online", (c, v, r) -> c.messages.mojangOnline = v);
		KEYS.put("http-head-session-server", (c, v, r) -> c.checks.httpHeadSessionServer = Boolean.parseBoolean(v.trim()));
		KEYS.put("down-detector-lockout-minutes", (c, v, r) -> c.checks.downDetectorLockoutMinutes = integer("down-detector-lockout-minutes", v, c.checks.downDetectorLockoutMinutes, r));
		KEYS.put("notify-message-offline", (c, v, r) -> c.notifications.messageOffline = v);
		KEYS.put("notify-message-online", (c, v, r) -> c.notifications.messageOnline = v);
		KEYS.put("discord-webhook-url", (c, v, r) -> c.notifications.discord.webhookUrl = v);
		KEYS.put("discord-webhook-username", (c, v, r) -> c.notifications.discord.username = v);
		KEYS.put("discord-webhook-message-offline", (c, v, r) -> c.notifications.discord.messageOffline = v);
		KEYS.put("discord-webhook-message-online", (c, v, r) -> c.notifications.discord.messageOnline = v);
		KEYS.put("notify-webhook-url", (c, v, r) -> c.notifications.webhook.url = v);
		KEYS.put("telegram-bot-token", (c, v, r) -> c.notifications.telegram.botToken = v);
		KEYS.put("telegram-chat-id", (c, v, r) -> c.notifications.telegram.chatId = v);
		KEYS.put("ntfy-url", (c, v, r) -> c.notifications.ntfy.url = v);
		KEYS.put("ntfy-token", (c, v, r) -> c.notifications.ntfy.token = v);
		KEYS.put("pushover-token", (c, v, r) -> c.notifications.pushover.token = v);
		KEYS.put("pushover-user", (c, v, r) -> c.notifications.pushover.user = v);
		KEYS.put("gotify-url", (c, v, r) -> c.notifications.gotify.url = v);
		KEYS.put("gotify-token", (c, v, r) -> c.notifications.gotify.token = v);
		KEYS.put("notify-command-offline", (c, v, r) -> c.notifications.commands.offline = v);
		KEYS.put("notify-command-online", (c, v, r) -> c.notifications.commands.online = v);
		KEYS.put("use_mysql", (c, v, r) -> c.storage.mysql.enabled = Boolean.parseBoolean(v.trim()));
		KEYS.put("host", (c, v, r) -> c.storage.mysql.host = v);
		KEYS.put("port", (c, v, r) -> c.storage.mysql.port = integer("port", v, c.storage.mysql.port, r));
		KEYS.put("database-name", (c, v, r) -> c.storage.mysql.database = v);
		KEYS.put("database-username", (c, v, r) -> c.storage.mysql.username = v);
		KEYS.put("database-password", (c, v, r) -> c.storage.mysql.password = v);
		KEYS.put("database-extra", (c, v, r) -> c.storage.mysql.extra = v);
		KEYS.put("use_mongodb", (c, v, r) -> c.storage.mongodb.enabled = Boolean.parseBoolean(v.trim()));
		KEYS.put("mongo-host", (c, v, r) -> c.storage.mongodb.host = v);
		KEYS.put("mongo-port", (c, v, r) -> c.storage.mongodb.port = integer("mongo-port", v, c.storage.mongodb.port, r));
		KEYS.put("mongo-database", (c, v, r) -> c.storage.mongodb.database = v);
		KEYS.put("mongo-username", (c, v, r) -> c.storage.mongodb.username = v);
		KEYS.put("mongo-password", (c, v, r) -> c.storage.mongodb.password = v);
		KEYS.put("mongo-connection-string", (c, v, r) -> c.storage.mongodb.connectionString = v);
		// The old file's version marker has no meaning in the YAML schema.
		KEYS.put("config_version", (c, v, r) -> {
		});
	}

	private LegacyPropertiesImporter() {
	}

	/**
	 * Copies the values in {@code propertiesFile} onto {@code config}. The file is decoded as UTF-8
	 * when its bytes are valid UTF-8, and as ISO-8859-1 otherwise, which is what Properties has
	 * always assumed. That differs from the old plugin on purpose: it read raw UTF-8 characters as
	 * several wrong ones. ASCII and backslash-u escapes come through identically either way.
	 */
	public static Result importInto(AlwaysOnlineConfig config, Path propertiesFile) throws IOException {
		// Server owners save this file from ordinary editors, so decorative characters such as the
		// fullwidth brackets U+3010/U+3011 arrive as raw UTF-8. Properties.load(InputStream) always
		// reads ISO-8859-1, which splits each of those into three wrong characters, so decode the
		// bytes as UTF-8 when they are valid UTF-8 and only fall back to ISO-8859-1 when they are
		// not. Plain ASCII and \\uXXXX escapes read identically either way. A leading BOM is dropped
		// first: it would otherwise fold into the first key's name and lose that setting.
		byte[] bytes = Files.readAllBytes(propertiesFile);
		int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
		Properties properties = new Properties();
		properties.load(new StringReader(decode(bytes, offset)));
		Result result = new Result();
		for (Map.Entry<String, Setter> entry : KEYS.entrySet()) {
			String value = properties.getProperty(entry.getKey());
			if (value == null) continue;
			entry.getValue().apply(config, value, result);
			if (!"config_version".equals(entry.getKey())) result.imported.add(entry.getKey());
		}
		for (String key : new TreeSet<>(properties.stringPropertyNames())) {
			if (!KEYS.containsKey(key)) result.unknown.add(key);
		}
		return result;
	}

	private static String decode(byte[] bytes, int offset) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(buffer)
					.toString();
		} catch (CharacterCodingException notUtf8) {
			return new String(bytes, offset, bytes.length - offset, StandardCharsets.ISO_8859_1);
		}
	}

	private static int integer(String key, String value, int fallback, Result result) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			result.warnings.add("Ignoring '" + value + "' for " + key + " in " + LEGACY_FILE + ": expected a whole number, keeping " + fallback + ".");
			return fallback;
		}
	}

}
