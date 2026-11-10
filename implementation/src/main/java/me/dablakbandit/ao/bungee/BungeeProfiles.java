package me.dablakbandit.ao.bungee;

import me.dablakbandit.ao.databases.Skin;
import net.md_5.bungee.connection.InitialHandler;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reads and writes the login profile BungeeCord keeps for a connection: the profile Mojang returned, with the
 * player's skin. Without Mojang it stays empty, so the player has no skin; giving it the saved skin fixes
 * that, and with ip_forward on BungeeCord passes it on to the backend servers.
 * The property class has moved between BungeeCord versions, so everything is found by reflection.
 */
final class BungeeProfiles {

	private static Constructor<?> loginResultConstructor;
	private static Constructor<?> propertyConstructor;
	private static Class<?> propertyClass;
	private static Field loginProfileField;
	private static boolean resolved;

	private BungeeProfiles() {
	}

	private static synchronized boolean resolve() {
		if (resolved) return loginResultConstructor != null;
		resolved = true;
		try {
			Class<?> loginResult = Class.forName("net.md_5.bungee.connection.LoginResult");
			propertyClass = loginResult.getMethod("getProperties").getReturnType().getComponentType();
			Class<?> propertyArray = Array.newInstance(propertyClass, 0).getClass();
			loginResultConstructor = loginResult.getConstructor(String.class, String.class, propertyArray);
			propertyConstructor = propertyClass.getConstructor(String.class, String.class, String.class);
			loginProfileField = InitialHandler.class.getDeclaredField("loginProfile");
			loginProfileField.setAccessible(true);
			return true;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			loginResultConstructor = null;
			return false;
		}
	}

	/**
	 * @return the skin Mojang sent for this connection, or null
	 */
	static Skin skinOf(InitialHandler handler) {
		try {
			Object profile = handler.getLoginProfile();
			if (profile == null) return null;
			Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
			if (properties == null) return null;
			for (int i = 0; i < Array.getLength(properties); i++) {
				Object property = Array.get(properties, i);
				Class<?> type = property.getClass();
				if (Skin.PROPERTY.equals(type.getMethod("getName").invoke(property))) {
					return Skin.of((String) type.getMethod("getValue").invoke(property), (String) type.getMethod("getSignature").invoke(property));
				}
			}
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
		}
		return null;
	}

	/**
	 * Gives a connection that skipped Mojang a login profile carrying the saved skin.
	 *
	 * @return whether it was set
	 */
	static boolean applySkin(InitialHandler handler, UUID uuid, String name, Skin skin) {
		if (skin == null || !resolve()) return false;
		try {
			if (handler.getLoginProfile() != null) return false; // never replace a profile Mojang supplied
			Object properties = Array.newInstance(propertyClass, 1);
			Array.set(properties, 0, propertyConstructor.newInstance(Skin.PROPERTY, skin.value, skin.signature));
			Object profile = loginResultConstructor.newInstance(uuid.toString().replace("-", ""), name, properties);
			loginProfileField.set(handler, profile);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
