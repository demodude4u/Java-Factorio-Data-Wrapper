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

	public RecipePrototype(LuaTable lua, String name, String type, boolean expensive) {
		super(lua, name, type);

		LuaValue difficultyLua = lua.get(expensive ? "expensive" : "normal");
		if (!difficultyLua.isnil()) {
			Utils.forEach(difficultyLua, (k, v) -> {
				lua.set(k, v);
			});
		}

		LuaValue ingredientsLua = lua.get("ingredients");
		Utils.forEach(ingredientsLua, lv -> {
			if (lv.get("name").isnil()) {
				inputs.put(lv.get(1).tojstring(), lv.get(2).toint());
			} else {
				inputs.put(lv.get("name").tojstring(), lv.get("amount").toint());
			}
		});

		LuaValue resultLua = lua.get("result");
		if (resultLua.isnil()) {
			resultLua = lua.get("results");
		}
		if (resultLua.istable()) {
			Utils.forEach(resultLua, lv -> {
				if (lv.get("name").isnil()) {
					outputs.put(lv.get(1).tojstring(), (double) lv.get(2).toint());
				} else {
					LuaValue probabilityLua = lv.get("probability");
					if (probabilityLua.isnil()) {
						outputs.put(lv.get("name").tojstring(), (double) lv.get("amount").toint());
					} else {
						outputs.put(lv.get("name").tojstring(), probabilityLua.todouble());
					}
				}
			});
		} else {
			outputs.put(resultLua.tojstring(), (double) lua.get("result_count").optint(1));
		}

		energyRequired = lua.get("energy_required").optdouble(0.5);
		category = lua.get("category").optjstring("crafting");
		handCraftable = category.equals("crafting");
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
