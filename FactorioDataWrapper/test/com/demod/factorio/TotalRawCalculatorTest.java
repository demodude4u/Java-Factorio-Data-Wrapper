package com.demod.factorio;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.prototype.RecipePrototype;

public class TotalRawCalculatorTest {

	@Test
	public void ignoresRecipesThatDisallowDecomposition() {
		RecipePrototype root = recipe("root", "crafting", true, 1, map("intermediate", 2),
				map("root", 1));
		RecipePrototype recycling = recipe("intermediate-recycling", "recycling", false, 1,
				map("intermediate", 1), map("intermediate", 1));
		RecipePrototype intermediate = recipe("intermediate", "crafting", true, 3, map("ore", 4),
				map("intermediate", 1));
		Map<String, RecipePrototype> recipes = recipes(root, recycling, intermediate);

		Map<String, Double> totalRaw = new TotalRawCalculator(recipes).compute(root);

		assertEquals(Map.of(TotalRawCalculator.RAW_TIME, 7.0, "ore", 8.0), totalRaw);
	}

	@Test
	public void treatsIngredientsAsRawWhenTheirRecipesDisallowDecomposition() {
		RecipePrototype root = recipe("root", "crafting", true, 2, map("asteroid-chunk", 3),
				map("root", 1));
		RecipePrototype crushing = recipe("asteroid-crushing", "crafting", false, 5,
				map("asteroid-chunk", 1), map("asteroid-chunk", 1));
		Map<String, RecipePrototype> recipes = recipes(root, crushing);

		Map<String, Double> totalRaw = new TotalRawCalculator(recipes).compute(root);

		assertEquals(Map.of(TotalRawCalculator.RAW_TIME, 2.0, "asteroid-chunk", 3.0), totalRaw);
	}

	@Test
	public void treatsIngredientsAsRawWhenTheirRecipesAreNotHandCraftable() {
		RecipePrototype root = recipe("root", "crafting", true, 2, map("intermediate", 3), map("root", 1));
		RecipePrototype machineRecipe = recipe("intermediate", "metallurgy", true, 5, map("ore", 4),
				map("intermediate", 1));
		Map<String, RecipePrototype> recipes = recipes(root, machineRecipe);

		Map<String, Double> totalRaw = new TotalRawCalculator(recipes).compute(root);

		assertEquals(Map.of(TotalRawCalculator.RAW_TIME, 2.0, "intermediate", 3.0), totalRaw);
	}

	@Test
	public void ignoresRecyclingRecipes() {
		RecipePrototype root = recipe("root", "crafting", true, 1, map("intermediate", 2), map("root", 1));
		RecipePrototype recycling = recipe("intermediate-recycling", "recycling", true, 1,
				map("intermediate", 1), map("intermediate", 1));
		RecipePrototype intermediate = recipe("intermediate", "crafting", true, 3, map("ore", 4),
				map("intermediate", 1));
		Map<String, RecipePrototype> recipes = recipes(root, recycling, intermediate);

		Map<String, Double> totalRaw = new TotalRawCalculator(recipes).compute(root);

		assertEquals(Map.of(TotalRawCalculator.RAW_TIME, 7.0, "ore", 8.0), totalRaw);
	}

	@Test
	public void treatsTheRepeatedIngredientAsRawWhenRecipesFormACycle() {
		RecipePrototype root = recipe("root", "crafting", true, 1, map("first", 1), map("root", 1));
		RecipePrototype first = recipe("first", "crafting", true, 2, map("second", 1), map("first", 1));
		RecipePrototype second = recipe("second", "crafting", true, 3, map("first", 1), map("second", 1));
		Map<String, RecipePrototype> recipes = recipes(root, first, second);

		Map<String, Double> totalRaw = new TotalRawCalculator(recipes).compute(root);

		assertEquals(Map.of(TotalRawCalculator.RAW_TIME, 6.0, "first", 1.0), totalRaw);
	}

	@Test
	public void decomposesNutrientsUsingTheLastEligibleRecipeInPrototypeOrder() {
		RecipePrototype root = recipe("root", "crafting", true, 1, map("nutrients", 100), map("root", 1));
		RecipePrototype fish = recipe("nutrients-from-fish", "c[nutrients-from-fish]", "crafting", true, 2,
				map("raw-fish", 1), map("nutrients", 20));
		RecipePrototype biterEgg = recipe("nutrients-from-biter-egg", "d[nutrients-from-biter-egg]", "crafting", true,
				2, map("biter-egg", 1), map("nutrients", 20));
		RecipePrototype biterEggProduction = recipe("biter-egg", "captive-spawner-process", true, 10, Map.of(),
				map("biter-egg", 5));
		Map<String, RecipePrototype> recipes = recipes(root, biterEgg, fish, biterEggProduction);

		Map<String, Double> totalRaw = new TotalRawCalculator(recipes).compute(root);

		assertEquals(Map.of(TotalRawCalculator.RAW_TIME, 11.0, "biter-egg", 5.0), totalRaw);
	}

	@Test
	public void decomposesNutrientsThroughFishWhenThatIsTheOnlyEligibleRecipe() {
		RecipePrototype root = recipe("root", "crafting", true, 1, map("nutrients", 20), map("root", 1));
		RecipePrototype fish = recipe("nutrients-from-fish", "crafting", true, 2, map("raw-fish", 1),
				map("nutrients", 20));
		RecipePrototype bioflux = recipe("nutrients-from-bioflux", "organic", true, 3, map("bioflux", 5),
				map("nutrients", 20));
		Map<String, RecipePrototype> recipes = recipes(root, fish, bioflux);

		Map<String, Double> totalRaw = new TotalRawCalculator(recipes).compute(root);

		assertEquals(Map.of(TotalRawCalculator.RAW_TIME, 3.0, "raw-fish", 1.0), totalRaw);
	}

	private static Map<String, Integer> map(String name, int amount) {
		return Map.of(name, amount);
	}

	private static Map<String, RecipePrototype> recipes(RecipePrototype... recipes) {
		Map<String, RecipePrototype> result = new LinkedHashMap<>();
		for (RecipePrototype recipe : recipes) {
			result.put(recipe.getName(), recipe);
		}
		return result;
	}

	private static RecipePrototype recipe(String name, String category, boolean decomposable, double energyRequired,
			Map<String, Integer> inputs, Map<String, Integer> outputs) {
		return recipe(name, name, category, decomposable, energyRequired, inputs, outputs);
	}

	private static RecipePrototype recipe(String name, String order, String category, boolean decomposable,
			double energyRequired, Map<String, Integer> inputs, Map<String, Integer> outputs) {
		JSONObject json = new JSONObject();
		json.put("type", "recipe");
		json.put("name", name);
		json.put("order", order);
		json.put("category", category);
		json.put("allow_decomposition", decomposable);
		json.put("energy_required", energyRequired);
		json.put("ingredients", ingredients(inputs));
		json.put("results", results(outputs));
		RecipePrototype recipe = new RecipePrototype(new LuaTable(json));
		recipe.setGroup(Optional.empty());
		return recipe;
	}

	private static JSONArray ingredients(Map<String, Integer> inputs) {
		JSONArray ingredients = new JSONArray();
		inputs.forEach((name, amount) -> ingredients.put(new JSONObject().put("name", name).put("amount", amount)));
		return ingredients;
	}

	private static JSONArray results(Map<String, Integer> outputs) {
		JSONArray results = new JSONArray();
		outputs.forEach((name, amount) -> results.put(new JSONObject().put("name", name).put("amount", amount)));
		return results;
	}
}
