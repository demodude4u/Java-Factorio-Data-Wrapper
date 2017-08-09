package com.demod.factorio.apps;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.json.JSONException;
import org.json.JSONObject;

import com.demod.factorio.DataTable;
import com.demod.factorio.FactorioData;
import com.demod.factorio.ModInfo;
import com.demod.factorio.TotalRawCalculator;
import com.demod.factorio.Utils;
import com.demod.factorio.prototype.RecipePrototype;
import com.demod.factorio.prototype.TechPrototype;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.primitives.Ints;

public class FactorioWikiMain {

	private static class WikiTypeMatch {
		boolean item = false, recipe = false, entity = false, equipment = false, tile = false, fluid = false;
		String entityType;
		String equipmentType;
		String itemType;

		public void setEntity(String type) {
			entity = true;
			entityType = type;
		}

		public void setEquipment(String type) {
			equipment = true;
			equipmentType = type;
		}

		public void setItem(String type) {
			item = true;
			itemType = type;
		}

		@Override
		public String toString() {
			if (!item && !recipe) {
				return "N/A";
			} else if (equipment) {
				return equipmentType;
			} else if (tile) {
				return "tile";
			} else if (fluid) {
				return "fluid";
			} else if (entity) {
				return entityType;
			} else if (item) {
				return itemType;
			} else if (recipe) {
				return "recipe";
			}
			return "???";
		}
	}

	public static final Map<String, Integer> wiki_ScienceOrdering = new LinkedHashMap<>();

	static {
		wiki_ScienceOrdering.put("science-pack-1", 1);
		wiki_ScienceOrdering.put("science-pack-2", 2);
		wiki_ScienceOrdering.put("science-pack-3", 3);
		wiki_ScienceOrdering.put("military-science-pack", 4);
		wiki_ScienceOrdering.put("production-science-pack", 5);
		wiki_ScienceOrdering.put("high-tech-science-pack", 6);
		wiki_ScienceOrdering.put("space-science-pack", 7);
	}

	private static Map<String, Function<Double, String>> wiki_EffectModifierFormatter = new LinkedHashMap<>();

	static {
		Function<Double, String> fmtCount = v -> wiki_fmtDouble(v);
		Function<Double, String> fmtPercent = v -> String.format("%.0f%%", v * 100);
		Function<Double, String> fmtSlot = v -> "+" + wiki_fmtDouble(v) + " slots";

		wiki_EffectModifierFormatter.put("ammo-damage", fmtPercent);
		wiki_EffectModifierFormatter.put("character-logistic-slots", fmtSlot);
		wiki_EffectModifierFormatter.put("character-logistic-trash-slots", fmtSlot);
		wiki_EffectModifierFormatter.put("gun-speed", fmtPercent);
		wiki_EffectModifierFormatter.put("laboratory-speed", fmtPercent);
		wiki_EffectModifierFormatter.put("maximum-following-robots-count", fmtCount);
		wiki_EffectModifierFormatter.put("mining-drill-productivity-bonus", fmtPercent);
		wiki_EffectModifierFormatter.put("train-braking-force-bonus", fmtPercent);
		wiki_EffectModifierFormatter.put("turret-attack", fmtPercent);
		wiki_EffectModifierFormatter.put("worker-robot-speed", fmtPercent);
		wiki_EffectModifierFormatter.put("worker-robot-storage", fmtCount);
	}

	public static void main(String[] args) throws JSONException, IOException {
		DataTable table = FactorioData.getTable();
		ModInfo baseInfo = new ModInfo(
				Utils.readJsonFromStream(new FileInputStream(new File(FactorioData.factorio, "data/base/info.json"))));

		File outputFolder = new File("output/" + baseInfo.getVersion());
		outputFolder.mkdirs();

		try (PrintWriter pw = new PrintWriter(
				new File(outputFolder, "wiki-technologies-" + baseInfo.getVersion() + ".txt"))) {
			wiki_Technologies(table, pw);
		}

		try (PrintWriter pw = new PrintWriter(
				new File(outputFolder, "wiki-formula-technologies-" + baseInfo.getVersion() + ".txt"))) {
			wiki_FormulaTechnologies(table, pw);
		}

		try (PrintWriter pw = new PrintWriter(
				new File(outputFolder, "wiki-recipes-totals-" + baseInfo.getVersion() + ".txt"))) {
			wiki_RawTotals(table, pw);
		}

		Map<String, WikiTypeMatch> wikiTypes;
		try (PrintWriter pw = new PrintWriter(new File(outputFolder, "wiki-types-" + baseInfo.getVersion() + ".txt"))) {
			wikiTypes = wiki_Types(table, pw);
		}

		try (PrintWriter pw = new PrintWriter(new File(outputFolder, "wiki-items-" + baseInfo.getVersion() + ".txt"))) {
			wiki_Items(table, pw);
		}

		try (PrintWriter pw = new PrintWriter(
				new File(outputFolder, "wiki-type-tree-" + baseInfo.getVersion() + ".txt"))) {
			wiki_TypeTree(table, pw);
		}

		try (PrintWriter pw = new PrintWriter(
				new File(outputFolder, "wiki-tech-names-" + baseInfo.getVersion() + ".txt"))) {
			wiki_TechNames(table, pw);
		}

		try (PrintWriter pw = new PrintWriter(
				new File(outputFolder, "wiki-entities-health-" + baseInfo.getVersion() + ".txt"))) {
			wiki_EntitiesHealth(table, wikiTypes, pw);
		}

		// wiki_GenerateTintedIcons(table, new File(outputFolder, "icons"));

		Desktop.getDesktop().open(outputFolder);
	}

	private static void wiki_EntitiesHealth(DataTable table, Map<String, WikiTypeMatch> wikiTypes, PrintWriter pw) {
		table.getEntities().values().stream().sorted((e1, e2) -> e1.getName().compareTo(e2.getName()))
				.filter(e -> !wikiTypes.get(e.getName()).toString().equals("N/A")).forEach(e -> {
					double health = e.lua().get("max_health").todouble();
					if (health > 0) {
						pw.println(table.getWikiEntityName(e.getName()));

						pw.println("|health = " + wiki_fmtDouble(health));

						pw.println();
					}
				});
	}

	public static String wiki_fmtDouble(double value) {
		if (value == (long) value) {
			return String.format("%d", (long) value);
		} else {
			return Double.toString(value);// String.format("%f", value);
		}
	}

	/**
	 * Same as {@link #wiki_fmtName(String, JSONObject)}, but adds a ",
	 * [number]" when there is a number as the last part of the name. This adds
	 * the number to the icon.
	 */
	public static String wiki_fmtNumberedWikiName(String wikiName) {
		String[] split = wikiName.split("\\s+");
		Integer num = Ints.tryParse(split[split.length - 1]);
		if (num != null) {
			wikiName += ", " + num;
		}
		return wikiName;
	}

	private static void wiki_FormulaTechnologies(DataTable table, PrintWriter pw) {
		table.getTechnologies().values().stream().filter(t -> t.isBonus()).map(t -> t.getBonusName()).distinct()
				.sorted().forEach(bonusName -> {
					String wikiBonusName = table.getWikiTechnologyName(bonusName);
					pw.println(wikiBonusName);
					TechPrototype firstTech = table.getTechnology(bonusName + "-1").get();
					int maxBonus = firstTech.getBonusGroup().stream().mapToInt(TechPrototype::getBonusLevel).max()
							.getAsInt();

					double time = 0;
					Optional<IntUnaryOperator> countFormula = Optional.empty();
					LinkedHashMap<String, Integer> ingredients = null;
					List<TechPrototype.Effect> effects = null;
					Map<String, Double> effectTypeSum = new LinkedHashMap<>();

					for (int i = 1; i <= maxBonus; i++) {
						Optional<TechPrototype> optTech = table.getTechnology(bonusName + "-" + i);
						int count;
						boolean showFormula = false;
						String formula = null;
						if (optTech.isPresent()) {
							TechPrototype tech = optTech.get();

							time = tech.getTime();
							ingredients = tech.getIngredients();
							effects = tech.getEffects();

							if (tech.getBonusCountFormula().isPresent()) {
								countFormula = tech.getBonusCountFormula();
							}
							count = tech.getEffectiveCount();

							if (tech.isMaxLevelInfinite()) {
								showFormula = true;
								formula = tech.getBonusCountFormulaVisual().get().replace("L", "Level");
							}
						} else {
							count = countFormula.get().applyAsInt(i);
						}

						effects.forEach(e -> {
							double sum = effectTypeSum.getOrDefault(e.getKey(), 0.0);
							sum += e.getModifier();
							effectTypeSum.put(e.getKey(), sum);
						});

						pw.println("| {{Icontech|" + wikiBonusName + " (research)|" + i + "}} " + wikiBonusName + " "
								+ i + " || {{Icon|Time|" + wiki_fmtDouble(time) + "}} "
								+ ingredients.entrySet().stream()
										.sorted((e1, e2) -> Integer.compare(wiki_ScienceOrdering.get(e1.getKey()),
												wiki_ScienceOrdering.get(e2.getKey())))
										.map(e -> "{{Icon|" + table.getWikiItemName(e.getKey()) + "|" + e.getValue()
												+ "}}")
										.collect(Collectors.joining(" "))
								+ " <big>X " + count + "</big>" + (showFormula ? (" " + formula) : "") + " || "
								+ effects.stream()
										.map(e -> wiki_EffectModifierFormatter.getOrDefault(e.getType(), v -> "")
												.apply(e.getModifier()))
										.filter(s -> !s.isEmpty()).distinct().collect(
												Collectors
														.joining(
																" "))
								+ " || "
								+ effectTypeSum.entrySet().stream()
										.map(e -> wiki_EffectModifierFormatter
												.getOrDefault(e.getKey().split("\\|")[0], v -> "").apply(e.getValue()))
										.filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(" ")));
						pw.println("|-");
					}
					pw.println();
				});
	}

	@SuppressWarnings("unused")
	private static void wiki_GenerateTintedIcons(DataTable table, File folder) {
		folder.mkdirs();

		table.getRecipes().values().stream().forEach(recipe -> {
			if (!recipe.lua().get("icons").isnil()) {
				System.out.println();
				System.out.println(recipe.getName());
				Utils.debugPrintLua(recipe.lua().get("icons"));
				try {
					ImageIO.write(FactorioData.getIcon(recipe), "PNG", new File(folder, recipe.getName() + ".png"));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		table.getItems().values().stream().forEach(item -> {
			if (!item.lua().get("icons").isnil()) {
				System.out.println();
				System.out.println(item.getName());
				Utils.debugPrintLua(item.lua().get("icons"));
				try {
					ImageIO.write(FactorioData.getIcon(item), "PNG", new File(folder, item.getName() + ".png"));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}

	private static void wiki_Items(DataTable table, PrintWriter pw) {
		Multimap<String, String> requiredTechnologies = LinkedHashMultimap.create();
		table.getTechnologies().values()
				.forEach(tech -> tech.getRecipeUnlocks().stream().map(table.getRecipes()::get)
						.flatMap(r -> r.getOutputs().keySet().stream())
						.forEach(name -> requiredTechnologies.put(name, tech.getName())));

		table.getItems().values().stream().sorted((i1, i2) -> i1.getName().compareTo(i2.getName())).forEach(item -> {
			pw.println(table.getWikiItemName(item.getName()));

			List<String> names = table.getRecipes().values().stream()
					.filter(r -> r.getInputs().containsKey(item.getName())).map(RecipePrototype::getName).sorted()
					.collect(Collectors.toList());
			if (!names.isEmpty()) {
				pw.println("|consumers = "
						+ names.stream().map(n -> table.getWikiRecipeName(n)).collect(Collectors.joining(" + ")));
			}

			pw.println("|stack-size = " + item.lua().get("stack_size").toint());

			Collection<String> reqTech = requiredTechnologies.get(item.getName());
			if (!reqTech.isEmpty()) {
				pw.println("|required-technologies = " + reqTech.stream().sorted()
						.map(n -> table.getWikiTechnologyName(n)).collect(Collectors.joining(" + ")));
			}

			pw.println();
		});
	}

	/**
	 * 
	 * List all hand-craftable recipes.
	 * 
	 * <pre>
	 * |recipe = Time, [ENERGY] + [Ingredient Name], [Ingredient Count] + ...  = [Ingredient Name], [Ingredient Count] + ...
	 * </pre>
	 * 
	 * If there is only one output ingredient with just 1 count, do not include
	 * the = part
	 * 
	 * <pre>
	 * |total-raw = Time, [ENERGY] + [Ingredient Name], [Ingredient Count] + ...
	 * </pre>
	 * 
	 * @param table
	 * @param mappingJson
	 */
	private static void wiki_RawTotals(DataTable table, PrintWriter pw) throws FileNotFoundException {

		Map<String, RecipePrototype> normalRecipes = table.getRecipes();
		Map<String, RecipePrototype> expensiveRecipes = table.getExpensiveRecipes();

		TotalRawCalculator normalTotalRawCalculator = new TotalRawCalculator(normalRecipes);
		TotalRawCalculator expensiveTotalRawCalculator = new TotalRawCalculator(expensiveRecipes);

		Sets.union(normalRecipes.keySet(), expensiveRecipes.keySet()).stream().sorted().forEach(name -> {
			pw.println(table.getWikiRecipeName(name));

			{
				RecipePrototype recipe = normalRecipes.get(name);
				if (recipe != null) {
					pw.print("|recipe = ");
					pw.printf("Time, %s", wiki_fmtDouble(recipe.getEnergyRequired()));
					recipe.getInputs().entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
							.forEach(entry -> {
								pw.printf(" + %s, %d", table.getWikiItemName(entry.getKey()), entry.getValue());
							});
					if (recipe.getOutputs().size() > 1
							|| recipe.getOutputs().values().stream().findFirst().get() != 1) {
						pw.print(" = ");
						pw.print(recipe.getOutputs().entrySet().stream()
								.sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey())).map(entry -> String
										.format("%s, %d", table.getWikiItemName(entry.getKey()), entry.getValue()))
								.collect(Collectors.joining(" + ")));
					}
					pw.println();

					Map<String, Double> totalRaw = normalTotalRawCalculator.compute(recipe);

					pw.print("|total-raw = ");
					pw.printf("Time, %s", wiki_fmtDouble(totalRaw.get(TotalRawCalculator.RAW_TIME)));
					totalRaw.entrySet().stream().filter(e -> !e.getKey().equals(TotalRawCalculator.RAW_TIME))
							.sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey())).forEach(entry -> {
								pw.printf(" + %s, %s", table.getWikiItemName(entry.getKey()),
										wiki_fmtDouble(entry.getValue()));
							});
					pw.println();
				}
			}
			{
				RecipePrototype recipe = expensiveRecipes.get(name);
				if (recipe != null) {
					pw.print("|expensive-recipe = ");
					pw.printf("Time, %s", wiki_fmtDouble(recipe.getEnergyRequired()));
					recipe.getInputs().entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
							.forEach(entry -> {
								pw.printf(" + %s, %d", table.getWikiItemName(entry.getKey()), entry.getValue());
							});
					if (recipe.getOutputs().size() > 1
							|| recipe.getOutputs().values().stream().findFirst().get() != 1) {
						pw.print(" = ");
						pw.print(recipe.getOutputs().entrySet().stream()
								.sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey())).map(entry -> String
										.format("%s, %d", table.getWikiItemName(entry.getKey()), entry.getValue()))
								.collect(Collectors.joining(" + ")));
					}
					pw.println();

					Map<String, Double> totalRaw = expensiveTotalRawCalculator.compute(recipe);

					pw.print("|expensive-total-raw = ");
					pw.printf("Time, %s", wiki_fmtDouble(totalRaw.get(TotalRawCalculator.RAW_TIME)));
					totalRaw.entrySet().stream().filter(e -> !e.getKey().equals(TotalRawCalculator.RAW_TIME))
							.sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey())).forEach(entry -> {
								pw.printf(" + %s, %s", table.getWikiItemName(entry.getKey()),
										wiki_fmtDouble(entry.getValue()));
							});
					pw.println();
				}
			}

			pw.println();
		});
	}

	private static void wiki_TechNames(DataTable table, PrintWriter pw) {
		table.getTechnologies().values().stream().sorted((t1, t2) -> t1.getName().compareTo(t2.getName()))
				.filter(t -> !t.isBonus() || t.isFirstBonus()).forEach(tech -> {
					pw.println(table.getWikiTechnologyName(tech.isBonus() ? tech.getBonusName() : tech.getName()));
					pw.println("|internal-name = " + tech.getName());
					pw.println();
				});
	}

	/**
	 * | cost = Time,30 + Science pack 1,1 + Science pack 2,1 + Science pack
	 * 3,1<br>
	 * |cost-multiplier = 1000 <br>
	 * |expensive-cost-multiplier = 4000<br>
	 * |required-technologies = Advanced electronics + Concrete <br>
	 * |allows = Atomic bomb + Uranium ammo + Kovarex enrichment process +
	 * Nuclear fuel reprocessing <br>
	 * |effects = Nuclear reactor + Centrifuge + Uranium processing + Uranium
	 * fuel cell + Heat exchanger + Heat pipe + Steam turbine <br>
	 * <br>
	 * allows are the techs it unlocks, effects are the items it unlocks. <br>
	 * bonuses are handled weirdly, we do one infobox per kind of bonus that
	 * gives the required technologies for the first tier of the bonus, no
	 * effect and the other bonus research as the allows, like this: <br>
	 * | cost = time, 60 + science pack 1,1 + science pack 2,1 + science pack
	 * 3,1 + military science pack,1 <br>
	 * | cost-multiplier = 100 <br>
	 * | required-technologies = tanks <br>
	 * | allows = Cannon shell damage (research), 2-5<br>
	 * <br>
	 * - Bilka
	 */
	private static void wiki_Technologies(DataTable table, PrintWriter pw) {
		Multimap<String, String> allowsMap = LinkedHashMultimap.create();
		table.getTechnologies().values().forEach(tech -> tech.getPrerequisites()
				.forEach(n -> allowsMap.put(n, tech.isBonus() ? tech.getBonusName() : tech.getName())));

		table.getTechnologies().values().stream().sorted((t1, t2) -> t1.getName().compareTo(t2.getName()))
				.filter(t -> !t.isBonus() || t.isFirstBonus()).forEach(tech -> {
					pw.println(table.getWikiTechnologyName(tech.isBonus() ? tech.getBonusName() : tech.getName()));

					pw.print("|cost = ");
					pw.printf("Time, %s", wiki_fmtDouble(tech.getTime()));
					tech.getIngredients().entrySet().stream().sorted((e1, e2) -> Integer
							.compare(wiki_ScienceOrdering.get(e1.getKey()), wiki_ScienceOrdering.get(e2.getKey())))
							.forEach(entry -> {
								pw.printf(" + %s, %d", table.getWikiItemName(entry.getKey()), entry.getValue());
							});
					pw.println();

					int count = tech.getEffectiveCount();
					pw.println("|cost-multiplier = " + count);
					pw.println("|expensive-cost-multiplier = " + (count * 4));

					if (!tech.getPrerequisites().isEmpty()) {
						pw.println("|required-technologies = " + tech.getPrerequisites().stream().sorted()
								.map(n -> wiki_fmtNumberedWikiName(table.getWikiTechnologyName(n)))
								.collect(Collectors.joining(" + ")));
					}

					if (!tech.isFirstBonus()) {
						Collection<String> allows = allowsMap.get(tech.getName());
						if (!allows.isEmpty()) {
							pw.println("|allows = " + allows.stream().sorted()
									.map(n -> wiki_fmtNumberedWikiName(table.getWikiTechnologyName(n)))
									.collect(Collectors.joining(" + ")));
						}
					} else {
						pw.println("|allows = " + table.getWikiTechnologyName(tech.getBonusName()) + ", 2-"
								+ tech.getBonusGroup().size());
					}

					if (!tech.getRecipeUnlocks().isEmpty()) {
						pw.println("|effects = " + tech.getRecipeUnlocks().stream().sorted()
								.map(n -> table.getWikiRecipeName(n)).collect(Collectors.joining(" + ")));
					}

					pw.println();
				});
	}

	private static Map<String, WikiTypeMatch> wiki_Types(DataTable table, PrintWriter pw) {

		Map<String, WikiTypeMatch> protoMatches = new LinkedHashMap<>();
		table.getItems().entrySet().forEach(e -> protoMatches.computeIfAbsent(e.getKey(), k -> new WikiTypeMatch())
				.setItem(e.getValue().getType()));
		table.getRecipes().keySet()
				.forEach(name -> protoMatches.computeIfAbsent(name, k -> new WikiTypeMatch()).recipe = true);
		table.getTiles().keySet()
				.forEach(name -> protoMatches.computeIfAbsent(name, k -> new WikiTypeMatch()).tile = true);
		table.getFluids().keySet()
				.forEach(name -> protoMatches.computeIfAbsent(name, k -> new WikiTypeMatch()).fluid = true);
		table.getEntities().entrySet().forEach(e -> protoMatches.computeIfAbsent(e.getKey(), k -> new WikiTypeMatch())
				.setEntity(e.getValue().getType()));
		table.getEquipments().entrySet().forEach(e -> protoMatches.computeIfAbsent(e.getKey(), k -> new WikiTypeMatch())
				.setEquipment(e.getValue().getType()));

		protoMatches.entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey())).forEach(e -> {
			String name = e.getKey();
			WikiTypeMatch m = e.getValue();
			String type = m.toString();

			if (!m.item && !m.recipe) {
				return;
			}

			pw.println(m.item ? table.getWikiItemName(name) : table.getWikiRecipeName(name));
			pw.println("|internal-name = " + name);
			pw.println("|prototype-type = " + type);
			pw.println();
		});

		return protoMatches;
	}

	private static void wiki_TypeTree(DataTable table, PrintWriter pw) {
		Multimap<String, String> links = LinkedHashMultimap.create();
		Multimap<String, String> leafs = LinkedHashMultimap.create();

		table.getTypeHiearchy().getParents().forEach((n, p) -> {
			links.put(p, n);
		});
		table.getTypeHiearchy().getRoots().forEach(n -> {
			links.put("__ROOT__", n);
		});

		Utils.forEach(table.getRawLua(), v -> {
			Utils.forEach(v.checktable(), protoLua -> {
				String type = protoLua.get("type").tojstring();
				String name = protoLua.get("name").tojstring();
				leafs.put(type, name);
				if (!table.getTypeHiearchy().getParents().containsKey(type)
						&& !table.getTypeHiearchy().getRoots().contains(type)) {
					System.err.println("MISSING PARENT FOR TYPE: " + type + " (" + name + ")");
				}
			});
		});

		Collection<String> rootTypes = links.get("__ROOT__");
		rootTypes.stream().sorted().forEach(n -> {
			pw.println("== " + n + " ==");
			pw.println(
					"<div class=\"factorio-list\" style=\"column-count:2;-moz-column-count:2;-webkit-column-count:2\">");
			wiki_TypeTree_RecursivePrint(links, leafs, pw, "*", n);
			pw.println("</div>");
		});
	}

	private static void wiki_TypeTree_RecursivePrint(Multimap<String, String> links, Multimap<String, String> leafs,
			PrintWriter pw, String stars, String parent) {
		Collection<String> types = links.get(parent);
		Collection<String> names = leafs.get(parent);
		Streams.concat(types.stream(), names.stream()).sorted().forEach(n -> {
			pw.println(stars + " " + n);
			if (types.contains(n)) {
				wiki_TypeTree_RecursivePrint(links, leafs, pw, stars + "*", n);
			}
		});
	}
}
