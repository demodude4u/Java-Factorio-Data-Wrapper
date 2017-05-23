package com.demod.factorio;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import com.demod.factorio.prototype.DataPrototype;
import com.demod.factorio.prototype.RecipePrototype;
import com.demod.factorio.prototype.TechPrototype;

public class DataTable {
	private final Map<String, DataPrototype> entities = new LinkedHashMap<>();
	private final Map<String, DataPrototype> items = new LinkedHashMap<>();
	private final Map<String, RecipePrototype> recipes = new LinkedHashMap<>();
	private final Map<String, RecipePrototype> expensiveRecipes = new LinkedHashMap<>();
	private final Map<String, DataPrototype> fluids = new LinkedHashMap<>();
	private final Map<String, TechPrototype> technologies = new LinkedHashMap<>();

	private final Set<String> worldInputs = new LinkedHashSet<>();

	public DataTable(TypeHiearchy typeHiearchy, LuaTable dataLua, JSONObject excludeDataJson) {
		Set<String> excludedRecipesAndItems = asStringSet(excludeDataJson.getJSONArray("recipes-and-items"));

		LuaTable rawLua = dataLua.get("raw").checktable();
		Utils.forEach(rawLua, v -> {
			Utils.forEach(v.checktable(), protoLua -> {
				String type = protoLua.get("type").toString();
				String name = protoLua.get("name").toString();
				if (typeHiearchy.isAssignable("item", type) && !excludedRecipesAndItems.contains(name)) {
					items.put(name, new DataPrototype(protoLua.checktable(), name, type));
				} else if (typeHiearchy.isAssignable("recipe", type) && !excludedRecipesAndItems.contains(name)) {
					recipes.put(name, new RecipePrototype(protoLua.checktable(), name, type, false));
					expensiveRecipes.put(name, new RecipePrototype(protoLua.checktable(), name, type, true));
				} else if (typeHiearchy.isAssignable("entity", type)) {
					entities.put(name, new DataPrototype(protoLua.checktable(), name, type));
				} else if (typeHiearchy.isAssignable("fluid", type)) {
					fluids.put(name, new DataPrototype(protoLua.checktable(), name, type));
				} else if (typeHiearchy.isAssignable("technology", type)) {
					technologies.put(name,
							new TechPrototype(protoLua.checktable(), name, type, excludedRecipesAndItems));
				}
			});
		});

		for (RecipePrototype recipe : getRecipes().values()) {
			for (String input : recipe.getInputs().keySet()) {
				worldInputs.add(input);
			}
		}
		for (RecipePrototype recipe : getRecipes().values()) {
			for (String output : recipe.getOutputs().keySet()) {
				worldInputs.remove(output);
			}
		}

		// getTechnologies().values().stream().forEach(tech -> {
		// LuaValue countFormulaLua =
		// tech.lua().get("unit").get("count_formula");
		// if (!countFormulaLua.isnil()) {
		// System.out.println("COUNT FORMULA: " + tech.getName());
		// FactorioData.parseCountFormula(countFormulaLua);
		// }
		// });

		getTechnologies().values().stream().filter(t -> t.isUpgrade() && t.getName().endsWith("-1"))
				.forEach(firstBonus -> {
					String bonusMatch = firstBonus.getName().substring(0, firstBonus.getName().length() - 1);
					String bonusName = bonusMatch.substring(0, bonusMatch.length() - 1);
					LuaValue countFormulaLua = firstBonus.lua().get("unit").get("count_formula");
					IntUnaryOperator bonusCountFormula = !countFormulaLua.isnil()
							? FactorioData.parseCountFormula(countFormulaLua.tojstring()) : null;
					firstBonus.setFirstBonus(true);
					List<TechPrototype> bonusGroup = getTechnologies().values().stream()
							.filter(bonus -> bonus.getName().startsWith(bonusMatch)).collect(Collectors.toList());
					bonusGroup.forEach(bonus -> {
						bonus.setBonus(true);
						bonus.setBonusName(bonusName);
						bonus.setBonusGroup(bonusGroup);

						int bonusLevel = -Integer.parseInt(bonus.getName().replace(bonusName, ""));
						if (bonusCountFormula != null) {
							bonus.setCount(bonusCountFormula.applyAsInt(bonusLevel));
						}
						bonus.setBonusLevel(bonusLevel);
					});
				});
	}

	private Set<String> asStringSet(JSONArray jsonArray) {
		Set<String> ret = new LinkedHashSet<>();
		Utils.forEach(jsonArray, ret::add);
		return ret;
	}

	public Map<String, DataPrototype> getEntities() {
		return entities;
	}

	public Map<String, RecipePrototype> getExpensiveRecipes() {
		return expensiveRecipes;
	}

	public Map<String, DataPrototype> getFluids() {
		return fluids;
	}

	public Map<String, DataPrototype> getItems() {
		return items;
	}

	public Map<String, RecipePrototype> getRecipes() {
		return recipes;
	}

	public Map<String, TechPrototype> getTechnologies() {
		return technologies;
	}

	public Set<String> getWorldInputs() {
		return worldInputs;
	}
}
