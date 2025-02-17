package com.demod.factorio.prototype;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.json.JSONArray;

import com.demod.factorio.ItemToPlace;
import com.demod.factorio.Utils;
import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.fakelua.LuaValue;

public class EntityPrototype extends DataPrototype {

	private final Rectangle2D.Double selectionBox;
	private final List<String> flags = new ArrayList<>();
	private final List<ItemToPlace> placedBy = new ArrayList<>();
	private Optional<ItemToPlace> primaryItem = Optional.empty();

	public EntityPrototype(LuaTable lua, String name, String type) {
		super(lua, name, type);
		LuaValue selectionBoxLua = lua.get("selection_box");
		if (!selectionBoxLua.isnil()) {
			selectionBox = Utils.parseRectangle(selectionBoxLua);
		} else {
			selectionBox = new Rectangle2D.Double();
		}

		Utils.forEach(lua.get("flags").opttable(new LuaTable(new JSONArray())), l -> {
			flags.add(l.tojstring());
		});

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

	public List<String> getFlags() {
		return flags;
	}

	public List<ItemToPlace> getPlacedBy() {
		return placedBy;
	}

	public Optional<ItemToPlace> getPrimaryItem() {
		return primaryItem;
	}

	public Rectangle2D.Double getSelectionBox() {
		return selectionBox;
	}

	public void setPrimaryItem(Optional<ItemToPlace> primaryItem) {
		this.primaryItem = primaryItem;
	}
}
