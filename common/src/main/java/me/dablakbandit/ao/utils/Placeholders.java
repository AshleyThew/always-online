package me.dablakbandit.ao.utils;

public class Placeholders {

	/**
	 * Replaces the supported message placeholders with the values for the connecting player.
	 * Supported placeholders: {player}, {player_name}, {player_ip}, {last_ip}
	 *
	 * @param message  the raw configured message
	 * @param username the connecting player's username
	 * @param ip       the connecting player's ip address
	 * @param lastIp   the last known ip address for the player, may be null
	 * @return the message with placeholders replaced, or null if the message was null
	 */
	public static String apply(String message, String username, String ip, String lastIp) {
		if (message == null) return null;
		return message
				.replace("{player}", username == null ? "unknown" : username)
				.replace("{player_name}", username == null ? "unknown" : username)
				.replace("{player_ip}", ip == null ? "unknown" : ip)
				.replace("{last_ip}", lastIp == null ? "unknown" : lastIp);
	}
}
