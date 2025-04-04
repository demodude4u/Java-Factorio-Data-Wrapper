package com.demod.factorio.prototype;

import java.util.Optional;

import com.demod.factorio.fakelua.LuaTable;

public class ItemGroupPrototype extends DataPrototype {

	private final Optional<String> orderInRecipe;

	public ItemGroupPrototype(LuaTable lua) {
		super(lua);

		orderInRecipe = Optional.ofNullable(lua.get("order_in_recipe").optjstring(null));
	}

	public Optional<String> getOrderInRecipe() {
		return orderInRecipe;
	}
}
