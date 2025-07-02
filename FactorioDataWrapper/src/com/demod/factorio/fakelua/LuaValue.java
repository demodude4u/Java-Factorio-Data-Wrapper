package com.demod.factorio.fakelua;

import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONObject;

public class LuaValue {

	public static final LuaValue NIL = new LuaValue(null);

	private final String debugPath;
	private final Object json;

	public LuaValue(Object json) {
		this.debugPath = "";
		this.json = json;
	}

	LuaValue(String debugPath, Object json) {
		this.debugPath = debugPath;
		this.json = json;
	}

	public String getDebugPath() {
		return debugPath;
	}

	public boolean checkboolean() {
		return (boolean) json;
	}

	public double checkdouble() {
		return ((Number) json).doubleValue();
	}

	public float checkfloat() {
		return ((Number) json).floatValue();
	}

	public int checkint() {
		return ((Number) json).intValue();
	}

	public String checkjstring() {
		return (String) json;
	}

	public LuaTable checktable() {
		if (json instanceof JSONObject) {
			return new LuaTable(debugPath, (JSONObject) json);
		} else {
			return new LuaTable(debugPath, (JSONArray) json);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LuaValue other = (LuaValue) obj;
		return Objects.equals(json, other.json);
	}

	public LuaValue get(int index) {
		if (isnil()) {
			return this;
		}
		return checktable().get(index);
	}

	public LuaValue get(String key) {
		if (isnil()) {
			return this;
		}
		return checktable().get(key);
	}

	public Object getJson() {
		return json;
	}

	@Override
	public int hashCode() {
		return Objects.hash(json);
	}

	public boolean isarray() {
		return json instanceof JSONArray;
	}

	public boolean isboolean() {
		return json instanceof Boolean;
	}

	public boolean isnil() {
		return json == null || json == JSONObject.NULL;
	}

	public boolean isnumber() {
		return json instanceof Number;
	}

	public boolean isobject() {
		return json instanceof JSONObject;
	}

	public boolean isjstring() {
		return json instanceof String;
	}

	public int length() {
		return checktable().length();
	}

	public boolean optboolean(boolean defaultValue) {
		return isnil() ? defaultValue : checkboolean();
	}

	public double optdouble(double defaultValue) {
		return isnil() ? defaultValue : checkdouble();
	}

	public float optfloat(float defaultValue) {
		return isnil() ? defaultValue : checkfloat();
	}

	public int optint(int defaultValue) {
		return isnil() ? defaultValue : checkint();
	}

	public String optjstring(String defaultString) {
		return isnil() ? defaultString : checkjstring();
	}

	public LuaTable opttable(LuaTable defaultValue) {
		return isnil() ? defaultValue : checktable();
	}

	public Boolean toboolean() {
		return optboolean(false);
	}

	public double todouble() {
		return optdouble(0);
	}

	public float tofloat() {
		return optfloat(0f);
	}

	public int toint() {
		return optint(0);
	}

	public String tojstring() {
		return optjstring("");
	}

	@Override
	public String toString() {
		return json.toString();
	}

	public LuaTable totableArray() {
		return opttable(new LuaTable(debugPath, new JSONArray()));
	}

	public LuaTable totableObject() {
		return opttable(new LuaTable(debugPath, new JSONObject()));
	}

}
