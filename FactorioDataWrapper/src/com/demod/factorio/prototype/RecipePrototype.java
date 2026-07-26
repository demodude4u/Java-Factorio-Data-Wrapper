package com.demod.factorio.prototype;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;

import com.demod.factorio.DataTable;
import com.demod.factorio.Utils;
import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.fakelua.LuaValue;

public class RecipePrototype extends DataPrototype {

	private final String category;
	private final Set<String> categories = new LinkedHashSet<>();
	private final Map<String, Integer> inputs = new LinkedHashMap<>();
	private final Map<String, Double> outputs = new LinkedHashMap<>();
	private final double energyRequired;
	private boolean handCraftable;
	private final boolean recycling;

	public RecipePrototype(LuaTable lua) {
		super(lua);

		boolean hidden = lua.get("hidden").optboolean(false);

		LuaTable ingredientsLua = lua.get("ingredients").opttable(new LuaTable(new JSONArray()));
		if (!hidden) {
			Utils.forEach(ingredientsLua, lv -> {
				if (lv.get("name").isnil()) {
					inputs.put(lv.get(1).tojstring(), lv.get(2).toint());
				} else {
					inputs.put(lv.get("name").tojstring(), lv.get("amount").toint());
				}
			});
		}

		LuaTable resultLua = lua.get("results").opttable(new LuaTable(new JSONArray()));
		if (!hidden) {
			Utils.forEach(resultLua, lv -> {
				LuaValue probabilityLua = lv.get("probability");
				if (probabilityLua.isnil()) {
					outputs.put(lv.get("name").tojstring(), (double) lv.get("amount").toint());
				} else {
					outputs.put(lv.get("name").tojstring(), probabilityLua.todouble());
				}
			});
		}

		energyRequired = lua.get("energy_required").optdouble(0.5);
		LuaValue categoriesLua = lua.get("categories");
		if (categoriesLua.isnil()) {
			categories.add(lua.get("category").optjstring("crafting"));
		} else {
			Utils.forEach(categoriesLua.checktable(), categoryLua -> categories.add(categoryLua.tojstring()));
		}
		category = categories.iterator().next();
		recycling = categories.contains("recycling");
	}

	public String getCategory() {
		return category;
	}

	public double getEnergyRequired() {
		return energyRequired;
	}

	public Map<String, Integer> getInputs() {
		return inputs;
	}

	public Map<String, Double> getOutputs() {
		return outputs;
	}

	public boolean isHandCraftable() {
		return handCraftable;
	}

	public boolean isRecycling() {
		return recycling;
	}

	@Override
	public void setTable(DataTable table) {
		super.setTable(table);
		handCraftable = categories.stream().anyMatch(table.getCharacterCraftingCategories()::contains);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Recipe: " + getName() + (!isHandCraftable() ? " (MACHINE ONLY)" : "") + "\n");
		sb.append("\tTIME " + getEnergyRequired() + "\n");
		getInputs().forEach((k, v) -> {
			sb.append("\tIN " + k + " " + v + "\n");
		});
		getOutputs().forEach((k, v) -> {
			sb.append("\tOUT " + k + " " + v + "\n");
		});
		return sb.toString();
	}
}
