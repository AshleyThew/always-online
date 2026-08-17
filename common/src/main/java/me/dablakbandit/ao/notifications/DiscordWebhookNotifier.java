package me.dablakbandit.ao.notifications;

import com.google.gson.Gson;
import me.dablakbandit.ao.NativeExecutor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

public class DiscordWebhookNotifier {

	private static final int COLOR_OFFLINE = 0xE74C3C;
	private static final int COLOR_ONLINE = 0x2ECC71;

	private final NativeExecutor nativeExecutor;
	private final Gson gson = new Gson();
	private final String webhookUrl;
	private final String username;
	private final String messageOffline, messageOnline;

	public DiscordWebhookNotifier(NativeExecutor nativeExecutor, Properties config) {
		this.nativeExecutor = nativeExecutor;
		this.webhookUrl = config.getProperty("discord-webhook-url", "").trim();
		this.username = config.getProperty("discord-webhook-username", "AlwaysOnline").trim();
		this.messageOffline = config.getProperty("discord-webhook-message-offline", "Mojang servers are now offline! Falling back to AlwaysOnline authentication.");
		this.messageOnline = config.getProperty("discord-webhook-message-online", "Mojang servers are back online! Normal authentication restored.");
		if (this.isEnabled()) {
			this.nativeExecutor.log(Level.INFO, "Discord webhook notifications enabled.");
		}
	}

	public boolean isEnabled() {
		return !this.webhookUrl.isEmpty() && !"null".equals(this.webhookUrl);
	}

	public void notifyOffline() {
		this.send(this.messageOffline, COLOR_OFFLINE);
	}

	public void notifyOnline() {
		this.send(this.messageOnline, COLOR_ONLINE);
	}

	// Callers are expected to invoke this from an async thread.
	private void send(String message, int color) {
		if (!this.isEnabled() || "null".equals(message)) return;
		Map<String, Object> embed = new LinkedHashMap<>();
		embed.put("description", message);
		embed.put("color", color);
		embed.put("timestamp", Instant.now().toString());
		List<Map<String, Object>> embeds = new ArrayList<>();
		embeds.add(embed);
		Map<String, Object> payload = new LinkedHashMap<>();
		if (!this.username.isEmpty()) payload.put("username", this.username);
		payload.put("embeds", embeds);
		try {
			this.post(this.gson.toJson(payload));
		} catch (IOException e) {
			this.nativeExecutor.log(Level.WARNING, "Failed to send Discord webhook notification. [" + e.getMessage() + "]");
		}
	}

	private void post(String jsonPayload) throws IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(this.webhookUrl).openConnection();
		con.setConnectTimeout(10000);
		con.setReadTimeout(10000);
		con.setRequestMethod("POST");
		con.setRequestProperty("Content-Type", "application/json");
		con.setRequestProperty("User-Agent", "AlwaysOnline");
		con.setDoOutput(true);
		OutputStream out = con.getOutputStream();
		out.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
		out.flush();
		out.close();
		int responseCode = con.getResponseCode();
		if (responseCode < 200 || responseCode >= 300) {
			throw new IOException("Discord responded with HTTP " + responseCode);
		}
		con.disconnect();
	}

}
