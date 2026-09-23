package net.servertools.nickskins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Fetches the Mojang-signed skin of an existing Minecraft account (works on online and offline servers). */
public final class MojangSkins {
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private MojangSkins() {}

	public static CompletableFuture<SkinEntry> fetch(String username, String label) {
		return CompletableFuture.supplyAsync(() -> {
			JsonObject idJson = getJson("https://api.mojang.com/users/profiles/minecraft/"
					+ URLEncoder.encode(username, StandardCharsets.UTF_8));
			if (idJson == null || !idJson.has("id")) {
				throw new IllegalArgumentException("No Minecraft account named " + username);
			}
			String id = idJson.get("id").getAsString();

			JsonObject profile = getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false");
			if (profile == null || !profile.has("properties")) {
				throw new IllegalStateException("Mojang returned no profile for " + username);
			}
			for (JsonElement el : profile.getAsJsonArray("properties")) {
				JsonObject prop = el.getAsJsonObject();
				if ("textures".equals(prop.get("name").getAsString()) && prop.has("signature")) {
					return new SkinEntry(label.toLowerCase(Locale.ROOT),
							prop.get("value").getAsString(),
							prop.get("signature").getAsString());
				}
			}
			throw new IllegalStateException(username + " has no signed skin data");
		});
	}

	private static JsonObject getJson(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(10))
					.header("User-Agent", "NickSkins-Fabric")
					.GET()
					.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 204 || response.statusCode() == 404) return null;
			if (response.statusCode() != 200) {
				throw new IllegalStateException("Mojang API returned HTTP " + response.statusCode()
						+ (response.statusCode() == 429 ? " (rate limited, try again in a minute)" : ""));
			}
			return JsonParser.parseString(response.body()).getAsJsonObject();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while contacting Mojang");
		} catch (java.io.IOException e) {
			throw new IllegalStateException("Could not reach Mojang: " + e.getMessage());
		}
	}
}
