package com.demod.factorio.prototype;

import java.util.LinkedHashMap;
import java.util.Map;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import com.demod.factorio.Utils;

public class RecipePrototype extends DataPrototype {

	private final String category;
	private final Map<String, Integer> inputs = new LinkedHashMap<>();
	private final Map<String, Double> outputs = new LinkedHashMap<>();
	private final double energyRequired;
	private final boolean handCraftable;
	private final boolean recycling;

	public RecipePrototype(LuaTable lua, String name, String type) {
		super(lua, name, type);

		boolean hidden = lua.get("hidden").optboolean(false);

		LuaValue ingredientsLua = lua.get("ingredients").opttable(new LuaTable());
		if (!hidden) {
			Utils.forEach(ingredientsLua, lv -> {
				if (lv.get("name").isnil()) {
					inputs.put(lv.get(1).tojstring(), lv.get(2).toint());
				} else {
					inputs.put(lv.get("name").tojstring(), lv.get("amount").toint());
				}
			});
		}

		LuaTable resultLua = lua.get("results").opttable(new LuaTable());
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
		category = lua.get("category").optjstring("crafting");
		// FIXME get these from the character prototype
		handCraftable = category.equals("crafting") || category.equals("electronics") || category.equals("pressing")
				|| category.equals("recycling-or-hand-crafing") || category.equals("organic-or-hand-crafing")
				|| category.equals("organic-or-assembling");
		recycling = category.equals("recycling");
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
