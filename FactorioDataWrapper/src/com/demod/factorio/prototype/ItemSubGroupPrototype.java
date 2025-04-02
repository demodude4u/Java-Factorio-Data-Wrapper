package com.demod.factorio.prototype;

import java.util.Optional;

import com.demod.factorio.fakelua.LuaTable;

public class ItemSubGroupPrototype extends DataPrototype {

	public ItemSubGroupPrototype(LuaTable lua, String name, String type) {
		super(lua, name, type);

		group = Optional.of(lua.get("group").tojstring());
	}
}
