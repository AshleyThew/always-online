package me.dablakbandit.ao.notifications;

import com.google.gson.Gson;
import me.dablakbandit.ao.NativeExecutor;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class DiscordWebhookNotifier extends AbstractStatusNotifier {

	private static final int COLOR_OFFLINE = 0xE74C3C;
	private static final int COLOR_ONLINE = 0x2ECC71;

	private final Gson gson = new Gson();
	private final String webhookUrl;
	private final String username;

	public DiscordWebhookNotifier(NativeExecutor nativeExecutor, Properties config) {
		super(nativeExecutor, config.getProperty("discord-webhook-message-offline", DEFAULT_MESSAGE_OFFLINE), config.getProperty("discord-webhook-message-online", DEFAULT_MESSAGE_ONLINE));
		this.webhookUrl = config.getProperty("discord-webhook-url", "").trim();
		this.username = config.getProperty("discord-webhook-username", "AlwaysOnline").trim();
	}

	@Override
	public String name() {
		return "Discord webhook";
	}

	@Override
	public boolean isEnabled() {
		return !this.webhookUrl.isEmpty() && !"null".equals(this.webhookUrl);
	}

	@Override
	protected void sendNotification(boolean offline, String message) throws IOException {
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("description", message);
		embed.put("color", offline ? COLOR_OFFLINE : COLOR_ONLINE);
		embed.put("timestamp", Instant.now().toString());
		List<Map<String, Object>> embeds = new ArrayList<>();
		embeds.add(embed);
		Map<String, Object> payload = new LinkedHashMap<>();
		if (!this.username.isEmpty()) payload.put("username", this.username);
		payload.put("embeds", embeds);
		NotificationHttp.post(this.webhookUrl, "application/json", this.gson.toJson(payload), null);
	}

}
