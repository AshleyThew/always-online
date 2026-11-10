package me.dablakbandit.ao.sponge.session;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.dablakbandit.ao.databases.Skin;
import org.spongepowered.api.Sponge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * The bits of the vanilla server Sponge has no API for: switching mojang login off while it is down, and
 * giving a player who logged in without it their real UUID and saved skin.
 * <p>
 * This used to look the server class up by the name "minecraft.server.MinecraftServer", which does not exist
 * (the package is net.minecraft), so both switching offline mode and the session service hook failed on
 * startup and offline mode never took effect. The server object itself is a MinecraftServer, so its class is
 * now found from that instead. The session service hook was dropped: with mojang login switched off the
 * server never calls it, so it could not help anyone during an outage.
 */
public class SpongeSessionInjector {

	private static final SpongeSessionInjector instance = new SpongeSessionInjector();

	public static SpongeSessionInjector getInstance() {
		return instance;
	}

	private SpongeSessionInjector() {
		// Private constructor to prevent instantiation
	}

	private static Class<?> minecraftServerClass() {
		for (Class<?> c = Sponge.server().getClass(); c != null; c = c.getSuperclass()) {
			if (c.getName().equals("net.minecraft.server.MinecraftServer")) return c;
		}
		throw new IllegalStateException("The server is not a net.minecraft.server.MinecraftServer: " + Sponge.server().getClass());
	}

	/**
	 * Switches the server's mojang login off (or back on). With it off the server does not send players to
	 * mojang at all, which is the only way in while mojang is down.
	 */
	public void setOfflineMode(boolean offlineMode) throws ReflectiveOperationException {
		Class<?> server = minecraftServerClass();
		try {
			Method setter = server.getDeclaredMethod("setUsesAuthentication", boolean.class);
			setter.setAccessible(true);
			setter.invoke(Sponge.server(), !offlineMode);
		} catch (NoSuchMethodException e) {
			Field field = server.getDeclaredField("onlineMode");
			field.setAccessible(true);
			field.setBoolean(Sponge.server(), !offlineMode);
		}
	}

	/**
	 * Replaces the profile a logging-in player is about to be given (an offline-mode profile with a made-up
	 * UUID and no skin) with their real UUID and saved skin.
	 *
	 * @param loginListener the vanilla ServerLoginPacketListenerImpl handling this login
	 * @return whether it was replaced
	 */
	public boolean replaceLoginProfile(Object loginListener, UUID uuid, String name, Skin skin) throws ReflectiveOperationException {
		Field profileField = null;
		for (Class<?> c = loginListener.getClass(); c != null && profileField == null; c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				if (field.getType() == GameProfile.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
					profileField = field;
					break;
				}
			}
		}
		if (profileField == null) return false;
		profileField.setAccessible(true);
		GameProfile profile = new GameProfile(uuid, name);
		if (skin != null) {
			// PropertyMap is a Guava multimap; called reflectively so this module does not need Guava to build.
			Object properties = GameProfile.class.getMethod("getProperties").invoke(profile);
			properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, Skin.PROPERTY, new Property(Skin.PROPERTY, skin.value, skin.signature));
		}
		profileField.set(loginListener, profile);
		return true;
	}

}
