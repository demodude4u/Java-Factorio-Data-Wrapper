package com.demod.factorio;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class ModInfo {

	private final String name;
	private final String version;
	private final String title;
	private final String author;
	private final String contact;
	private final String homepage;
	private final String description;
	private final List<String> dependencies = new ArrayList<>();

	public ModInfo(JSONObject json) {
		name = json.getString("name");
		version = json.getString("version");
		title = json.optString("title", "");
		author = json.optString("author", "");
		contact = json.optString("contact", "");
		homepage = json.optString("homepage", "");
		description = json.optString("description", "");
		JSONArray dependenciesJson = json.getJSONArray("dependencies");
		for (int i = 0; i < dependenciesJson.length(); i++) {
			dependencies.add(dependenciesJson.getString(i));
		}
	}

	public String getAuthor() {
		return author;
	}

	public String getContact() {
		return contact;
	}

	public List<String> getDependencies() {
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
