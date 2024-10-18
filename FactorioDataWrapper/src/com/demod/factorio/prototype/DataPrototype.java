package com.demod.factorio.prototype;

import org.luaj.vm2.LuaTable;

import com.demod.factorio.Utils;

public abstract class DataPrototype {
	private final LuaTable lua;
	private final String name;
	private final String type;

	public DataPrototype(LuaTable lua, String name, String type) {
		this.lua = lua;
		this.name = name;
		this.type = type;
	}

	public void debugPrint() {
		System.out.println();
		System.out.println(name);
		Utils.debugPrintLua(lua);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DataPrototype other = (DataPrototype) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public LuaTable lua() {
		return lua;
	}
}