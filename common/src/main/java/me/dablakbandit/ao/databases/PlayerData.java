package me.dablakbandit.ao.databases;

import java.util.UUID;

public class PlayerData {

	public final String ipAddress;
	public final UUID uuid;
	// Null when the player has not joined since skins started being saved.
	public final Skin skin;

	public PlayerData(String ipAddress, UUID uuid) {
		this(ipAddress, uuid, null);
	}

	public PlayerData(String ipAddress, UUID uuid, Skin skin) {
		// Preconditions.checkNotNull(uuid);
		if (uuid == null) throw new NullPointerException("UUID provided is null.");
		this.uuid = uuid;
		this.ipAddress = ipAddress;
		this.skin = skin;
	}

	@Override
	public boolean equals(Object o) {
		return o == this || (o instanceof PlayerData && ((PlayerData) o).uuid.equals(this.uuid));
	}

	@Override
	public int hashCode() {
		return this.uuid.hashCode();
	}

}
