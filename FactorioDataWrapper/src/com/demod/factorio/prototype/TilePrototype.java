package com.demod.factorio.prototype;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.demod.factorio.ItemToPlace;
import com.demod.factorio.Utils;
import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.fakelua.LuaValue;

public class TilePrototype extends DataPrototype {
	private final boolean foundation;
	private final List<ItemToPlace> placedBy = new ArrayList<>();
	private Optional<ItemToPlace> primaryItem = Optional.empty();

	public TilePrototype(LuaTable lua, String name, String type) {
		super(lua, name, type);

		foundation = lua.get("is_foundation").optboolean(false);

		LuaValue placeableByLua = lua.get("placeable_by");
		if (!placeableByLua.isnil()) {
			if (placeableByLua.isarray()) {
				Utils.forEach(placeableByLua.totableArray(), l -> placedBy.add(new ItemToPlace(l)));
			} else {
				placedBy.add(new ItemToPlace(placeableByLua));
			}
		}
	}

	public void addPlacedBy(ItemToPlace itemToPlace) {
		if (!placedBy.stream().anyMatch(i -> i.getItem().equals(itemToPlace.getItem()))) {
			placedBy.add(itemToPlace);
		}
	}

	public List<ItemToPlace> getPlacedBy() {
		return placedBy;
	}

	public Optional<ItemToPlace> getPrimaryItem() {
		return primaryItem;
	}

	public boolean isFoundation() {
		return foundation;
	}

	public void setPrimaryItem(Optional<ItemToPlace> primaryItem) {
		this.primaryItem = primaryItem;
	}
}
