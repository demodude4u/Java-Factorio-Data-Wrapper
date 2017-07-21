package com.demod.factorio;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class ModInfo {
	public static class Dependency {
		private final boolean optional;
		private final String name;
		private final String conditional;
		private final String version;

		private Dependency(boolean optional, String name, String conditional, String version) {
			this.optional = optional;
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

		public String getVersion() {
			return version;
		}

		public boolean isOptional() {
			return optional;
		}
	}

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
		JSONArray dependenciesJson = json.getJSONArray("dependencies");
		for (int i = 0; i < dependenciesJson.length(); i++) {
			String depString = dependenciesJson.getString(i);
			String[] depSplit = depString.split("\\s");
			if (depSplit.length == 3) {
				dependencies.add(new Dependency(false, depSplit[0], depSplit[1], depSplit[2]));
			} else {
				dependencies.add(new Dependency(true, depSplit[1], depSplit[2], depSplit[3]));
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
