package com.demod.factorio.prototype;

import java.util.Optional;

import com.demod.factorio.fakelua.LuaTable;

public class ItemSubGroupPrototype extends DataPrototype {

	public ItemSubGroupPrototype(LuaTable lua) {
		super(lua);

		group = Optional.of(lua.get("group").tojstring());
	}

	@Override
	public void setGroup(Optional<String> group) {
		// Do Nothing
	}
}
