package me.dablakbandit.ao.spigot.authservices;

import com.mojang.authlib.yggdrasil.Check_1_14;
import com.mojang.authlib.yggdrasil.Check_1_16_4;
import com.mojang.authlib.yggdrasil.Check_1_20_2;
import com.mojang.authlib.yggdrasil.Check_26_1;
import me.dablakbandit.ao.spigot.SpigotLoader;
import me.dablakbandit.ao.utils.NMSUtils;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

public class NMSAuthSetup {

	private enum HookType {
		NONE, V1_14, V1_16_4, V26_1, V1_20_2
	}

	private static final Class<?> classMinecraftServer = NMSUtils.getNMSClass("MinecraftServer");
	private static final Method setUsesAuthentication = NMSUtils.getMethodSilent(classMinecraftServer, new String[]{"setOnlineMode", "setUsesAuthentication", "d"}, boolean.class);
	private static final Method getServer = NMSUtils.getMethod(classMinecraftServer, "getServer");
	private static HookType activeHook = HookType.NONE;

	public static void setUp(SpigotLoader spigotLoader) throws Exception {
		activeHook = HookType.NONE;
		if (isValid(Check_1_14::valid)) {
			spigotLoader.log(Level.INFO, "Attempting setup ~1_14 Auth service");
			Check_1_14.setup(spigotLoader.getAOInstance());
			activeHook = HookType.V1_14;
		} else if (isValid(Check_1_16_4::valid)) {
			spigotLoader.log(Level.INFO, "Attempting setup ~1_16 Auth service");
			Check_1_16_4.setup(spigotLoader.getAOInstance());
			activeHook = HookType.V1_16_4;
		} else if (isValid(Check_26_1::valid)) {
			spigotLoader.log(Level.INFO, "Attempting setup 26.1+ Auth service");
			Check_26_1.setup(spigotLoader.getAOInstance());
			activeHook = HookType.V26_1;
		} else {
			spigotLoader.log(Level.INFO, "Attempting setup 1.20+ Auth service");
			Check_1_20_2.setup(spigotLoader.getAOInstance());
			activeHook = HookType.V1_20_2;
		}
	}

	// A hook built against an older authlib fails to link on newer servers (authlib 10, Paper 26.3+,
	// renamed MinecraftSessionService), which surfaces as a NoClassDefFoundError from its static
	// initializer rather than a false result, so treat that as "not this version" and move on.
	private static boolean isValid(BooleanSupplier check) {
		try {
			return check.getAsBoolean();
		} catch (LinkageError e) {
			return false;
		}
	}

	public static void tearDown(SpigotLoader spigotLoader) {
		try {
			switch (activeHook) {
				case V1_14:
					spigotLoader.log(Level.INFO, "Restoring ~1_14 Auth service hooks");
					Check_1_14.teardown();
					break;
				case V1_16_4:
					spigotLoader.log(Level.INFO, "Restoring ~1_16 Auth service hooks");
					Check_1_16_4.teardown();
					break;
				case V26_1:
					spigotLoader.log(Level.INFO, "Restoring 26.1+ Auth service hooks");
					Check_26_1.teardown();
					break;
				case V1_20_2:
					spigotLoader.log(Level.INFO, "Restoring 1.20+ Auth service hooks");
					Check_1_20_2.teardown();
					break;
				case NONE:
				default:
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			activeHook = HookType.NONE;
		}
	}

	public static void setOnlineMode(boolean onlineMode) {
		if (setUsesAuthentication != null) {
			try {
				Object ms = getServer.invoke(null);
				setUsesAuthentication.invoke(ms, onlineMode);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}