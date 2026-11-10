package me.dablakbandit.ao.databases;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class FileDatabase implements Database {

	private final ConcurrentHashMap<String, PlayerData> cache = new ConcurrentHashMap<>();

	private final Path savedData;
	// Players are saved from async tasks, so two joining at the same moment would otherwise both rewrite the
	// file at once and lose or garble entries. Every read and write of the file goes through this lock.
	private final Object fileLock = new Object();

	public FileDatabase(Path savedData) {
		this.savedData = savedData;
	}

	public String getIP(String username) {
		PlayerData playerData = this.lookup(username);
		return playerData == null ? null : playerData.ipAddress;
	}

	public UUID getUUID(String username) {
		PlayerData playerData = this.lookup(username);
		return playerData == null ? null : playerData.uuid;
	}

	@Override
	public Skin getSkin(String username) {
		PlayerData playerData = this.lookup(username);
		return playerData == null ? null : playerData.skin;
	}

	@Override
	public String describe() {
		return "file (" + this.savedData.getFileName() + ")";
	}

	private PlayerData lookup(String username) {
		PlayerData playerData = this.cache.get(username);
		if (playerData != null) return playerData;
		try {
			playerData = this.loadPlayerData(username);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (playerData != null) this.cache.put(username, playerData);
		return playerData;
	}

	@Override
	public void updatePlayer(String username, String ip, UUID uuid, Skin skin) {
		synchronized (this.fileLock) {
			if (skin == null) {
				// Keep the skin already on record rather than forgetting it.
				PlayerData existing = this.lookup(username);
				if (existing != null && existing.uuid.equals(uuid)) skin = existing.skin;
			}
			this.cache.entrySet().removeIf(entry -> entry.getValue().uuid.equals(uuid));
			this.cache.put(username, new PlayerData(ip, uuid, skin));
			try {
				this.save();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void save() throws Exception {
		synchronized (this.fileLock) {
			List<String> existingLines = new ArrayList<>();
			if (Files.isReadable(this.savedData)) {// File exists check is included
				for (String line : Files.readAllLines(this.savedData, StandardCharsets.UTF_8)) {
					int split = line.indexOf('|');
					// Lines for players in the cache are rewritten below from the cache.
					if (split > 0 && this.cache.containsKey(line.substring(0, split))) continue;
					existingLines.add(line);
				}
			}

			// Write a temporary file and swap it in, so a crash part way through cannot leave a cut-off file.
			Path temp = this.savedData.resolveSibling(this.savedData.getFileName() + ".tmp");
			try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(temp, StandardCharsets.UTF_8))) {
				for (String line : existingLines) {
					w.println(line);
				}
				for (Map.Entry<String, PlayerData> en : this.cache.entrySet()) {
					PlayerData data = en.getValue();
					// Older versions read only the first three fields, so the skin can follow safely.
					String line = en.getKey() + "|" + data.ipAddress + "|" + data.uuid;
					if (data.skin != null) line += "|" + data.skin.value + "|" + (data.skin.signature == null ? "" : data.skin.signature);
					w.println(line);
				}
			}
			try {
				Files.move(temp, this.savedData, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, this.savedData, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	@Override
	public void resetCache() {
		this.cache.clear();
	}

	@Override
	public void close() {
		/* Nothing to see here */
		this.cache.clear();
	}

	private PlayerData loadPlayerData(String username) throws IOException {
		synchronized (this.fileLock) {
			if (Files.notExists(this.savedData)) Files.createFile(this.savedData);
			String startWithKey = username + "|";
			try (BufferedReader br = Files.newBufferedReader(this.savedData, StandardCharsets.UTF_8)) {
				String l;
				while ((l = br.readLine()) != null) {
					if (!l.startsWith(startWithKey)) continue;
					String[] data = l.split(Pattern.quote("|"));
					// A damaged line (older versions could write the file from two threads at once) is skipped
					// rather than allowed to throw out of the login check.
					if (data.length < 3) continue;
					try {
						Skin skin = data.length >= 4 ? Skin.of(data[3], data.length >= 5 ? data[4].trim() : null) : null;
						return new PlayerData(data[1], UUID.fromString(data[2].trim()), skin);
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
			return null;
		}
	}

}
