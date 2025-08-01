package me.dablakbandit.ao.sponge.session;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.YggdrasilEnvironment;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import me.dablakbandit.ao.hybrid.IAlwaysOnline;
import me.dablakbandit.ao.utils.NMSUtils;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.Proxy;

public class SpongeSessionInjector {

	private static final SpongeSessionInjector instance = new SpongeSessionInjector();

	public static SpongeSessionInjector getInstance() {
		return instance;
	}

	private SpongeSessionInjector() {
		// Private constructor to prevent instantiation
	}

	public void injectSessionService(IAlwaysOnline alwaysOnline, Server server) throws Exception {
		Class<?> classMinecraftServer = NMSUtils.getClass("minecraft.server.MinecraftServer");
		Class<?> classServices = NMSUtils.getClass("minecraft.server.Services");
		Field fieldServices = NMSUtils.getFirstFieldOfTypeSilent(classMinecraftServer, classServices);
		Object services = fieldServices.get(server);
		Field fieldServicesSessionService = NMSUtils.getFirstFieldOfTypeSilent(classServices, MinecraftSessionService.class);
		YggdrasilMinecraftSessionService oldSessionService = (YggdrasilMinecraftSessionService) fieldServicesSessionService.get(services);
		Field fieldServicesKeySet = NMSUtils.getFirstFieldOfTypeSilent(YggdrasilMinecraftSessionService.class, ServicesKeySet.class);
		ServicesKeySet servicesKeySet = (ServicesKeySet) fieldServicesKeySet.get(oldSessionService);
		Field fieldMinecraftClient = NMSUtils.getFirstFieldOfTypeSilent(YggdrasilMinecraftSessionService.class, MinecraftClient.class);
		MinecraftClient minecraftClient = (MinecraftClient) fieldMinecraftClient.get(oldSessionService);
		Field fieldProxy = NMSUtils.getFirstFieldOfTypeSilent(MinecraftClient.class, Proxy.class);
		Proxy proxy = (Proxy) fieldProxy.get(minecraftClient);

		SpongeAuthSessionService service = new SpongeAuthSessionService(alwaysOnline, oldSessionService, servicesKeySet, proxy, YggdrasilEnvironment.PROD.getEnvironment(), alwaysOnline.getDatabase());

		Constructor<?> conServices = classServices.getConstructors()[0];
		Object[] objects = new Object[conServices.getParameterCount()];
		objects[0] = service;

		for (int i = 1; i < conServices.getParameterCount(); i++) {
			objects[i] = NMSUtils.getFirstFieldOfType(classServices, conServices.getParameterTypes()[i]).get(services);
		}

		Object newServices = conServices.newInstance(objects);

		fieldServices.set(server, newServices);
	}

	public void setOfflineMode(boolean offlineMode) {
		try {
			Class<?> classMinecraftServer = NMSUtils.getClass("minecraft.server.MinecraftServer");
			Field onlineMode = NMSUtils.getField(classMinecraftServer, "onlineMode");
			onlineMode.set(Sponge.server(), !offlineMode);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
