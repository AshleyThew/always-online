package me.dablakbandit.ao.databases;

/**
 * A player's signed "textures" profile property, as Mojang hands it out on a normal login. It is saved on
 * every normal join and given back to the player while mojang is offline, so they keep their skin and cape.
 * The signature is checked by clients against a key built into the game, so this works without mojang.
 */
public final class Skin {

	public static final String PROPERTY = "textures";

	public final String value;
	public final String signature;

	public Skin(String value, String signature) {
		this.value = value;
		this.signature = signature;
	}

	/**
	 * @return a skin, or null when there is no usable value
	 */
	public static Skin of(String value, String signature) {
		if (value == null || value.isEmpty()) return null;
		return new Skin(value, signature == null || signature.isEmpty() ? null : signature);
	}
}
