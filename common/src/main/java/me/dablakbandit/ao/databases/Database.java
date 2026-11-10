package me.dablakbandit.ao.databases;

import java.net.InetAddress;
import java.util.UUID;

public interface Database {

	String getIP(String username);

	UUID getUUID(String username);

	/**
	 * @return the skin saved on the player's last normal join, or null if none was saved
	 */
	Skin getSkin(String username);

	/**
	 * Caches the information and updates the database, keeping any skin already saved.
	 * Highly recommended to run async
	 *
	 * @param username The username
	 * @param ip       The IP
	 * @param uuid     The UUID
	 */
	default void updatePlayer(String username, String ip, UUID uuid) {
		this.updatePlayer(username, ip, uuid, null);
	}

	/**
	 * Caches the information and updates the database.
	 * Highly recommended to run async
	 *
	 * @param skin the player's current skin, or null to keep whatever is already saved
	 */
	void updatePlayer(String username, String ip, UUID uuid, Skin skin);

	/**
	 * Saves the data.
	 * Can be safely ran asynchronously
	 */
	void save() throws Exception;

	/**
	 * Resets the database cache. Mainly used for debugging.
	 */
	void resetCache();

	/**
	 * Closes the database connections/streams
	 * This method does not save any data
	 */
	void close();

	/**
	 * A short name for /alwaysonline status.
	 */
	String describe();

	default boolean isValidIP(String ip) {
		try {
			InetAddress.getByName(ip);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
