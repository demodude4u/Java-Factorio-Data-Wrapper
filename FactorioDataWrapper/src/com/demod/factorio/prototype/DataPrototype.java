package com.demod.factorio.prototype;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demod.factorio.DataTable;
import com.demod.factorio.Utils;
import com.demod.factorio.fakelua.LuaTable;

public abstract class DataPrototype {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataPrototype.class);

	private final LuaTable lua;
	private final String name;
	private final String type;

	private DataTable table;

	public DataPrototype(LuaTable lua, String name, String type) {
		this.lua = lua;
		this.name = name;
		this.type = type;
	}

	public void debugPrint() {
		LOGGER.debug(name);
		Utils.debugPrintLua(lua.tovalue());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DataPrototype other = (DataPrototype) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	public String getName() {
		return name;
	}

	public DataTable getTable() {
		return table;
	}

	public String getType() {
		return type;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	public LuaTable lua() {
		return lua;
	}

	public void setTable(DataTable table) {
		this.table = table;
	}
}