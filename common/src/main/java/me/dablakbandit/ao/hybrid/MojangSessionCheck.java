package me.dablakbandit.ao.hybrid;

import me.dablakbandit.ao.config.AlwaysOnlineConfig;
import me.dablakbandit.ao.notifications.NotificationManager;
import me.dablakbandit.ao.utils.CheckMethods;

import java.util.logging.Level;

public class MojangSessionCheck implements Runnable {

	private final AlwaysOnline alwaysOnline;
	private final String messageMojangOffline, messageMojangOnline;
	private final long lockoutDurationMillis;
	private final NotificationManager notificationManager;
	private long lockoutEndTime = 0;
	// While the mode is set by hand: what the admin was last told, starting from the mode they chose, so that
	// pinning offline mode on while mojang looks fine still produces a "back online" alert.
	private boolean alertedOffline;
	private int seenOverrideGeneration = -1;

	public MojangSessionCheck(AlwaysOnline alwaysOnline) {
		this.alwaysOnline = alwaysOnline;
		if (!this.alwaysOnline.config.checks.httpHeadSessionServer) {
			this.alwaysOnline.nativeExecutor.log(Level.WARNING, "No check methods have been enabled in the configuration. Going to enable the session server check.");
		}
		this.alwaysOnline.nativeExecutor.log(Level.INFO, "Session server check: enabled");
		this.messageMojangOffline = this.alwaysOnline.config.messages.mojangOffline;
		this.messageMojangOnline = this.alwaysOnline.config.messages.mojangOnline;
		int lockoutMinutes = this.alwaysOnline.config.checks.downDetectorLockoutMinutes;
		this.lockoutDurationMillis = lockoutMinutes * 60 * 1000L;
		this.alwaysOnline.nativeExecutor.log(Level.INFO, "Down detector lockout duration: " + lockoutMinutes + " minutes");
		this.notificationManager = new NotificationManager(this.alwaysOnline.nativeExecutor, this.alwaysOnline.config.notifications);
	}

	@Override
	public void run() {
		if (!alwaysOnline.getCheckSessionStatus()) return;
		String problem = CheckMethods.sessionServerProblem(alwaysOnline);
		alwaysOnline.recordCheck(problem);
		long currentTime = System.currentTimeMillis();
		if (problem != null) {
			// Reset lockout timer when down is detected (whether first time or again during lockout)
			this.lockoutEndTime = currentTime + this.lockoutDurationMillis;
		}
		// Offline as soon as a check fails; back online only once the lockout since the last failure has passed.
		boolean offline = problem != null || currentTime < this.lockoutEndTime;
		String reason = problem != null ? problem : "a check failed within the last " + (this.lockoutDurationMillis / 60000) + " minutes";

		if (alwaysOnline.isManualOverride()) {
			// The admin chose the mode by hand; leave it alone but still tell them, so they know to switch back.
			int generation = alwaysOnline.getOverrideGeneration();
			if (generation != this.seenOverrideGeneration) {
				this.seenOverrideGeneration = generation;
				this.alertedOffline = alwaysOnline.getOfflineMode();
			}
			if (offline == this.alertedOffline) return;
			this.alertedOffline = offline;
			String pinned = alwaysOnline.getOfflineMode() ? "on" : "off";
			if (offline) {
				this.alwaysOnline.nativeExecutor.log(Level.WARNING, "Mojang servers appear to be offline (" + reason + "). Offline mode was set by hand with /alwaysonline toggle and stays " + pinned + " until it is toggled again.");
				this.notificationManager.notifyOffline();
			} else {
				this.alwaysOnline.nativeExecutor.log(Level.WARNING, "Mojang servers appear to be back online. Offline mode was set by hand with /alwaysonline toggle and stays " + pinned + " until it is toggled again.");
				this.notificationManager.notifyOnline();
			}
			return;
		}

		if (!alwaysOnline.setOfflineMode(offline)) return;
		this.alwaysOnline.saveState();
		if (offline) {
			this.alwaysOnline.nativeExecutor.log(Level.INFO, "Mojang servers appear to be offline (" + reason + "). Enabling mojang offline mode...");
			this.alwaysOnline.warnIfFallbackDatabase();
			if (!AlwaysOnlineConfig.disabled(this.messageMojangOffline))
				this.alwaysOnline.nativeExecutor.broadcastMessage(this.messageMojangOffline);
			this.notificationManager.notifyOffline();
		} else {
			this.alwaysOnline.nativeExecutor.log(Level.INFO, "Mojang servers appear to be online. Disabling mojang offline mode...");
			if (!AlwaysOnlineConfig.disabled(this.messageMojangOnline))
				this.alwaysOnline.nativeExecutor.broadcastMessage(this.messageMojangOnline);
			this.notificationManager.notifyOnline();
		}
	}

}
