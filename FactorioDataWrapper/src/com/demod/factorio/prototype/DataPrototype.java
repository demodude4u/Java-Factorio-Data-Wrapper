package com.demod.factorio.prototype;

import java.util.Comparator;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demod.factorio.DataTable;
import com.demod.factorio.Utils;
import com.demod.factorio.fakelua.LuaTable;

public abstract class DataPrototype implements Comparable<DataPrototype> {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataPrototype.class);

	public static final Comparator<DataPrototype> ORDER_COMPARE = new Comparator<DataPrototype>() {
		@Override
		public int compare(DataPrototype o1, DataPrototype o2) {
			Optional<String> id1 = o1.getGroup();
			Optional<String> id2 = o2.getGroup();
			if (!id1.equals(id2)) {
				if (id1.isPresent() && id2.isPresent()) {
					Optional<ItemGroupPrototype> p1 = o1.getTable().getItemGroup(id1.get());
					Optional<ItemGroupPrototype> p2 = o2.getTable().getItemGroup(id2.get());
					if (p1.isPresent() && p2.isPresent()) {
						return p1.get().compareTo(p2.get());
					}
					return p1.isPresent() ? 1 : -1;
				}
				return id1.isPresent() ? 1 : -1;
			}
			return 0;
		}
	}.thenComparing(new Comparator<DataPrototype>() {
		@Override
		public int compare(DataPrototype o1, DataPrototype o2) {
			Optional<String> id1 = o1.getSubgroup();
			Optional<String> id2 = o2.getSubgroup();
			if (!id1.equals(id2)) {
				if (id1.isPresent() && id2.isPresent()) {
					Optional<ItemSubGroupPrototype> p1 = o1.getTable().getItemSubgroup(id1.get());
					Optional<ItemSubGroupPrototype> p2 = o2.getTable().getItemSubgroup(id2.get());
					if (p1.isPresent() && p2.isPresent()) {
						return p1.get().compareTo(p2.get());
					}
					return p1.isPresent() ? 1 : -1;
				}
				return id1.isPresent() ? 1 : -1;
			}
			return 0;
		}
	}).thenComparing(p -> p.getOrder()).thenComparing(p -> p.getName());

	private final LuaTable lua;
	private final String name;
	private final String type;
	private final String order;
	private final Optional<String> subgroup;

	private DataTable table;

	protected Optional<String> group;

	public DataPrototype(LuaTable lua, String name, String type) {
		this.lua = lua;
		this.name = name;
		this.type = type;

		order = lua.get("order").optjstring("");
		subgroup = Optional.ofNullable(lua.get("subgroup").optjstring(null));
	}

	@Override
	public int compareTo(DataPrototype other) {
		return ORDER_COMPARE.compare(this, other);
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

	public Optional<String> getGroup() {
		return group;
	}

	public String getName() {
		return name;
	}

	public String getOrder() {
		return order;
	}

	public Optional<String> getSubgroup() {
		return subgroup;
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

	public void setGroup(Optional<String> group) {
		this.group = group;
	}

	public void setTable(DataTable table) {
		this.table = table;
	}
}