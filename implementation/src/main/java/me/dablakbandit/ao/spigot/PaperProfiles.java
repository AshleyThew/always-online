package me.dablakbandit.ao.spigot;

import me.dablakbandit.ao.databases.Skin;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

/**
 * Paper lets AsyncPlayerPreLoginEvent change the profile the player logs in with, and uses whatever the
 * event ends with. Setting the stored UUID (and saved skin) there does not depend on the network hook
 * winning a race, so it is the reliable way to give a player back their identity during an outage.
 * Plain Spigot has no such API, so everything here is looked up by reflection and simply does nothing when
 * it is missing.
 */
final class PaperProfiles {

	private static final Method GET_PROFILE;
	private static final Method SET_PROFILE;
	private static final Method SET_ID;
	private static final Method GET_PROPERTIES;
	private static final Method SET_PROPERTY;
	private static final Method HAS_PROPERTY;
	private static final Constructor<?> NEW_PROPERTY;
	private static final Method PROPERTY_NAME, PROPERTY_VALUE, PROPERTY_SIGNATURE;
	private static final Method PLAYER_PROFILE;

	static {
		Method get = null, set = null, setId = null, getProperties = null, setProperty = null, hasProperty = null;
		Method propertyName = null, propertyValue = null, propertySignature = null, playerProfile = null;
		Constructor<?> newProperty = null;
		try {
			Class<?> profileClass = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
			Class<?> propertyClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
			get = AsyncPlayerPreLoginEvent.class.getMethod("getPlayerProfile");
			set = AsyncPlayerPreLoginEvent.class.getMethod("setPlayerProfile", profileClass);
			setId = profileClass.getMethod("setId", UUID.class);
			getProperties = profileClass.getMethod("getProperties");
			setProperty = profileClass.getMethod("setProperty", propertyClass);
			hasProperty = profileClass.getMethod("hasProperty", String.class);
			newProperty = propertyClass.getConstructor(String.class, String.class, String.class);
			propertyName = propertyClass.getMethod("getName");
			propertyValue = propertyClass.getMethod("getValue");
			propertySignature = propertyClass.getMethod("getSignature");
			playerProfile = Player.class.getMethod("getPlayerProfile");
		} catch (ReflectiveOperationException | LinkageError ignored) {
			get = null;
		}
		GET_PROFILE = get;
		SET_PROFILE = set;
		SET_ID = setId;
		GET_PROPERTIES = getProperties;
		SET_PROPERTY = setProperty;
		HAS_PROPERTY = hasProperty;
		NEW_PROPERTY = newProperty;
		PROPERTY_NAME = propertyName;
		PROPERTY_VALUE = propertyValue;
		PROPERTY_SIGNATURE = propertySignature;
		PLAYER_PROFILE = playerProfile;
	}

	private PaperProfiles() {
	}

	static boolean available() {
		return GET_PROFILE != null;
	}

	/**
	 * Gives the logging-in player their stored UUID and, if the profile has none, their saved skin.
	 *
	 * @param uuid null to leave the UUID alone
	 * @param skin null to leave the skin alone
	 * @return true when the profile was changed
	 */
	static boolean apply(AsyncPlayerPreLoginEvent event, UUID uuid, Skin skin) {
		if (GET_PROFILE == null || (uuid == null && skin == null)) return false;
		try {
			Object profile = GET_PROFILE.invoke(event);
			boolean changed = false;
			if (uuid != null) {
				SET_ID.invoke(profile, uuid);
				changed = true;
			}
			if (skin != null && !((Boolean) HAS_PROPERTY.invoke(profile, Skin.PROPERTY))) {
				SET_PROPERTY.invoke(profile, NEW_PROPERTY.newInstance(Skin.PROPERTY, skin.value, skin.signature));
				changed = true;
			}
			if (changed) SET_PROFILE.invoke(event, profile);
			return changed;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * @return the player's current skin, or null when it cannot be read (plain Spigot, or no skin)
	 */
	static Skin skinOf(Player player) {
		if (PLAYER_PROFILE == null) return null;
		try {
			Object profile = PLAYER_PROFILE.invoke(player);
			for (Object property : (Collection<?>) GET_PROPERTIES.invoke(profile)) {
				if (Skin.PROPERTY.equals(PROPERTY_NAME.invoke(property))) {
					return Skin.of((String) PROPERTY_VALUE.invoke(property), (String) PROPERTY_SIGNATURE.invoke(property));
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
		return null;
	}
}
