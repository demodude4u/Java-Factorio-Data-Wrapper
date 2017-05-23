package com.demod.factorio;

import java.io.FileInputStream;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

public final class Config {
	private static JSONObject config = null;

	public static synchronized JSONObject get() {
		if (config == null) {
			loadConfig();
		}
		return config;
	}

	private static void loadConfig() {
		try {
			config = Utils.readJsonFromStream(new FileInputStream("config.json"));
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			System.err.println("################################");
			System.err.println("Missing or bad config.json file!");
			System.err.println("################################");
			System.exit(0);
		}
	}

	private Config() {
	}
}
