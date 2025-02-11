package com.demod.factorio.fakelua;

import org.json.JSONArray;
import org.json.JSONObject;

public class LuaTable {
	private final Object json;

	public LuaTable(JSONArray json) {
		this.json = json;
	}

	public LuaTable(JSONObject json) {
		this.json = json;
	}

	public LuaValue get(int index) {
		if (json instanceof JSONObject) {
			return new LuaValue(null);
		}
		return new LuaValue(((JSONArray) json).opt(index - 1));
	}

	public LuaValue get(String key) {
		if (json instanceof JSONArray) {
			return new LuaValue(null);
		}
		return new LuaValue(((JSONObject) json).opt(key));
	}

	public Object getJson() {
		return json;
	}

	public boolean isArray() {
		return json instanceof JSONArray;
	}

	public boolean isObject() {
		return json instanceof JSONObject;
	}

	public int length() {
		if (json instanceof JSONObject) {
			return ((JSONObject) json).length();
		} else {
			return ((JSONArray) json).length();
		}
	}

	@Override
	public String toString() {
		return json.toString();
	}

	public LuaValue tovalue() {
		return new LuaValue(json);
	}
}
