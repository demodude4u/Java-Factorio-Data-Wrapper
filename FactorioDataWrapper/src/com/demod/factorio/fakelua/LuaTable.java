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
			return LuaValue.NIL;
		}
		Object value = ((JSONArray) json).opt(index - 1);
		if (value == null) {
			return LuaValue.NIL;
		}
		return new LuaValue(value);
	}

	public LuaValue get(String key) {
		if (json instanceof JSONArray) {
			return LuaValue.NIL;
		}
		Object value = ((JSONObject) json).opt(key);
		if (value == null) {
			return LuaValue.NIL;
		}
		return new LuaValue(value);
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
