package me.dablakbandit.ao.notifications;

import me.dablakbandit.ao.NativeExecutor;

import java.util.Properties;
import java.util.logging.Level;

public abstract class AbstractStatusNotifier implements StatusNotifier {

	public static final String DEFAULT_MESSAGE_OFFLINE = "Mojang servers are now offline! Falling back to AlwaysOnline authentication.";
	public static final String DEFAULT_MESSAGE_ONLINE = "Mojang servers are back online! Normal authentication restored.";

	protected final NativeExecutor nativeExecutor;
	protected final String messageOffline, messageOnline;

	protected AbstractStatusNotifier(NativeExecutor nativeExecutor, Properties config) {
		this(nativeExecutor, config.getProperty("notify-message-offline", DEFAULT_MESSAGE_OFFLINE), config.getProperty("notify-message-online", DEFAULT_MESSAGE_ONLINE));
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
		if (!this.isEnabled() || "null".equals(message)) return;
		try {
			this.sendNotification(offline, message);
		} catch (Exception e) {
			this.nativeExecutor.log(Level.WARNING, "Failed to send " + this.name() + " notification. [" + e.getMessage() + "]");
		}
	}

	// Callers are expected to invoke notifyOffline/notifyOnline from an async thread.
	protected abstract void sendNotification(boolean offline, String message) throws Exception;

}
