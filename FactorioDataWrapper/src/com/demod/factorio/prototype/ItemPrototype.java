package com.demod.factorio.prototype;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;

import com.demod.factorio.Utils;
import com.demod.factorio.fakelua.LuaTable;

public class ItemPrototype extends DataPrototype {
	private final List<String> flags = new ArrayList<>();

	public ItemPrototype(LuaTable lua, String name, String type) {
		super(lua, name, type);

		Utils.forEach(lua.get("flags").opttable(new LuaTable(new JSONArray())), l -> {
			flags.add(l.tojstring());
		});
	}

	public List<String> getFlags() {
		return flags;
	}
}
