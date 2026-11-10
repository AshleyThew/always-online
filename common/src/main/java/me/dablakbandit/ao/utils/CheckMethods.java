package me.dablakbandit.ao.utils;

import me.dablakbandit.ao.hybrid.IAlwaysOnline;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class CheckMethods {

	// The endpoint the server itself calls when a player logs in. Asked about a session that does not exist,
	// a healthy session server answers 204 No Content. Probing this rather than a profile lookup matters:
	// profile lookups are cached and stay up through outages where logins are failing.
	private static final String HAS_JOINED_URL = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=AlwaysOnline&serverId=";

	// Without these a session server that accepts the connection but never answers (common when it is
	// overloaded) holds the check forever, and the outage is never noticed.
	private static final int CONNECT_TIMEOUT_MILLIS = 5000;
	private static final int READ_TIMEOUT_MILLIS = 10000;

	/**
	 * Asks the session server the same question the server asks when a player logs in.
	 *
	 * @return null when it answered normally, otherwise a short description of what went wrong (an error
	 * status, a rate limit, a refused connection or no answer in time)
	 */
	public static String sessionServerProblem(IAlwaysOnline alwaysOnline) {
		HttpURLConnection con = null;
		try {
			URL url = new URL(HAS_JOINED_URL + Long.toHexString(ThreadLocalRandom.current().nextLong()));
			con = (HttpURLConnection) url.openConnection();
			con.setUseCaches(false);
			con.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
			con.setReadTimeout(READ_TIMEOUT_MILLIS);
			con.setRequestMethod("GET");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("Connection", "close");
			con.setRequestProperty("User-Agent", "AlwaysOnline");
			int code = con.getResponseCode();
			drain(con, code);
			boolean up = code == HttpURLConnection.HTTP_NO_CONTENT || code == HttpURLConnection.HTTP_OK;
			if (alwaysOnline.isDebug()) {
				alwaysOnline.getNativeExecutor().log(Level.INFO, "Session server login check answered HTTP " + code);
			}
			return up ? null : "the login check answered HTTP " + code;
		} catch (IOException e) {
			if (alwaysOnline.isDebug()) {
				alwaysOnline.getNativeExecutor().log(Level.INFO, "Session server login check failed: " + e);
			}
			return "the login check failed: " + e;
		} finally {
			if (con != null) con.disconnect();
		}
	}

	private static void drain(HttpURLConnection con, int code) {
		try {
			InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
			if (in == null) return;
			byte[] buffer = new byte[1024];
			while (in.read(buffer) != -1) {
				// discard
			}
			in.close();
		} catch (IOException ignored) {
		}
	}

}
