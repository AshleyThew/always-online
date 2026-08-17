package me.dablakbandit.ao.notifications;

public interface StatusNotifier {

	String name();

	boolean isEnabled();

	void notifyOffline();

	void notifyOnline();

}
