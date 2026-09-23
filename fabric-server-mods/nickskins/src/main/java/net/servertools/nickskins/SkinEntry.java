package net.servertools.nickskins;

/**
 * A Mojang-signed "textures" property. Since 1.20.2 the vanilla client ignores unsigned skins
 * on other players, so both value and signature are required.
 */
public class SkinEntry {
	public String name;
	public String value;
	public String signature;

	public SkinEntry() {}

	public SkinEntry(String name, String value, String signature) {
		this.name = name;
		this.value = value;
		this.signature = signature;
	}

	public boolean isValid() {
		return name != null && !name.isBlank()
				&& value != null && !value.isBlank()
				&& signature != null && !signature.isBlank();
	}
}
