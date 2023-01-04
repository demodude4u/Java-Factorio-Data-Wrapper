package com.demod.factorio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

public class ModInfo {
	public static class Dependency {
		private final DependencyType type;
		private final String name;
		private final String conditional;
		private final String version;

		private Dependency(DependencyType type, String name, String conditional, String version) {
			this.type = type;
			this.name = name;
			this.conditional = conditional;
			this.version = version;
		}

		public String getConditional() {
			return conditional;
		}

		public String getName() {
			return name;
		}

		public DependencyType getType() {
			return this.type;
		}

		public String getVersion() {
			return version;
		}

		public boolean isOptional() {
			return this.type == DependencyType.OPTIONAL || this.type == DependencyType.HIDDEN_OPTIONAL;
		}
	}

	// https://wiki.factorio.com/Tutorial:Mod_structure#dependencies
	public static enum DependencyType {
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

		private static DependencyType fromSymbol(String symbol) {
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

	public static final Pattern DEPENDENCY_REGEX = Pattern
			.compile("^(?:(\\?|\\(\\?\\)|!|~) *)?(.+?)(?: *([<>=]=?) *([0-9.]+))?$");

	private final String name;
	private final String version;
	private final String title;
	private final String author;
	private final String contact;
	private final String homepage;
	private final String description;
	private final List<Dependency> dependencies = new ArrayList<>();

	public ModInfo(JSONObject json) {
		name = json.getString("name");
		version = json.optString("version", "???");
		title = json.optString("title", "");
		author = json.optString("author", "");
		contact = json.optString("contact", "");
		homepage = json.optString("homepage", "");
		description = json.optString("description", "");
		JSONArray dependenciesJson = json.optJSONArray("dependencies");
		if (dependenciesJson == null) {
			dependenciesJson = new JSONArray("[\"base\"]");
		}
		for (int i = 0; i < dependenciesJson.length(); i++) {
			String depString = dependenciesJson.getString(i);
			Matcher matcher = DEPENDENCY_REGEX.matcher(depString);
			if (matcher.matches()) {
				String symbol = matcher.group(1);
				DependencyType type = DependencyType.fromSymbol(symbol);
				String name = matcher.group(2);
				String conditional = matcher.group(3);
				String version = matcher.group(4);
				dependencies.add(new Dependency(type, name, conditional, version));
			} else {
				throw new RuntimeException("Invalid dependency string: " + depString);
			}
		}
	}

	public String getAuthor() {
		return author;
	}

	public String getContact() {
		return contact;
	}

	public List<Dependency> getDependencies() {
		return dependencies;
	}

	public String getDescription() {
		return description;
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

}
