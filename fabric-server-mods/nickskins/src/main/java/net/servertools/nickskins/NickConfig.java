package net.servertools.nickskins;

public class NickConfig {
	/**
	 * When a player's skin changes, briefly "respawn" them in place on their own client so they
	 * see the new skin in F5 right away. Health, inventory, XP, effects and position are re-sent.
	 * Turn off if it misbehaves with another mod; they'll then see their own new skin after relogging.
	 */
	public boolean refreshOwnSkin = true;
}
