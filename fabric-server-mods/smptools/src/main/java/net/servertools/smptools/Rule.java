package net.servertools.smptools;

public enum Rule {
	VILLAGERS("remove_villagers", "villagers"),
	SHULKERS("remove_shulkers", "shulkers"),
	ELYTRAS("remove_elytras", "elytras in End ship item frames"),
	OMINOUS_VAULTS("remove_ominousvaults", "ominous vaults");

	public final String command;
	public final String label;

	Rule(String command, String label) {
		this.command = command;
		this.label = label;
	}
}
