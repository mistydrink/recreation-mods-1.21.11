package net.servertools.nickskins;

import org.jetbrains.annotations.Nullable;

/** What a single player currently looks like to everyone else. */
public class PlayerOverride {
	/** Real account name, kept for /nick list and group displays. */
	public String realName;
	public @Nullable String nick;
	public @Nullable SkinEntry skin;

	public PlayerOverride() {}

	public PlayerOverride(String realName) {
		this.realName = realName;
	}

	public boolean isEmpty() {
		return nick == null && skin == null;
	}
}
