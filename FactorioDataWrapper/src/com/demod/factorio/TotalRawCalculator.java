package com.demod.factorio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import com.demod.factorio.prototype.RecipePrototype;

public class TotalRawCalculator {
	public static final String RAW_TIME = "_TIME_";
	private final Map<String, RecipePrototype> recipes;
	private final Map<String, Map<String, Double>> recipeTotalRaws = new LinkedHashMap<>();

	public TotalRawCalculator(Map<String, RecipePrototype> recipes) {
		this.recipes = recipes;
	}

	public Map<String, Double> compute(RecipePrototype recipe) {
		Map<String, Double> totalRaw = new LinkedHashMap<>();
		recipeTotalRaws.put(recipe.getName(), totalRaw);
		totalRaw.put(TotalRawCalculator.RAW_TIME, recipe.getEnergyRequired());

		for (Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
			String input = entry.getKey();
			Optional<RecipePrototype> findRecipe = recipes.values().stream()
					// XXX the nutrients-from-fish filter is here to match the bad Factorio behavior
					// of picking nutrients from biter eggs
					.filter(r -> !r.getName().equals("nutrients-from-fish")).filter(RecipePrototype::isHandCraftable)
					.filter(r -> r.getOutputs().keySet().stream().anyMatch(i -> {
						return i.equals(input);
					})).findFirst();
			if (findRecipe.isPresent()) {
				RecipePrototype inputRecipe = findRecipe.get();
				Map<String, Double> inputTotalRaw = compute(inputRecipe);
				Double inputRunYield = inputRecipe.getOutputs().get(input);
				double inputRunCount = entry.getValue() / inputRunYield;
				inputTotalRaw.forEach((k, v) -> {
					totalRaw.put(k, totalRaw.getOrDefault(k, 0.0) + v * inputRunCount);
				});
			} else {
				totalRaw.put(input, totalRaw.getOrDefault(input, 0.0) + entry.getValue());
			}
		}

		return totalRaw;
	}
}