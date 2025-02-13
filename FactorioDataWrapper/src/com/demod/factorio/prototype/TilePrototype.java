package com.demod.factorio.prototype;

import com.demod.factorio.fakelua.LuaTable;

public class TilePrototype extends DataPrototype {
	private final boolean foundation;

	public TilePrototype(LuaTable lua, String name, String type) {
		super(lua, name, type);

		foundation = lua.get("is_foundation").optboolean(false);
	}

	public boolean isFoundation() {
		return foundation;
	}
}
