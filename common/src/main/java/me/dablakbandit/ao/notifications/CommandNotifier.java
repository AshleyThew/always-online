package me.dablakbandit.ao.notifications;

import me.dablakbandit.ao.NativeExecutor;

import java.util.Properties;
import java.util.logging.Level;

public class CommandNotifier implements StatusNotifier {

	private final NativeExecutor nativeExecutor;
	private final String commandOffline, commandOnline;

	public CommandNotifier(NativeExecutor nativeExecutor, Properties config) {
		this.nativeExecutor = nativeExecutor;
		this.commandOffline = config.getProperty("notify-command-offline", "").trim();
		this.commandOnline = config.getProperty("notify-command-online", "").trim();
	}

	@Override
	public String name() {
		return "console command";
	}

	@Override
	public boolean isEnabled() {
		return this.hasCommand(this.commandOffline) || this.hasCommand(this.commandOnline);
	}

	@Override
	public void notifyOffline() {
		this.dispatch(this.commandOffline);
	}

	@Override
	public void notifyOnline() {
		this.dispatch(this.commandOnline);
	}

	private boolean hasCommand(String command) {
		return !command.isEmpty() && !"null".equals(command);
	}

	private void dispatch(String command) {
		if (!this.hasCommand(command)) return;
		try {
			this.nativeExecutor.dispatchConsoleCommand(command);
		} catch (Exception e) {
			this.nativeExecutor.log(Level.WARNING, "Failed to run notification command '" + command + "'. [" + e.getMessage() + "]");
		}
	}

}
