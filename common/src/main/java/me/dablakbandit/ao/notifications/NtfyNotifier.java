package me.dablakbandit.ao.notifications;

import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.config.AlwaysOnlineConfig;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class NtfyNotifier extends AbstractStatusNotifier {

	private final String topicUrl;
	private final String token;

	public NtfyNotifier(NativeExecutor nativeExecutor, AlwaysOnlineConfig.Notifications config) {
		super(nativeExecutor, config);
		this.topicUrl = AlwaysOnlineConfig.text(config.ntfy.url);
		this.token = AlwaysOnlineConfig.text(config.ntfy.token);
	}

	@Override
	public String name() {
		return "ntfy";
	}

	@Override
	public boolean isEnabled() {
		return !this.topicUrl.isEmpty() && !"null".equals(this.topicUrl);
	}

	@Override
	protected void sendNotification(boolean offline, String message) throws IOException {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Title", "AlwaysOnline");
		headers.put("Tags", offline ? "red_circle" : "green_circle");
		headers.put("Priority", offline ? "high" : "default");
		if (!this.token.isEmpty()) headers.put("Authorization", "Bearer " + this.token);
		NotificationHttp.post(this.topicUrl, "text/plain", message, headers);
	}

}
