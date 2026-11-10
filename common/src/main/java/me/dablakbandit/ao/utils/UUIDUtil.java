package me.dablakbandit.ao.utils;

import java.util.UUID;

public class UUIDUtil {

	/**
	 * Validates if a string is a valid UUID format
	 *
	 * @param uuidString The string to validate
	 * @return true if the string is a valid UUID, false otherwise
	 */
	public static boolean isValidUUID(String uuidString) {
		try {
			UUID.fromString(uuidString);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Floodgate (Geyser) gives Bedrock players a UUID built from their Xbox id, with the upper 64 bits all
	 * zero. A Mojang UUID is random (version 4), so it never looks like this. Bedrock players sign in through
	 * Xbox, not Mojang, so a mojang outage does not affect them and AlwaysOnline must leave them alone.
	 */
	public static boolean isFloodgateId(UUID uuid) {
		return uuid != null && uuid.getMostSignificantBits() == 0;
	}
}
