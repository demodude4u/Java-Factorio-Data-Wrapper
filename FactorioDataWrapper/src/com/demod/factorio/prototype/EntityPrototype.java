package com.demod.factorio.prototype;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;

import com.demod.factorio.Utils;
import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.fakelua.LuaValue;

public class EntityPrototype extends DataPrototype {

	private final Rectangle2D.Double selectionBox;
	private final List<String> flags = new ArrayList<>();

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

	}

	public List<String> getFlags() {
		return flags;
	}

	public Rectangle2D.Double getSelectionBox() {
		return selectionBox;
	}

}
