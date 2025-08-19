package com.demod.factorio.fakelua;

import org.json.JSONArray;
import org.json.JSONObject;

public class LuaTable {
	private final String debugPath;
	private final Object json;

	public LuaTable(JSONArray json) {
		this.debugPath = "";
		this.json = json;
	}

	public LuaTable(JSONObject json) {
		this.debugPath = "";
		this.json = json;
	}

	LuaTable(String debugPath, JSONArray json) {
		this.debugPath = debugPath;
		this.json = json;
	}

	LuaTable(String debugPath, JSONObject json) {
		this.debugPath = debugPath;
		this.json = json;
	}

	public String getDebugPath() {
		return debugPath;
	}

	public LuaValue get(int index) {
		if (json instanceof JSONObject) {
			return LuaValue.NIL;
		}
		Object value = ((JSONArray) json).opt(index - 1);
		if (value == null) {
			return LuaValue.NIL;
		}
		return new LuaValue(debugPath + "[" + index + "]", value);
	}

	public LuaValue get(String key) {
		if (json instanceof JSONArray) {
			return LuaValue.NIL;
		}
		Object value = ((JSONObject) json).opt(key);
		if (value == null) {
			return LuaValue.NIL;
		}
		return new LuaValue(debugPath + "[" + key + "]", value);
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
		return new LuaValue(debugPath, json);
	}
}
