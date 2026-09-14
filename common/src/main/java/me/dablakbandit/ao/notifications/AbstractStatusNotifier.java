package me.dablakbandit.ao.notifications;

import me.dablakbandit.ao.NativeExecutor;
import me.dablakbandit.ao.config.AlwaysOnlineConfig;

import java.util.logging.Level;

public abstract class AbstractStatusNotifier implements StatusNotifier {

	protected final NativeExecutor nativeExecutor;
	protected final String messageOffline, messageOnline;

	protected AbstractStatusNotifier(NativeExecutor nativeExecutor, AlwaysOnlineConfig.Notifications config) {
		this(nativeExecutor, config.messageOffline, config.messageOnline);
	}

	protected AbstractStatusNotifier(NativeExecutor nativeExecutor, String messageOffline, String messageOnline) {
		this.nativeExecutor = nativeExecutor;
		this.messageOffline = messageOffline;
		this.messageOnline = messageOnline;
	}

	@Override
	public void notifyOffline() {
		this.send(true, this.messageOffline);
	}

	@Override
	public void notifyOnline() {
		this.send(false, this.messageOnline);
	}

	private void send(boolean offline, String message) {
		if (!this.isEnabled() || AlwaysOnlineConfig.disabled(message)) return;
		try {
			this.sendNotification(offline, message);
		} catch (Exception e) {
			this.nativeExecutor.log(Level.WARNING, "Failed to send " + this.name() + " notification. [" + e.getMessage() + "]");
		}
	}

	// Callers are expected to invoke notifyOffline/notifyOnline from an async thread.
	protected abstract void sendNotification(boolean offline, String message) throws Exception;

}
