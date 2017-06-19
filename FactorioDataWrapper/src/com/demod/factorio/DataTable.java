package com.demod.factorio;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import com.demod.factorio.prototype.DataPrototype;
import com.demod.factorio.prototype.EntityPrototype;
import com.demod.factorio.prototype.EquipmentPrototype;
import com.demod.factorio.prototype.FluidPrototype;
import com.demod.factorio.prototype.ItemPrototype;
import com.demod.factorio.prototype.RecipePrototype;
import com.demod.factorio.prototype.TechPrototype;
import com.demod.factorio.prototype.TilePrototype;

public class DataTable {
	private final TypeHiearchy typeHiearchy;
	private final LuaTable rawLua;

	private final Map<String, EntityPrototype> entities = new LinkedHashMap<>();
	private final Map<String, ItemPrototype> items = new LinkedHashMap<>();
	private final Map<String, RecipePrototype> recipes = new LinkedHashMap<>();
	private final Map<String, RecipePrototype> expensiveRecipes = new LinkedHashMap<>();
	private final Map<String, FluidPrototype> fluids = new LinkedHashMap<>();
	private final Map<String, TechPrototype> technologies = new LinkedHashMap<>();
	private final Map<String, EquipmentPrototype> equipments = new LinkedHashMap<>();
	private final Map<String, TilePrototype> tiles = new LinkedHashMap<>();

	private final Set<String> worldInputs = new LinkedHashSet<>();

	public DataTable(TypeHiearchy typeHiearchy, LuaTable dataLua, JSONObject excludeDataJson) {
		this.typeHiearchy = typeHiearchy;
		this.rawLua = dataLua.get("raw").checktable();

		Set<String> excludedRecipesAndItems = asStringSet(excludeDataJson.getJSONArray("recipes-and-items"));

		Utils.forEach(rawLua, v -> {
			Utils.forEach(v.checktable(), protoLua -> {
				String type = protoLua.get("type").tojstring();
				String name = protoLua.get("name").tojstring();
				if (typeHiearchy.isAssignable("item", type) && !excludedRecipesAndItems.contains(name)) {
					items.put(name, new ItemPrototype(protoLua.checktable(), name, type));
				} else if (typeHiearchy.isAssignable("recipe", type) && !excludedRecipesAndItems.contains(name)) {
					recipes.put(name, new RecipePrototype(protoLua.checktable(), name, type, false));
					expensiveRecipes.put(name, new RecipePrototype(protoLua.checktable(), name, type, true));
				} else if (typeHiearchy.isAssignable("entity", type)) {
					entities.put(name, new EntityPrototype(protoLua.checktable(), name, type));
				} else if (typeHiearchy.isAssignable("fluid", type)) {
					fluids.put(name, new FluidPrototype(protoLua.checktable(), name, type));
				} else if (typeHiearchy.isAssignable("technology", type)) {
					technologies.put(name,
							new TechPrototype(protoLua.checktable(), name, type, excludedRecipesAndItems));
				} else if (typeHiearchy.isAssignable("equipment", type)) {
					equipments.put(name, new EquipmentPrototype(protoLua.checktable(), name, type));
				} else if (typeHiearchy.isAssignable("tile", type)) {
					tiles.put(name, new TilePrototype(protoLua.checktable(), name, type));
				}
			});
		});

		for (RecipePrototype recipe : recipes.values()) {
			for (String input : recipe.getInputs().keySet()) {
				worldInputs.add(input);
			}
		}
		for (RecipePrototype recipe : recipes.values()) {
			for (String output : recipe.getOutputs().keySet()) {
				worldInputs.remove(output);
			}
		}

		technologies.values().stream().filter(t -> t.isUpgrade() && t.getName().endsWith("-1")).forEach(firstBonus -> {
			String bonusMatch = firstBonus.getName().substring(0, firstBonus.getName().length() - 1);
			String bonusName = bonusMatch.substring(0, bonusMatch.length() - 1);

			firstBonus.setFirstBonus(true);
			List<TechPrototype> bonusGroup = technologies.values().stream()
					.filter(bonus -> bonus.getName().startsWith(bonusMatch))
					.peek(b -> b.setBonusLevel(-Integer.parseInt(b.getName().replace(bonusName, ""))))
					.sorted((b1, b2) -> Integer.compare(b1.getBonusLevel(), b2.getBonusLevel()))
					.collect(Collectors.toList());

			for (TechPrototype bonus : bonusGroup) {
				bonus.setBonus(true);
				bonus.setBonusName(bonusName);
				bonus.setBonusGroup(bonusGroup);

				LuaValue countFormulaLua = bonus.lua().get("unit").get("count_formula");
				if (!countFormulaLua.isnil()) {
					bonus.setBonusFormula(Optional.of(countFormulaLua.tojstring()),
							Optional.of(FactorioData.parseCountFormula(countFormulaLua.tojstring())));
				}
			}
		});
	}

	private Set<String> asStringSet(JSONArray jsonArray) {
		Set<String> ret = new LinkedHashSet<>();
		Utils.forEach(jsonArray, ret::add);
		return ret;
	}

	public Map<String, EntityPrototype> getEntities() {
		return entities;
	}

	public Optional<EntityPrototype> getEntity(String name) {
		return Optional.ofNullable(entities.get(name));
	}

	public Optional<EquipmentPrototype> getEquipment(String name) {
		return Optional.ofNullable(equipments.get(name));
	}

	public Map<String, EquipmentPrototype> getEquipments() {
		return equipments;
	}

	public Optional<RecipePrototype> getExpensiveRecipe(String name) {
		return Optional.ofNullable(expensiveRecipes.get(name));
	}

	public Map<String, RecipePrototype> getExpensiveRecipes() {
		return expensiveRecipes;
	}

	public Optional<DataPrototype> getFluid(String name) {
		return Optional.ofNullable(fluids.get(name));
	}

	public Map<String, FluidPrototype> getFluids() {
		return fluids;
	}

	public Optional<DataPrototype> getItem(String name) {
		return Optional.ofNullable(items.get(name));
	}

	public Map<String, ItemPrototype> getItems() {
		return items;
	}

	public Optional<LuaValue> getRaw(String key) {
		LuaValue retLua = rawLua.get(key);
		if (retLua.isnil()) {
			return Optional.empty();
		}
		return Optional.of(retLua);
	}

	public LuaTable getRawLua() {
		return rawLua;
	}

	public Optional<RecipePrototype> getRecipe(String name) {
		return Optional.ofNullable(recipes.get(name));
	}

	public Map<String, RecipePrototype> getRecipes() {
		return recipes;
	}

	public Map<String, TechPrototype> getTechnologies() {
		return technologies;
	}

	public Optional<TechPrototype> getTechnology(String name) {
		return Optional.ofNullable(technologies.get(name));
	}

	public Optional<TilePrototype> getTile(String name) {
		return Optional.ofNullable(tiles.get(name));
	}

	public Map<String, TilePrototype> getTiles() {
		return tiles;
	}

	public TypeHiearchy getTypeHiearchy() {
		return typeHiearchy;
	}

	public Set<String> getWorldInputs() {
		return worldInputs;
	}
}
