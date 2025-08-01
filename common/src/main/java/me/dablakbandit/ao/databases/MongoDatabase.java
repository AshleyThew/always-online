package me.dablakbandit.ao.databases;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import me.dablakbandit.ao.NativeExecutor;
import org.bson.Document;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MongoDatabase implements Database {

	private final String host;
	private final int port;
	private final String databaseName;
	private final String username;
	private final String password;
	private final String connectionString;

	private final ConcurrentHashMap<String, PlayerData> cache = new ConcurrentHashMap<>();

	private MongoClient mongoClient = null;
	private com.mongodb.client.MongoDatabase mongoDatabase = null;
	private MongoCollection<Document> collection = null;

	private final NativeExecutor nativeExecutor;
	private Object pingTaskID = -1;

	public MongoDatabase(NativeExecutor nativeExecutor, String host, int port, String databaseName, String username, String password, String connectionString) {
		this.host = host;
		this.port = port;
		this.databaseName = databaseName;
		this.username = username;
		this.password = password;
		this.connectionString = connectionString;
		this.nativeExecutor = nativeExecutor;
		this.connect();
	}

	public void pingDatabase() {
		if (this.mongoClient != null && this.mongoDatabase != null) {
			try {
				// Ping the database by running a simple command
				this.mongoDatabase.runCommand(new Document("ping", 1));
			} catch (Exception e) {
				// Auto-reconnect on failure
				this.connect();
			}
		}
	}

	private void connect() {
		this.close(); // Close existing database connections, if one exists.

		try {
			String uri;
			if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
				uri = String.format("mongodb://%s:%s@%s:%d/%s%s", username, password, host, port, databaseName, connectionString != null ? connectionString : "");
			} else {
				uri = String.format("mongodb://%s:%d/%s%s", host, port, databaseName, connectionString != null ? connectionString : "");
			}

			this.mongoClient = MongoClients.create(uri);
			this.mongoDatabase = this.mongoClient.getDatabase(databaseName);
			this.collection = this.mongoDatabase.getCollection("always_online");

			// Test the connection
			this.mongoDatabase.runCommand(new Document("ping", 1));

			// Manual keep-alive task
			this.pingTaskID = this.nativeExecutor.runAsyncRepeating(new Runnable() {
				@Override
				public void run() {
					MongoDatabase.this.pingDatabase();
				}
			}, 0, 1, TimeUnit.MINUTES); // 1 minute

		} catch (Exception e) {
			throw new RuntimeException("Failed to connect to MongoDB", e);
		}
	}

	@Override
	public String getIP(String username) {
		PlayerData playerData = this.cache.get(username);
		if (playerData != null) {
			return playerData.ipAddress;
		} else {
			if (this.loadDataFromMongo(username)) {
				return this.cache.get(username).ipAddress;
			}
		}
		return null;
	}

	private boolean loadDataFromMongo(String username) {
		if (this.collection != null) {
			PlayerData playerData = null;
			try {
				Document query = new Document("name", username);
				Document result = this.collection.find(query).first();

				if (result != null) {
					String ip = result.getString("ip");
					String uuidString = result.getString("uuid");
					if (ip != null && uuidString != null) {
						playerData = new PlayerData(ip, UUID.fromString(uuidString));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (playerData != null) {
				this.cache.put(username, playerData);
				return true;
			}
		}
		return false;
	}

	@Override
	public UUID getUUID(String username) {
		PlayerData playerData = this.cache.get(username);
		if (playerData != null) {
			return playerData.uuid;
		} else {
			if (this.loadDataFromMongo(username)) {
				return this.cache.get(username).uuid;
			}
		}
		return null;
	}

	@Override
	public void updatePlayer(String username, String ip, UUID uuid) {
		this.cache.put(username, new PlayerData(ip, uuid));
		if (this.collection != null) {
			try {
				Document filter = new Document("name", username);
				Document update = new Document("name", username).append("ip", ip).append("uuid", uuid.toString());

				ReplaceOptions options = new ReplaceOptions().upsert(true);
				this.collection.replaceOne(filter, update, options);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void save() throws Exception {
		if (this.collection != null) {
			// Batch update all cached data
			for (Map.Entry<String, PlayerData> entry : this.cache.entrySet()) {
				String username = entry.getKey();
				PlayerData playerData = entry.getValue();

				Document filter = new Document("name", username);
				Document update = new Document("name", username).append("ip", playerData.ipAddress).append("uuid", playerData.uuid.toString());

				ReplaceOptions options = new ReplaceOptions().upsert(true);
				this.collection.replaceOne(filter, update, options);
			}
		}
	}

	@Override
	public void resetCache() {
		this.cache.clear();
	}

	@Override
	public void close() {
		if (this.pingTaskID instanceof Integer && ((Integer) this.pingTaskID) != -1) {
			this.nativeExecutor.cancelTask(this.pingTaskID);
			this.pingTaskID = -1;
		}
		if (this.mongoClient != null) {
			try {
				this.mongoClient.close();
			} catch (Exception e) {
				/* Non-critical error */
			}
			this.mongoClient = null;
			this.mongoDatabase = null;
			this.collection = null;
			this.cache.clear();
		}
	}
}
