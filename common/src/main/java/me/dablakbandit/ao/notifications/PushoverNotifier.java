package me.dablakbandit.ao.notifications;

import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.config.AlwaysOnlineConfig;

import java.io.IOException;

public class PushoverNotifier extends AbstractStatusNotifier {

	private final String token;
	private final String user;

	public PushoverNotifier(NativeExecutor nativeExecutor, AlwaysOnlineConfig.Notifications config) {
		super(nativeExecutor, config);
		this.token = AlwaysOnlineConfig.text(config.pushover.token);
		this.user = AlwaysOnlineConfig.text(config.pushover.user);
	}

	@Override
	public String name() {
		return "Pushover";
	}

	@Override
	public boolean isEnabled() {
		return !this.token.isEmpty() && !this.user.isEmpty();
	}

	@Override
	protected void sendNotification(boolean offline, String message) throws IOException {
		String body = "token=" + NotificationHttp.urlEncode(this.token) + "&user=" + NotificationHttp.urlEncode(this.user) + "&title=" + NotificationHttp.urlEncode("AlwaysOnline") + "&message=" + NotificationHttp.urlEncode(message);
		NotificationHttp.post("https://api.pushover.net/1/messages.json", "application/x-www-form-urlencoded", body, null);
	}

}
