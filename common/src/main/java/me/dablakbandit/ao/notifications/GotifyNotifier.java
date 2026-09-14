package me.dablakbandit.ao.notifications;

import com.google.gson.Gson;
import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.config.AlwaysOnlineConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GotifyNotifier extends AbstractStatusNotifier {

	private final Gson gson = new Gson();
	private final String serverUrl;
	private final String token;

	public GotifyNotifier(NativeExecutor nativeExecutor, AlwaysOnlineConfig.Notifications config) {
		super(nativeExecutor, config);
		String url = AlwaysOnlineConfig.text(config.gotify.url);
		while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		this.serverUrl = url;
		this.token = AlwaysOnlineConfig.text(config.gotify.token);
	}

	@Override
	public String name() {
		return "Gotify";
	}

	@Override
	public boolean isEnabled() {
		return !this.serverUrl.isEmpty() && !this.token.isEmpty();
	}

	@Override
	protected void sendNotification(boolean offline, String message) throws IOException {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("title", "AlwaysOnline");
		payload.put("message", message);
		payload.put("priority", offline ? 8 : 5);
		NotificationHttp.post(this.serverUrl + "/message", "application/json", this.gson.toJson(payload), Collections.singletonMap("X-Gotify-Key", this.token));
	}

}
