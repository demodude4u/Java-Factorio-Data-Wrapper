package com.demod.factorio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class ModInfo {
	public static final Pattern DEPENDENCY_REGEX = Pattern
			.compile("^(?:(\\?|\\(\\?\\)|!|~) *)?(.+?)(?: *([<>=]=?) *([0-9.]+))?$");

	public static class Dependency {
		private final DepPrefix prefix;
		private final String name;
		private final DepOp op;
		private final String version;

		public static Dependency parse(String depString) {
			Matcher matcher = DEPENDENCY_REGEX.matcher(depString);
			if (!matcher.matches()) {
				throw new RuntimeException("Invalid dependency string: " + depString);
			}
			String symbol = matcher.group(1);
			DepPrefix prefix = DepPrefix.fromSymbol(symbol);
			String name = matcher.group(2);
			DepOp op = DepOp.fromSymbol(matcher.group(3));
			String version = matcher.group(4);
			return new Dependency(prefix, name, op, version);
		}

		private Dependency(DepPrefix prefix, String name, DepOp op, String version) {
			this.prefix = prefix;
			this.name = name;
			this.op = op;
			this.version = version;
		}

		public DepOp getOp() {
			return op;
		}

		public String getName() {
			return name;
		}

		public DepPrefix getPrefix() {
			return this.prefix;
		}

		public String getVersion() {
			return version;
		}

		public boolean isOptional() {
			return this.prefix == DepPrefix.OPTIONAL || this.prefix == DepPrefix.HIDDEN_OPTIONAL;
		}

		public boolean isIncompatible() {
			return this.prefix == DepPrefix.INCOMPATIBLE;
		}

		public boolean doesNotAffectLoadOrder() {
			return this.prefix == DepPrefix.DOES_NOT_AFFECT_LOAD_ORDER;
		}

		public boolean isRequired() {
			return this.prefix == DepPrefix.REQUIRED || this.prefix == DepPrefix.DOES_NOT_AFFECT_LOAD_ORDER;
		}
	}

	// https://wiki.factorio.com/Tutorial:Mod_structure#dependencies
	public static enum DepPrefix {
		// ! for incompatibility
		INCOMPATIBLE,
		// ? for an optional dependency
		OPTIONAL,
		// (?) for a hidden optional dependency
		HIDDEN_OPTIONAL,
		// ~ for a dependency that does not affect load order
		DOES_NOT_AFFECT_LOAD_ORDER,
		// no prefix for a hard requirement for the other mod.
		REQUIRED,;

		private static DepPrefix fromSymbol(String symbol) {
			if (symbol == null) {
				return REQUIRED;
			}
			switch (symbol) {
			case "!":
				return INCOMPATIBLE;
			case "?":
				return OPTIONAL;
			case "(?)":
				return HIDDEN_OPTIONAL;
			case "~":
				return DOES_NOT_AFFECT_LOAD_ORDER;
			default:
				throw new RuntimeException("Invalid dependency symbol: " + symbol);
			}
		}
	}

	public static enum DepOp {
		EQ, LT, LTE, GT, GTE;

		public static DepOp fromSymbol(String str) {
			if (str == null) {
				return null;
			}
			switch (str) {
			case "=":
				return EQ;
			case "<":
				return LT;
			case "<=":
				return LTE;
			case ">":
				return GT;
			case ">=":
				return GTE;
			default:
				throw new RuntimeException("Invalid equality operator: " + str);
			}
		}
	}

	private final String name;
	private final String version;
	private final String title;
	private final String author;
	private final String homepage;
	private final String description;
	private final String factorioVersion;
	private final List<Dependency> dependencies = new ArrayList<>();
	
	//When loaded by mod portal API only
	private final String downloadUrl;
	private final String filename;
	private final String sha1;
	private final String category;
	private final List<String> tags;
	private final long downloads;
	private final String owner;
	private final String updated;

	public ModInfo(JSONObject json) {
		name = json.getString("name");
		version = json.optString("version", "???");
		title = json.optString("title", "");
		author = json.optString("author", "");
		homepage = json.optString("homepage", "");
		description = json.optString("description", "");
		factorioVersion = json.optString("factorio_version", "");
		JSONArray dependenciesJson = json.optJSONArray("dependencies");
		if (dependenciesJson == null) {
			dependenciesJson = new JSONArray("[\"base\"]");
		}
		for (int i = 0; i < dependenciesJson.length(); i++) {
			String depString = dependenciesJson.getString(i);
			dependencies.add(Dependency.parse(depString));
		}
		downloadUrl = null;
		filename = null;
		sha1 = null;
		category = "";
		tags = new ArrayList<>();
		downloads = 0L;
		owner = author;
		updated = null;
	}

	//From mod portal API
	public ModInfo(JSONObject jsonResult, JSONObject jsonReleaseFull) {
		JSONObject jsonInfo = jsonReleaseFull.getJSONObject("info_json");
		name = jsonResult.getString("name");
		version = jsonReleaseFull.getString("version");
		title = jsonResult.getString("title");
		author = jsonResult.getString("owner");
		homepage = jsonResult.getString("homepage");
		description = jsonResult.optString("description", "");
		factorioVersion = jsonInfo.getString("factorio_version");
		JSONArray dependenciesJson = jsonInfo.optJSONArray("dependencies");
		if (dependenciesJson == null) {
			dependenciesJson = new JSONArray("[\"base\"]");
		}
		for (int i = 0; i < dependenciesJson.length(); i++) {
			String depString = dependenciesJson.getString(i);
			dependencies.add(Dependency.parse(depString));
		}
		downloadUrl = jsonReleaseFull.getString("download_url");
		filename = jsonReleaseFull.getString("file_name");
		sha1 = jsonReleaseFull.getString("sha1");
		category = jsonResult.optString("category", "");
		JSONArray tagsArr = jsonResult.optJSONArray("tags");
		tags = new ArrayList<>();
		if (tagsArr != null) {
			for (int i = 0; i < tagsArr.length(); i++) {
				tags.add(tagsArr.getString(i));
			}
		}
		downloads = jsonResult.optLong("downloads_count", 0L);
		owner = jsonResult.optString("owner", author);
		updated = jsonResult.optString("updated_at", null);
	}

	public String getAuthor() {
		return author;
	}

	public List<Dependency> getDependencies() {
		return dependencies;
	}

	public String getDescription() {
		return description;
	}

	public String getFactorioVersion() {
		return factorioVersion;
	}

	public String getHomepage() {
		return homepage;
	}

	public String getName() {
		return name;
	}

	public String getTitle() {
		return title;
	}

	public String getVersion() {
		return version;
	}

	public String getDownloadUrl() {
		return downloadUrl;
	}

	public String getFilename() {
		return filename;
	}

	public String getSha1() {
		return sha1;
	}

	public String getCategory() {
		return category;
	}

	public List<String> getTags() {
		return tags;
	}

	public long getDownloads() {
		return downloads;
	}

	public String getOwner() {
		return owner;
	}

	public String getUpdated() {
		return updated;
	}
}
