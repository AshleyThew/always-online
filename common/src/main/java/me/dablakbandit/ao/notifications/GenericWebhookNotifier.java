package me.dablakbandit.ao.notifications;

import com.google.gson.Gson;
import me.dablakbandit.ao.NativeExecutor;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class GenericWebhookNotifier extends AbstractStatusNotifier {

	private final Gson gson = new Gson();
	private final String webhookUrl;

	public GenericWebhookNotifier(NativeExecutor nativeExecutor, Properties config) {
		super(nativeExecutor, config);
		this.webhookUrl = config.getProperty("notify-webhook-url", "").trim();
	}

	@Override
	public String name() {
		return "generic webhook";
	}

	@Override
	public boolean isEnabled() {
		return !this.webhookUrl.isEmpty() && !"null".equals(this.webhookUrl);
	}

	@Override
	protected void sendNotification(boolean offline, String message) throws IOException {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("plugin", "AlwaysOnline");
		payload.put("status", offline ? "offline" : "online");
		payload.put("message", message);
		payload.put("timestamp", Instant.now().toString());
		NotificationHttp.post(this.webhookUrl, "application/json", this.gson.toJson(payload), null);
	}

}
