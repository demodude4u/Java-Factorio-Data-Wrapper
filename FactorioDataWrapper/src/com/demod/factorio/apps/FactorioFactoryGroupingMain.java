package com.demod.factorio.apps;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.json.JSONException;

import com.demod.factorio.DataTable;
import com.demod.factorio.FactorioData;
import com.demod.factorio.prototype.RecipePrototype;
import com.google.common.collect.Streams;

import javafx.util.Pair;

public class FactorioFactoryGroupingMain {

	public static class FactoryGroup {
		Map<RecipePrototype, Integer> factories = new LinkedHashMap<>();
		public int factoryCount = 0;
		public Map<String, Double> throughputs = new LinkedHashMap<>();
		public Map<String, RecipePrototype> consumers = new LinkedHashMap<>();
		public Map<String, RecipePrototype> producers = new LinkedHashMap<>();
		Set<String> externalInputs = new LinkedHashSet<>();
		Set<String> externalOutputs = new LinkedHashSet<>();
		public boolean solved;

		public void addFactory(RecipePrototype factory, int count) {
			Integer total = factories.get(factory);
			total = total == null ? count : total + count;
			factories.put(factory, total);
			factoryCount += count;

			for (String item : factory.getInputs().keySet()) {
				consumers.put(item, factory);
			}
			for (String item : factory.getOutputs().keySet()) {
				producers.put(item, factory);
			}

			externalInputs.clear();
			externalOutputs.clear();
			for (RecipePrototype recipe : factories.keySet()) {
				for (String item : recipe.getInputs().keySet()) {
					if (!producers.containsKey(item)) {
						externalInputs.add(item);
					}
				}
				for (String item : recipe.getOutputs().keySet()) {
					if (!consumers.containsKey(item)) {
						externalOutputs.add(item);
					}
				}
			}

			for (Entry<String, Integer> entry : factory.getInputs().entrySet()) {
				addThroughput(entry.getKey(), -(entry.getValue() * count) / factory.getEnergyRequired());
			}

			for (Entry<String, Integer> entry : factory.getOutputs().entrySet()) {
				addThroughput(entry.getKey(), (entry.getValue() * count) / factory.getEnergyRequired());
			}
		}

		public void addThroughput(String item, double delta) {
			Double total = throughputs.get(item);
			total = total == null ? delta : total + delta;
			if (Math.abs(total) < 0.001) {
				throughputs.remove(item);
			} else {
				throughputs.put(item, total);
			}
			solved = throughputs.size() == externalInputs.size() + externalOutputs.size();
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			if (!solved) {
				builder.append("########## NOT SOLVED ##########\n");
			}
			builder.append("Target Output: " + externalOutputs.toString() + "\n");
			builder.append("Factories:\n");
			for (Entry<RecipePrototype, Integer> entry : factories.entrySet()) {
				builder.append("\t" + entry.getValue() + " " + entry.getKey().getName() + "\n");
			}
			builder.append("Throughputs: \n");
			throughputs.entrySet().stream().sorted((e1, e2) -> Double.compare(e1.getValue(), e2.getValue()))
					.forEach(e -> {
						builder.append(String.format("\t%+.2f %s\n", e.getValue(), e.getKey()));
					});
			return builder.toString();
		}
	}

	public static void main(String[] args) throws JSONException, IOException {
		DataTable table = FactorioData.getTable();

		personal_factoryGroupSolving(table);
	}

	private static void personal_factoryGroupSolving(DataTable table) {
		System.out
				.println(solve(table, table.getExpensiveRecipes(), new String[] {}, new String[] { "science-pack-1" }));
		System.out
				.println(solve(table, table.getExpensiveRecipes(), new String[] {}, new String[] { "science-pack-2" }));
		System.out.println(solve(table, table.getExpensiveRecipes(), new String[] { "plastic-bar" },
				new String[] { "science-pack-3" }));
		System.out.println(
				solve(table, table.getExpensiveRecipes(), new String[] {}, new String[] { "military-science-pack" }));
		System.out.println(solve(table, table.getExpensiveRecipes(), new String[] { "plastic-bar" },
				new String[] { "production-science-pack" }));
		System.out.println("\n================================================================\n");
		System.out.println(
				solve(table, table.getExpensiveRecipes(), new String[] {}, new String[] { "electronic-circuit" }));
		System.out.println(solve(table, table.getExpensiveRecipes(), new String[] { "plastic-bar" },
				new String[] { "advanced-circuit" }));
	}

	public static FactoryGroup solve(DataTable table, Map<String, RecipePrototype> recipes, String[] providedInputs,
			String[] targetOutputs) {
		Set<String> externalInputs = new LinkedHashSet<>(table.getWorldInputs());
		for (String item : providedInputs) {
			externalInputs.add(item);
		}

		FactoryGroup factoryGroup = new FactoryGroup();

		// Minimum Factories Needed
		Deque<String> dependencies = new ArrayDeque<>();
		Collections.addAll(dependencies, targetOutputs);
		while (!dependencies.isEmpty()) {
			String name = dependencies.pop();
			if (!externalInputs.contains(name)) {
				for (RecipePrototype recipe : recipes.values()) {
					if (recipe.getOutputs().containsKey(name)) {
						factoryGroup.addFactory(recipe, 1);
						for (String inputName : recipe.getInputs().keySet()) {
							if (!externalInputs.contains(inputName)) {
								dependencies.add(inputName);
							}
						}
						break;
					}
				}
			}
		}

		while (!factoryGroup.solved && factoryGroup.factoryCount < 1000) {
			Optional<Pair<RecipePrototype, Integer>> recipe = factoryGroup.throughputs.entrySet().stream()
					.sorted((e1, e2) -> Double.compare(Math.abs(e2.getValue()), Math.abs(e1.getValue()))).flatMap(e -> {
						if (e.getValue() < -0.001) {
							RecipePrototype r = factoryGroup.producers.get(e.getKey());
							if (r != null) {
								int count = (int) Math.ceil(-0.001
										+ -e.getValue() / (r.getOutputs().get(e.getKey()) / r.getEnergyRequired()));
								// System.out.println("Need to get " +
								// e.getKey() + " (" + e.getValue() + ") via " +
								// count
								// + " " + r.getName() + " factories");
								return Streams.stream(Optional.of(new Pair<>(r, count)));
							}
						} else if (e.getValue() > 0.001) {
							RecipePrototype r = factoryGroup.consumers.get(e.getKey());
							if (r != null) {
								int count = (int) Math.ceil(-0.001
										+ e.getValue() / (r.getInputs().get(e.getKey()) / r.getEnergyRequired()));
								// System.out.println("Need to use " +
								// e.getKey() + " (" + e.getValue() + ") via " +
								// count
								// + " " + r.getName() + " factories");
								return Streams.stream(Optional.of(new Pair<>(r, count)));
							}
						}
						return Streams.stream(Optional.empty());
					}).findFirst();

			Pair<RecipePrototype, Integer> pair = recipe.get();
			factoryGroup.addFactory(pair.getKey(), pair.getValue());
		}

		return factoryGroup;
	}
}
