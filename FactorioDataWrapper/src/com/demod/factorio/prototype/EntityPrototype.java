package com.demod.factorio.prototype;

import java.awt.geom.Rectangle2D;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import com.demod.factorio.Utils;

public class EntityPrototype extends DataPrototype {

	private final Rectangle2D.Double selectionBox;

	public EntityPrototype(LuaTable lua, String name, String type) {
		super(lua, name, type);
		LuaValue selectionBoxLua = lua.get("selection_box");
		if (!selectionBoxLua.isnil()) {
			selectionBox = Utils.parseRectangle(selectionBoxLua);
		} else {
			selectionBox = new Rectangle2D.Double();
		}
	}

	public Rectangle2D.Double getSelectionBox() {
		return selectionBox;
	}
}
