package com.demod.factorio;

import com.demod.factorio.fakelua.LuaValue;

public class ItemToPlace {
	private final String item;
	private final int count;

	public ItemToPlace(LuaValue lua) {
		item = lua.get("item").tojstring();
		count = lua.get("count").toint();
	}

	public ItemToPlace(String item, int count) {
		this.item = item;
		this.count = count;
	}

	public int getCount() {
		return count;
	}

	public String getItem() {
		return item;
	}
}
