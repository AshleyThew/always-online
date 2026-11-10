package me.dablakbandit.ao.databases;

import me.dablakbandit.ao.NativeExecutor;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class MySQLDatabase implements Database {

	private static final String SELECT_BASIC = "SELECT uuid,ip FROM always_online WHERE name = ?";
	private static final String SELECT_WITH_SKIN = "SELECT uuid,ip,skin_value,skin_signature FROM always_online WHERE name = ?";
	private static final String INSERT_BASIC = "INSERT INTO always_online (name,ip,uuid) VALUES(?,?,?) ON DUPLICATE KEY UPDATE ip = VALUES(ip), uuid = VALUES(uuid)";
	// A null skin keeps the one on record, unless the name now belongs to a different player. The skin
	// columns are assigned before uuid because MySQL applies these assignments left to right.
	private static final String INSERT_WITH_SKIN = "INSERT INTO always_online (name,ip,uuid,skin_value,skin_signature) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE "
			+ "skin_signature = IF(uuid = VALUES(uuid) AND VALUES(skin_value) IS NULL, skin_signature, VALUES(skin_signature)), "
			+ "skin_value = IF(uuid = VALUES(uuid) AND VALUES(skin_value) IS NULL, skin_value, VALUES(skin_value)), "
			+ "ip = VALUES(ip), uuid = VALUES(uuid)";
	// The longest text form of an IP address (an IPv4-mapped IPv6 address). Tables made before 6.5.0 used
	// CHAR(15), which only fits IPv4: IPv6 players were either never saved (strict SQL mode rejects the
	// insert) or saved cut short, so their address never matched again.
	private static final int IP_COLUMN_SIZE = 45;

	private final String host;
	private final int port;
	private final String database;
	private final String username;
	private final String password;
	private final String extra;

	private final ConcurrentHashMap<String, PlayerData> cache = new ConcurrentHashMap<>();

	private volatile Statement statement = null;
	// False when the skin columns could not be added, so queries leave them out.
	private volatile boolean skinColumns = false;

	private final NativeExecutor nativeExecutor;
	private Object pingTaskID = -1;

	public MySQLDatabase(NativeExecutor nativeExecutor, String host, int port, String database, String username, String password, String extra) throws SQLException {
		this.host = host;
		this.port = port;
		this.database = database;
		this.username = username;
		this.password = password;
		this.extra = extra;
		this.nativeExecutor = nativeExecutor;
		this.connect();
	}

	public void pingDatabase() {
		Statement current = this.statement;
		if (current != null) {// Is alive check
			try {
				// Ping the database.
				if (!current.getConnection().isValid(30)) {// 30 second timeout
					// Auto-reconnect
					this.reconnect(current);
				}
			} catch (SQLException e) {
				this.reconnect(current);
			}
		}
	}

	private synchronized void connect() throws SQLException {
		this.close();// Close existing database connections, if one exists.
		Statement created = DriverManager.getConnection("jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database + this.extra, this.username, this.password).createStatement();
		this.statement = created;
		if (!this.doesTableExist(created, "always_online")) {
			created.executeUpdate("CREATE TABLE `always_online` ( `name` CHAR(16) NOT NULL , `ip` VARCHAR(" + IP_COLUMN_SIZE + ") NOT NULL , `uuid` CHAR(36) NOT NULL , `skin_value` TEXT NULL , `skin_signature` TEXT NULL , PRIMARY KEY (`name`)) ENGINE = MyISAM; ");
			this.skinColumns = true;
		} else {
			this.widenIpColumn(created);
			this.skinColumns = this.addSkinColumns(created);
		}

		// Manual keep-alive task. Should work with all drivers.
		this.pingTaskID = this.nativeExecutor.runAsyncRepeating(new Runnable() {
			@Override
			public void run() {
				MySQLDatabase.this.pingDatabase();
			}
		}, 1, 1, TimeUnit.MINUTES);// 1 minute
	}

	/**
	 * Replaces a connection that failed. Several threads can notice the same failure at once, so only the
	 * first replaces it; the rest see that the statement has already changed and use the new one.
	 *
	 * @return whether there is a working connection to retry on
	 */
	private synchronized boolean reconnect(Statement failed) {
		if (this.statement != null && this.statement != failed) return true;
		try {
			this.connect();
			this.nativeExecutor.log(Level.INFO, "Reconnected to the MySQL database.");
			return true;
		} catch (SQLException e) {
			this.nativeExecutor.log(Level.WARNING, "Could not reconnect to the MySQL database: " + e.getMessage());
			return false;
		}
	}

	private interface SqlWork<T> {
		T run(Connection connection) throws SQLException;
	}

	/**
	 * Runs a query, and if it fails because the connection dropped, reconnects and tries once more. Without
	 * this, a dropped connection failed every lookup until the next minute's keep-alive noticed, and during
	 * an outage every player in that window was refused as new.
	 */
	private <T> T withRetry(String what, T fallback, SqlWork<T> work) {
		for (int attempt = 0; attempt < 2; attempt++) {
			Statement current = this.statement;
			if (current == null) {
				if (attempt == 0 && this.reconnect(null)) continue;
				return fallback;
			}
			try {
				return work.run(current.getConnection());
			} catch (SQLException e) {
				if (attempt == 0) {
					this.nativeExecutor.log(Level.WARNING, "MySQL " + what + " failed (" + e.getMessage() + "), reconnecting and trying again...");
					if (!this.reconnect(current)) return fallback;
				} else {
					this.nativeExecutor.log(Level.WARNING, "MySQL " + what + " failed again after reconnecting: " + e.getMessage());
				}
			}
		}
		return fallback;
	}

	private PlayerData lookup(String username) {
		PlayerData playerData = this.cache.get(username);
		if (playerData != null) return playerData;
		playerData = this.withRetry("lookup for " + username, null, connection -> {
			boolean withSkin = this.skinColumns;
			try (PreparedStatement preparedStatement = connection.prepareStatement(withSkin ? SELECT_WITH_SKIN : SELECT_BASIC)) {
				preparedStatement.setString(1, username);
				try (ResultSet rs = preparedStatement.executeQuery()) {
					if (!rs.next()) return null;
					Skin skin = withSkin ? Skin.of(rs.getString(3), rs.getString(4)) : null;
					return new PlayerData(rs.getString(2), UUID.fromString(rs.getString(1)), skin);
				}
			}
		});
		if (playerData != null) this.cache.put(username, playerData);
		return playerData;
	}

	@Override
	public String getIP(String username) {
		PlayerData playerData = this.lookup(username);
		return playerData == null ? null : playerData.ipAddress;
	}

	@Override
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
		return "MySQL (" + this.host + ":" + this.port + "/" + this.database + (this.statement == null ? ", not connected" : "") + ")";
	}

	@Override
	public void updatePlayer(String username, String ip, UUID uuid, Skin skin) {
		PlayerData previous = this.cache.get(username);
		Skin cachedSkin = skin != null ? skin : (previous != null && previous.uuid.equals(uuid) ? previous.skin : null);
		this.cache.put(username, new PlayerData(ip, uuid, cachedSkin));
		this.withRetry("save for " + username, null, connection -> {
			try (PreparedStatement preparedStatement = connection.prepareStatement(this.skinColumns ? INSERT_WITH_SKIN : INSERT_BASIC)) {
				this.bind(preparedStatement, username, ip, uuid, skin);
				preparedStatement.execute();
			}
			return null;
		});
	}

	private void bind(PreparedStatement preparedStatement, String username, String ip, UUID uuid, Skin skin) throws SQLException {
		preparedStatement.setString(1, username);
		preparedStatement.setString(2, ip);
		preparedStatement.setString(3, uuid.toString());
		if (this.skinColumns) {
			preparedStatement.setString(4, skin == null ? null : skin.value);
			preparedStatement.setString(5, skin == null ? null : skin.signature);
		}
	}

	@Override
	public void save() throws Exception {
		Statement current = this.statement;
		if (current != null) {
			Connection connection = current.getConnection();
			if (connection != null) {
				try (PreparedStatement preparedStatement = connection.prepareStatement(this.skinColumns ? INSERT_WITH_SKIN : INSERT_BASIC)) {
					int i = 0;
					for (Map.Entry<String, PlayerData> en : this.cache.entrySet()) {
						this.bind(preparedStatement, en.getKey(), en.getValue().ipAddress, en.getValue().uuid, en.getValue().skin);
						preparedStatement.addBatch();
						if (++i == 1000) {
							preparedStatement.executeBatch(); // Execute every 1000 items
							i = 0;
						}
					}
					if (i > 0) preparedStatement.executeBatch();
				}
			}
		}
	}

	@Override
	public void resetCache() {
		this.cache.clear();
	}

	@Override
	public synchronized void close() {
		if (this.pingTaskID instanceof Integer && ((Integer) this.pingTaskID) != -1) {
			this.nativeExecutor.cancelTask(this.pingTaskID);
			this.pingTaskID = -1;
		}
		Statement current = this.statement;
		if (current != null) {
			try {
				current.getConnection().close();
			} catch (SQLException e) {
				/* Non-critical error */
			}
			this.statement = null;
			this.cache.clear();
		}
	}

	/**
	 * @return the column's declared type, such as "char(15)", or null if the table has no such column
	 */
	private String columnType(Statement statement, String column) throws SQLException {
		// SHOW COLUMNS reads the table in the connection's own database, with none of the catalog/schema
		// ambiguity of DatabaseMetaData.
		try (ResultSet rs = statement.executeQuery("SHOW COLUMNS FROM `always_online` LIKE '" + column + "'")) {
			return rs.next() ? rs.getString("Type") : null;
		}
	}

	private void widenIpColumn(Statement statement) {
		try {
			String type = this.columnType(statement, "ip");
			if (type == null) return;
			String digits = type.replaceAll("[^0-9]", "");
			int size = digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
			if (size >= IP_COLUMN_SIZE) return;
			this.nativeExecutor.log(Level.INFO, "Widening the always_online ip column from " + size + " to " + IP_COLUMN_SIZE + " characters so IPv6 addresses can be saved...");
			statement.executeUpdate("ALTER TABLE `always_online` MODIFY `ip` VARCHAR(" + IP_COLUMN_SIZE + ") NOT NULL");
		} catch (SQLException e) {
			// Not fatal: IPv4 players still work. Say how to do it by hand, since IPv6 players will not.
			this.nativeExecutor.log(Level.WARNING, "Could not widen the always_online ip column (" + e.getMessage() + "), so players connecting over IPv6 cannot be saved. Run this on the database to fix it: ALTER TABLE `always_online` MODIFY `ip` VARCHAR(" + IP_COLUMN_SIZE + ") NOT NULL;");
		}
	}

	/**
	 * @return whether the skin columns exist now
	 */
	private boolean addSkinColumns(Statement statement) {
		try {
			if (this.columnType(statement, "skin_value") != null) return true;
			this.nativeExecutor.log(Level.INFO, "Adding skin columns to the always_online table, so players keep their skin while mojang is offline...");
			statement.executeUpdate("ALTER TABLE `always_online` ADD COLUMN `skin_value` TEXT NULL, ADD COLUMN `skin_signature` TEXT NULL");
			return true;
		} catch (SQLException e) {
			this.nativeExecutor.log(Level.WARNING, "Could not add the skin columns to always_online (" + e.getMessage() + "). Everything else works, but players will have no skin while mojang is offline. To fix it run: ALTER TABLE `always_online` ADD COLUMN `skin_value` TEXT NULL, ADD COLUMN `skin_signature` TEXT NULL;");
			return false;
		}
	}

	/**
	 * Determines if a table exists
	 *
	 * @param tableName The table name
	 * @return If the table exists
	 */
	private boolean doesTableExist(Statement statement, String tableName) throws SQLException {
		try (ResultSet rs = statement.getConnection().getMetaData().getTables(null, null, tableName, null)) {
			return rs.next();
		}
	}

}
