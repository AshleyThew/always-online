package me.dablakbandit.ao.spigot.authservices;

import com.mojang.authlib.yggdrasil.Check_1_14;
import com.mojang.authlib.yggdrasil.Check_1_16_4;
import com.mojang.authlib.yggdrasil.Check_1_20_2;
import me.dablakbandit.ao.spigot.SpigotLoader;
import me.dablakbandit.ao.utils.NMSUtils;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class NMSAuthSetup {

	private static Class<?> classMinecraftServer = NMSUtils.getNMSClass("MinecraftServer");
	private static Method setUsesAuthentication = NMSUtils.getMethodSilent(classMinecraftServer, new String[]{"setOnlineMode", "setUsesAuthentication", "d"}, boolean.class);
	private static Method getServer = NMSUtils.getMethod(classMinecraftServer, "getServer");

	private static Class<?> servicesClass = NMSUtils.getClassSilent("net.minecraft.server.Services");

	private static boolean disableOnlineMode = true;


	public static void setUp(SpigotLoader spigotLoader) throws Exception {
		if (Check_1_14.valid()) {
			spigotLoader.log(Level.INFO, "Attempting setup ~1_14 Auth service");
			Check_1_14.setup(spigotLoader.getAOInstance());
		} else if (Check_1_16_4.valid()) {
			spigotLoader.log(Level.INFO, "Attempting setup ~1_16 Auth service");
			Check_1_16_4.setup(spigotLoader.getAOInstance());
		} else {
			spigotLoader.log(Level.INFO, "Attempting setup 1.20+ Auth service");
			Check_1_20_2.setup(spigotLoader.getAOInstance());
			disableOnlineMode = false;
		}
	}

	public static void setOnlineMode(boolean onlineMode) {
		if (servicesClass != null && setUsesAuthentication != null && disableOnlineMode) {
			try {
				Object ms = getServer.invoke(null);
				setUsesAuthentication.invoke(ms, onlineMode);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}


}