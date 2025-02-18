package com.demod.factorio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.fakelua.LuaValue;
import com.demod.factorio.port.SimpleMathFormula;
import com.demod.factorio.port.SimpleMathFormula.Expression;
import com.demod.factorio.port.SimpleMathFormula.InputException;
import com.demod.factorio.prototype.EntityPrototype;
import com.demod.factorio.prototype.EquipmentPrototype;
import com.demod.factorio.prototype.FluidPrototype;
import com.demod.factorio.prototype.ItemPrototype;
import com.demod.factorio.prototype.RecipePrototype;
import com.demod.factorio.prototype.TechPrototype;
import com.demod.factorio.prototype.TilePrototype;

public class DataTable {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataTable.class);

	private final TypeHierarchy typeHierarchy;
	private final LuaTable rawLua;

	private final JSONObject nameMappingTechnologies;
	private final JSONObject nameMappingItemsRecipes;

	private final Map<String, EntityPrototype> entities = new LinkedHashMap<>();
	private final Map<String, ItemPrototype> items = new LinkedHashMap<>();
	private final Map<String, RecipePrototype> recipes = new LinkedHashMap<>();
	private final Map<String, FluidPrototype> fluids = new LinkedHashMap<>();
	private final Map<String, TechPrototype> technologies = new LinkedHashMap<>();
	private final Map<String, EquipmentPrototype> equipments = new LinkedHashMap<>();
	private final Map<String, TilePrototype> tiles = new LinkedHashMap<>();

	private final Map<String, List<EntityPrototype>> craftingCategories = new LinkedHashMap<>();

	// probably bad code style
	private final Set<String> explicitelyIncludedEntities = new LinkedHashSet<>();;

	private final Set<String> worldInputs = new LinkedHashSet<>();

	private FactorioData data;

	public DataTable(TypeHierarchy typeHierarchy, LuaTable rawLua, JSONObject excludeDataJson,
			JSONObject includeDataJson, JSONObject wikiNamingJson) {
		this.typeHierarchy = typeHierarchy;
		this.rawLua = rawLua;

		Set<String> excludedRecipesAndItems = asStringSet(excludeDataJson.getJSONArray("recipes-and-items"));
		Set<String> excludedTechnologies = asStringSet(excludeDataJson.getJSONArray("technologies"));
		Set<String> excludedFluids = asStringSet(excludeDataJson.getJSONArray("fluids"));
		Set<String> excludedEntities = asStringSet(excludeDataJson.getJSONArray("entities"));
		this.explicitelyIncludedEntities.addAll(asStringSet(includeDataJson.getJSONArray("entities")));

		nameMappingTechnologies = wikiNamingJson.getJSONObject("technologies");
		nameMappingItemsRecipes = wikiNamingJson.getJSONObject("items and recipes");

		Utils.forEach(rawLua, v -> {
			Utils.forEach(v.checktable(), protoLua -> {
				String type = protoLua.get("type").tojstring();
				String name = protoLua.get("name").tojstring();
				try {
					if (typeHierarchy.isAssignable("item", type) && !excludedRecipesAndItems.contains(name)) {
						items.put(name, new ItemPrototype(protoLua.checktable(), name, type));
					} else if (typeHierarchy.isAssignable("recipe", type) && !excludedRecipesAndItems.contains(name)) {
						recipes.put(name, new RecipePrototype(protoLua.checktable(), name, type));
					} else if (typeHierarchy.isAssignable("entity", type) && !excludedEntities.contains(name)) {
						entities.put(name, new EntityPrototype(protoLua.checktable(), name, type));
					} else if (typeHierarchy.isAssignable("fluid", type) && !excludedFluids.contains(name)) {
						fluids.put(name, new FluidPrototype(protoLua.checktable(), name, type));
					} else if (typeHierarchy.isAssignable("technology", type) && !excludedTechnologies.contains(name)) {
						technologies.put(name,
								new TechPrototype(protoLua.checktable(), name, type, excludedRecipesAndItems));
					} else if (typeHierarchy.isAssignable("equipment", type)) {
						equipments.put(name, new EquipmentPrototype(protoLua.checktable(), name, type));
					} else if (typeHierarchy.isAssignable("tile", type)) {
						tiles.put(name, new TilePrototype(protoLua.checktable(), name, type));
					}
				} catch (Exception e) {
					LOGGER.error(">>>>> EXCEPTION FOR {} ({})", name, type);
					LOGGER.error(protoLua.getJson().toString());
					throw e;
				}
			});
		});

		items.values().forEach(p -> p.setTable(this));
		recipes.values().forEach(p -> p.setTable(this));
		entities.values().forEach(p -> p.setTable(this));
		fluids.values().forEach(p -> p.setTable(this));
		technologies.values().forEach(p -> p.setTable(this));
		equipments.values().forEach(p -> p.setTable(this));
		tiles.values().forEach(p -> p.setTable(this));

		for (ItemPrototype item : items.values()) {
			LuaValue luaPlaceResult = item.lua().get("place_result");
			if (!luaPlaceResult.isnil()) {
				EntityPrototype entity = entities.get(luaPlaceResult.tojstring());
				entity.addPlacedBy(new ItemToPlace(item.getName(), 1));
			}
			LuaValue luaPlaceAsTile = item.lua().get("place_as_tile");
			if (!luaPlaceAsTile.isnil()) {
				TilePrototype tile = tiles.get(luaPlaceAsTile.get("result").tojstring());
				tile.addPlacedBy(new ItemToPlace(item.getName(), 1));
			}
		}

		for (EntityPrototype entity : entities.values()) {
			List<ItemToPlace> placedBy = entity.getPlacedBy();
			Optional<ItemToPlace> primaryItem;
			if (placedBy.isEmpty()) {
				primaryItem = Optional.empty();
			} else if (placedBy.size() == 1) {
				primaryItem = Optional.of(placedBy.get(0));
			} else {
				Optional<String> flaggedPrimaryItem = placedBy.stream().map(i -> items.get(i.getItem()))
						.filter(i -> i.getFlags().contains("primary-place-result")).map(i -> i.getName()).findFirst();
				if (flaggedPrimaryItem.isPresent()) {
					primaryItem = placedBy.stream().filter(i -> i.getItem().equals(flaggedPrimaryItem.get())).findAny();
				} else {
					primaryItem = Optional.of(placedBy.get(0));
				}
			}
			entity.setPrimaryItem(primaryItem);
		}

		for (TilePrototype tile : tiles.values()) {
			List<ItemToPlace> placedBy = tile.getPlacedBy();
			Optional<ItemToPlace> primaryItem;
			if (placedBy.isEmpty()) {
				primaryItem = Optional.empty();
			} else if (placedBy.size() == 1) {
				primaryItem = Optional.of(placedBy.get(0));
			} else {
				Optional<String> flaggedPrimaryItem = placedBy.stream().map(i -> items.get(i.getItem()))
						.filter(i -> i.getFlags().contains("primary-place-result")).map(i -> i.getName()).findFirst();
				if (flaggedPrimaryItem.isPresent()) {
					primaryItem = placedBy.stream().filter(i -> i.getItem().equals(flaggedPrimaryItem.get())).findAny();
				} else {
					primaryItem = Optional.of(placedBy.get(0));
				}
			}
			tile.setPrimaryItem(primaryItem);
		}

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

		technologies.values().stream()
				.filter(t -> (t.isUpgrade() || t.isMaxLevelInfinite()) && t.getName().endsWith("-1"))
				.forEach(firstBonus -> {
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
									Optional.of(parseCountFormula(countFormulaLua.tojstring())));
						}
					}
				});

		this.entities.values().stream().filter(e -> !excludedRecipesAndItems.contains(e.getName())).forEach(e -> {
			LuaValue categories = e.lua().get("crafting_categories");
			if (!categories.isnil()) {
				Utils.forEach(categories.totableArray(), cat -> {
					this.craftingCategories.putIfAbsent(cat.toString(), new ArrayList<EntityPrototype>());
					this.craftingCategories.get(cat.toString()).add(e);
				});
			}
		});
	}

	private Set<String> asStringSet(JSONArray jsonArray) {
		Set<String> ret = new LinkedHashSet<>();
		Utils.forEach(jsonArray, ret::add);
		return ret;
	}

	public Map<String, List<EntityPrototype>> getCraftingCategories() {
		return craftingCategories;
	}

	public FactorioData getData() {
		return data;
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

	public Set<String> getExplicitelyIncludedEntities() {
		return explicitelyIncludedEntities;
	}

	public Optional<FluidPrototype> getFluid(String name) {
		return Optional.ofNullable(fluids.get(name));
	}

	public Map<String, FluidPrototype> getFluids() {
		return fluids;
	}

	public Optional<ItemPrototype> getItem(String name) {
		return Optional.ofNullable(items.get(name));
	}

	public Map<String, ItemPrototype> getItems() {
		return items;
	}

	public Optional<LuaValue> getRaw(String... path) {
		LuaValue retLua = rawLua.tovalue();
		for (String key : path) {
			retLua = retLua.get(key);
			if (retLua.isnil()) {
				return Optional.empty();
			}
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

	public TypeHierarchy getTypeHierarchy() {
		return typeHierarchy;
	}

	private String getWikiDefaultName(String name) {
		String[] split = name.split("-|_");
		String formatted = Character.toUpperCase(split[0].charAt(0)) + split[0].substring(1);
		if (formatted.equals("Uranium") && split.length == 2 && split[1].startsWith("2")) {
			return formatted + "-" + split[1];
		}
		for (int i = 1; i < split.length; i++) {
			formatted += " " + split[i];
		}
		return formatted;
	}

	public String getWikiEntityName(String name) {
		return getWikiName(name, nameMappingItemsRecipes);
	}

	public String getWikiFluidName(String name) {
		return getWikiName(name, nameMappingItemsRecipes);
	}

	public String getWikiItemName(String name) {
		if (name.equals(TotalRawCalculator.RAW_TIME)) {
			return "Time (Seconds)";
		}
		return getWikiName(name, nameMappingItemsRecipes);
	}

	private String getWikiName(String name, JSONObject nameMappingJson) {
		String ret = nameMappingJson.optString(name, null);
		if (ret == null) {
			LOGGER.warn("Wiki mapping not found for {} - \"{}\"", name, getWikiDefaultName(name));
			nameMappingJson.put(name, ret = getWikiDefaultName(name));
		}
		return ret;
	}

	public String getWikiRecipeName(String name) {
		return getWikiName(name, nameMappingItemsRecipes);
	}

	public String getWikiTechnologyName(String name) {
		return getWikiName(name, nameMappingTechnologies);
	}

	public Set<String> getWorldInputs() {
		return worldInputs;
	}

	public boolean hasWikiEntityName(String name) {
		return nameMappingItemsRecipes.optString(name, null) != null;
	}

	public IntUnaryOperator parseCountFormula(String countFormula) {
		Expression expression;
		try {
			expression = SimpleMathFormula.Expression.parse(countFormula, 0);
		} catch (InputException e) {
			LOGGER.error("COUNT FORMULA PARSE FAIL: {}", countFormula);
			e.printStackTrace();
			return l -> -1;
		}
		Map<String, Double> values = new HashMap<>();
		return level -> {
			values.put("L", (double) level);
			return (int) expression.evaluate(values);
		};
	}

	public void setData(FactorioData data) {
		this.data = data;
	}
}
