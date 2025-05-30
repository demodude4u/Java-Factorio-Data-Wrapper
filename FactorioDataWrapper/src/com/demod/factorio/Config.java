package com.demod.factorio;

import java.io.FileInputStream;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Config {
	private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

	private static JSONObject config = null;

	private static String configPath = "config.json";

	public static synchronized void setPath(String configPath) {
		Config.configPath = configPath;
		config = null;
	}

	public static String getPath() {
		return configPath;
	}

	public static synchronized JSONObject get() {
		if (config == null) {
			loadConfig();
		}
		return config;
	}

	private static void loadConfig() {
		try {
			config = Utils.readJsonFromStream(new FileInputStream(configPath));
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			LOGGER.error("################################");
			LOGGER.error("Missing or bad config.json file!");
			LOGGER.error("################################");
			System.exit(0);
		}
	}

	private Config() {
	}
}
