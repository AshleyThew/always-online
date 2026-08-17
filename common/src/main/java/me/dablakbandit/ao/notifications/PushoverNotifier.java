package me.dablakbandit.ao.notifications;

import me.dablakbandit.ao.NativeExecutor;

import java.io.IOException;
import java.util.Properties;

public class PushoverNotifier extends AbstractStatusNotifier {

	private final String token;
	private final String user;

	public PushoverNotifier(NativeExecutor nativeExecutor, Properties config) {
		super(nativeExecutor, config);
		this.token = config.getProperty("pushover-token", "").trim();
		this.user = config.getProperty("pushover-user", "").trim();
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
