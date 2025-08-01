package me.dablakbandit.ao.sponge.session;

import com.mojang.authlib.Environment;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.ServicesKeySet;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import me.dablakbandit.ao.databases.Database;
import me.dablakbandit.ao.hybrid.IAlwaysOnline;
import me.dablakbandit.ao.utils.NMSUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Proxy;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SpongeAuthSessionService extends YggdrasilMinecraftSessionService {

	private final IAlwaysOnline alwaysOnline;
	private final Database database;
	private final ConcurrentHashMap<UUID, CacheEntry<Optional<ProfileResult>>> uuidNameCache;
	private final ScheduledExecutorService cacheCleanupExecutor;

	private final YggdrasilMinecraftSessionService oldSessionService;

	private final Method fetchProfile1;
	private final Method fetchProfile2;

	// Simple cache entry with expiration time
	private static class CacheEntry<T> {
		private final T value;
		private final long expirationTime;

		public CacheEntry(T value, long expirationTimeMs) {
			this.value = value;
			this.expirationTime = expirationTimeMs;
		}

		public T getValue() {
			return value;
		}

		public boolean isExpired() {
			return System.currentTimeMillis() > expirationTime;
		}
	}

	public SpongeAuthSessionService(IAlwaysOnline alwaysOnline, YggdrasilMinecraftSessionService oldSessionService,
			ServicesKeySet servicesKeySet, Proxy proxy, Environment enviroment, Database database) {
		super(servicesKeySet, proxy, enviroment);
		this.alwaysOnline = alwaysOnline;
		this.oldSessionService = oldSessionService;
		this.database = database;
		this.fetchProfile1 = NMSUtils.getMethod(oldSessionService.getClass(), "fetchProfile", GameProfile.class,
				boolean.class);
		this.fetchProfile2 = NMSUtils.getMethod(oldSessionService.getClass(), "fetchProfile", UUID.class,
				boolean.class);
		this.uuidNameCache = new ConcurrentHashMap<>();
		this.cacheCleanupExecutor = Executors.newSingleThreadScheduledExecutor();

		// Schedule cache cleanup every hour
		this.cacheCleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredEntries, 1, 1, TimeUnit.HOURS);
	}

	private void cleanupExpiredEntries() {
		uuidNameCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
	}

	private void putInCache(UUID uuid, Optional<ProfileResult> value) {
		long expirationTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(6);
		uuidNameCache.put(uuid, new CacheEntry<>(value, expirationTime));
	}

	private Optional<ProfileResult> getFromCache(UUID uuid) {
		CacheEntry<Optional<ProfileResult>> entry = uuidNameCache.get(uuid);
		if (entry == null || entry.isExpired()) {
			if (entry != null) {
				uuidNameCache.remove(uuid); // Remove expired entry
			}
			return null;
		}
		return entry.getValue();
	}

	public void joinServer(final UUID profileId, final String authenticationToken, final String serverId)
			throws AuthenticationException {
		alwaysOnline.getNativeExecutor().log(Level.INFO, "Player " + profileId + " is joining the server with token: "
				+ authenticationToken + " and server ID: " + serverId);
		super.joinServer(profileId, authenticationToken, serverId);
	}

	public ProfileResult hasJoinedServer(String profileName, String serverId, InetAddress address)
			throws AuthenticationUnavailableException {
		if (alwaysOnline.getOfflineMode()) {
			UUID uuid = this.database.getUUID(profileName);
			if (uuid != null) {
				putInCache(uuid, Optional.of(new ProfileResult(new GameProfile(uuid, profileName))));
				return new ProfileResult(new GameProfile(uuid, profileName));
			} else {
				alwaysOnline.getNativeExecutor().log(Level.INFO, profileName + " "
						+ "never joined this server before when mojang servers were online. Denying their access.");
				throw new AuthenticationUnavailableException(
						"Mojang servers are offline and we can't authenticate the player with our own system.");
			}
		} else {
			return super.hasJoinedServer(profileName, serverId, address);
		}
	}

	public ProfileResult fetchProfile(GameProfile profile, boolean requireSecure) {
		if (alwaysOnline.getOfflineMode()) {
			Optional<ProfileResult> cached = getFromCache(profile.getId());
			return cached != null ? cached.orElse(null) : null;
		}
		try {
			return (ProfileResult) fetchProfile1.invoke(oldSessionService, profile, requireSecure);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	public ProfileResult fetchProfile(UUID profileId, boolean requireSecure) {
		if (alwaysOnline.getOfflineMode()) {
			Optional<ProfileResult> cached = getFromCache(profileId);
			return cached != null ? cached.orElse(null) : null;
		}
		try {
			return (ProfileResult) fetchProfile2.invoke(oldSessionService, profileId, requireSecure);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	// Cleanup method to shutdown the cache cleanup executor
	public void shutdown() {
		if (cacheCleanupExecutor != null && !cacheCleanupExecutor.isShutdown()) {
			cacheCleanupExecutor.shutdown();
		}
	}

}
