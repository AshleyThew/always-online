package me.dablakbandit.ao.notifications;

import me.dablakbandit.ao.NativeExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

public class NotificationManager {

	private final List<StatusNotifier> notifiers = new ArrayList<>();

	public NotificationManager(NativeExecutor nativeExecutor, Properties config) {
		this.register(new DiscordWebhookNotifier(nativeExecutor, config));
		this.register(new GenericWebhookNotifier(nativeExecutor, config));
		this.register(new TelegramNotifier(nativeExecutor, config));
		this.register(new NtfyNotifier(nativeExecutor, config));
		this.register(new PushoverNotifier(nativeExecutor, config));
		this.register(new GotifyNotifier(nativeExecutor, config));
		this.register(new CommandNotifier(nativeExecutor, config));
		if (!this.notifiers.isEmpty()) {
			StringBuilder names = new StringBuilder();
			for (StatusNotifier notifier : this.notifiers) {
				if (names.length() > 0) names.append(", ");
				names.append(notifier.name());
			}
			nativeExecutor.log(Level.INFO, "Status change notifications enabled: " + names);
		}
	}

	private void register(StatusNotifier notifier) {
		if (notifier.isEnabled()) this.notifiers.add(notifier);
	}

	public void notifyOffline() {
		for (StatusNotifier notifier : this.notifiers) {
			notifier.notifyOffline();
		}
	}

	public void notifyOnline() {
		for (StatusNotifier notifier : this.notifiers) {
			notifier.notifyOnline();
		}
	}

}
