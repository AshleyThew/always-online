package me.dablakbandit.ao;

import me.dablakbandit.ao.hybrid.IAlwaysOnline;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public interface NativeExecutor {

	Object runAsyncRepeating(Runnable runnable, long delay, long period, TimeUnit timeUnit);

	void cancelTask(Object taskID);

	void cancelAllOurTasks();

	void unregisterAllListeners();

	void log(Level level, String message);

	Path dataFolder();

	void disablePlugin();

	void registerListener();

	void broadcastMessage(String message);

	IAlwaysOnline getAOInstance();

	String getVersion();

	void notifyOfflineMode(boolean offlineMode);

	default void initMySQL() {

	}
}
