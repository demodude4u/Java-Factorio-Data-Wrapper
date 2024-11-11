package com.demod.factorio.apps;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.LuaValue;

import com.demod.factorio.Config;
import com.demod.factorio.DataTable;
import com.demod.factorio.FactorioData;
import com.demod.factorio.ModInfo;
import com.demod.factorio.TotalRawCalculator;
import com.demod.factorio.Utils;
import com.demod.factorio.prototype.EntityPrototype;
import com.demod.factorio.prototype.RecipePrototype;
import com.demod.factorio.prototype.TechPrototype;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
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
			if (!item && !recipe && !fluid) {
				return "N/A";
			} else if (equipment) {
				return equipmentType;
			} else if (fluid) {
				return "fluid";
			} else if (tile) {
				return "tile";
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
		wiki_ScienceOrdering.put("automation-science-pack", 1);
		wiki_ScienceOrdering.put("logistic-science-pack", 2);
		wiki_ScienceOrdering.put("military-science-pack", 3);
		wiki_ScienceOrdering.put("chemical-science-pack", 4);
		wiki_ScienceOrdering.put("production-science-pack", 5);
		wiki_ScienceOrdering.put("utility-science-pack", 6);
		wiki_ScienceOrdering.put("space-science-pack", 7);
		wiki_ScienceOrdering.put("metallurgic-science-pack", 8);
		wiki_ScienceOrdering.put("electromagnetic-science-pack", 9);
		wiki_ScienceOrdering.put("agricultural-science-pack", 10);
		wiki_ScienceOrdering.put("cryogenic-science-pack", 11);
		wiki_ScienceOrdering.put("promethium-science-pack", 12);
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

	private static ModInfo baseInfo;
	private static File folder;

	private static JSONObject createOrderedJSONObject() {
		JSONObject json = new JSONObject();
		Utils.terribleHackToHaveOrderedJSONObject(json);
		return json;
	}

	private static Map<String, WikiTypeMatch> generateWikiTypes(DataTable table) {
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

		return protoMatches;
	}

	public static void main(String[] args) throws JSONException, IOException {
		DataTable table = FactorioData.getTable();
		baseInfo = new ModInfo(
				Utils.readJsonFromStream(new FileInputStream(new File(FactorioData.factorio, "data/base/info.json"))));

		String outputPath = Config.get().optString("output", "output");

		folder = new File(outputPath + File.separator + baseInfo.getVersion());
		folder.mkdirs();

		Map<String, WikiTypeMatch> wikiTypes = generateWikiTypes(table);

		write(wiki_Technologies(table), "wiki-technologies");
		// write(wiki_FormulaTechnologies(table), "wiki-formula-technologies");
		write(wiki_Recipes(table), "wiki-recipes");
		write(wiki_Types(table, wikiTypes), "wiki-types");
		write(wiki_Items(table), "wiki-items");
		// write(wiki_TypeTree(table), "wiki-type-tree");
		write(wiki_Entities(table, wikiTypes), "wiki-entities");
		write(wiki_DataRawTree(table), "data-raw-tree");

		// Output icons outside of versions because these are a lot of icons and they
		// dont change so often. No need to spam the repository with them.
		File icons = new File(outputPath, "icons");
		wiki_GenerateIcons(table, icons, new File(icons, "technology"));

		Desktop.getDesktop().open(folder);
	}

	private static JSONArray pair(Object a, Object b) {
		JSONArray json = new JSONArray();
		json.put(a);
		json.put(b);
		return json;
	}

	private static <T> Collector<T, ?, JSONArray> toJsonArray() {
		return Collectors.collectingAndThen(Collectors.toList(), JSONArray::new);
	}

	private static JSONObject wiki_DataRawTree(DataTable table) {
		JSONObject json = createOrderedJSONObject();

		Multimap<String, String> leafs = LinkedHashMultimap.create();

		Utils.forEach(table.getRawLua(), v -> {
			Utils.forEach(v.checktable(), protoLua -> {
				String type = protoLua.get("type").tojstring();
				String name = protoLua.get("name").tojstring();
				leafs.put(type, name);
			});
		});

		leafs.keySet().stream().sorted().forEach(type -> {
			json.put(type, leafs.get(type).stream().sorted().collect(toJsonArray()));
		});

		return json;
	}

	private static JSONObject wiki_Entities(DataTable table, Map<String, WikiTypeMatch> wikiTypes) {
		JSONObject json = createOrderedJSONObject();

		Optional<LuaValue> optUtilityConstantsLua = table.getRaw("utility-constants", "default");
		LuaValue utilityConstantsLua = optUtilityConstantsLua.get();

		Color defaultFriendlyColor = Utils.parseColor(utilityConstantsLua.get("chart").get("default_friendly_color"));
		Map<String, Color> defaultFriendlyColorByType = new HashMap<>();
		Utils.forEach(utilityConstantsLua.get("chart").get("default_friendly_color_by_type"), (k, v) -> {
			defaultFriendlyColorByType.put(k.tojstring(), Utils.parseColor(v));
		});

		table.getEntities().values().stream().sorted((e1, e2) -> e1.getName().compareTo(e2.getName()))
				.filter(e -> (!wikiTypes.get(e.getName()).toString().equals("N/A")
						|| table.getExplicitelyIncludedEntities().contains(e.getName())))
				.forEach(e -> {
					Color mapColor = null;
					LuaValue friendlyMapColorLua = e.lua().get("friendly_map_color");
					if (!friendlyMapColorLua.isnil()) {
						mapColor = Utils.parseColor(friendlyMapColorLua);
					} else {
						LuaValue mapColorLua = e.lua().get("map_color");
						if (!mapColorLua.isnil()) {
							mapColor = Utils.parseColor(mapColorLua);
						} else {
							mapColor = defaultFriendlyColorByType.get(e.lua().get("type").tojstring());
							if (mapColor == null && !e.getFlags().contains("not-on-map")) {
								mapColor = defaultFriendlyColor;
							}
						}
					}

					if (e.getType().equals("car") || e.getType().equals("locomotive") || e.getType().contains("wagon")
							|| e.getType().equals("train-stop") || e.getType().equals("spider-vehicle")) {
						mapColor = null; // these entity types are not drawn on map normally
					}

					double health = e.lua().get("max_health").todouble();
					LuaValue minableLua = e.lua().get("minable");
					LuaValue resistances = e.lua().get("resistances");
					LuaValue energySource = e.lua().get("energy_source");
					double emissions = 0.0;

					if (!energySource.isnil()) {
						LuaValue prototypeEmissions = energySource.get("emissions_per_minute");
						if (!prototypeEmissions.isnil())
							emissions = prototypeEmissions.get("pollution").todouble();
					}

					if (mapColor != null || health > 0 || !minableLua.isnil() || emissions > 0
							|| !resistances.isnil()) {
						JSONObject itemJson = createOrderedJSONObject();
						json.put(table.getWikiEntityName(e.getName()), itemJson);

						if (mapColor != null)
							itemJson.put("map-color", String.format("%02x%02x%02x", mapColor.getRed(),
									mapColor.getGreen(), mapColor.getBlue()));
						if (health > 0)
							itemJson.put("health", health);
						if (!minableLua.isnil())
							itemJson.put("mining-time", minableLua.get("mining_time").todouble());
						if (emissions != 0)
							itemJson.put("pollution", Math.round(emissions * 100) / 100.0);
						if (!resistances.isnil()) {
							JSONObject resistancesJson = createOrderedJSONObject();
							itemJson.put("resistances", resistancesJson);

							Utils.forEach(resistances, resist -> {
								JSONObject resistJson = createOrderedJSONObject();
								resistancesJson.put(resist.get("type").toString(), resistJson);
								LuaValue percent = resist.get("percent");
								LuaValue decrease = resist.get("decrease");
								resistJson.put("percent", !percent.isnil() ? percent.toint() : 0);
								resistJson.put("decrease", !decrease.isnil() ? decrease.toint() : 0);
							});
						}
					}
				});

		// not entities but lets just.. ignore that
		table.getTiles().values().stream().sorted((t1, t2) -> t1.getName().compareTo(t2.getName()))
				.filter(t -> table.hasWikiEntityName(t.getName())).forEach(t -> {
					Color mapColor = null;
					LuaValue mapColorLua = t.lua().get("map_color");
					if (!mapColorLua.isnil())
						mapColor = Utils.parseColor(mapColorLua);

					if (mapColor != null) {
						JSONObject itemJson = createOrderedJSONObject();
						json.put(table.getWikiEntityName(t.getName()), itemJson);

						itemJson.put("map-color", String.format("%02x%02x%02x", mapColor.getRed(), mapColor.getGreen(),
								mapColor.getBlue()));
					}
				});

		return json;
	}

	public static String wiki_fmtDouble(double value) {
		if (value == (long) value) {
			return String.format("%d", (long) value);
		} else {
			return Double.toString(value);// String.format("%f", value);
		}
	}

	/**
	 * Same as {@link #wiki_fmtName(String, JSONObject)}, but adds a ", [number]"
	 * when there is a number as the last part of the name. This adds the number to
	 * the icon.
	 */
	public static String wiki_fmtNumberedWikiName(String wikiName) {
		String[] split = wikiName.split("\\s+");
		Integer num = Ints.tryParse(split[split.length - 1]);
		if (num != null) {
			wikiName += ", " + num;
		}
		return wikiName;
	}

	@SuppressWarnings("unused")
	private static JSONObject wiki_FormulaTechnologies(DataTable table) {
		JSONObject json = createOrderedJSONObject();
		table.getTechnologies().values().stream().filter(t -> t.isBonus()).map(t -> t.getBonusName()).distinct()
				.sorted().forEach(bonusName -> {
					JSONArray itemJson = new JSONArray();
					json.put(table.getWikiTechnologyName(bonusName), itemJson);

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

						// TODO convert markup to json
						String markup = "| {{Icontech|" + table.getWikiTechnologyName(bonusName) + " (research)|" + i
								+ "}} " + table.getWikiTechnologyName(bonusName) + " " + i + " || {{Icon|Time|"
								+ wiki_fmtDouble(time) + "}} "
								+ ingredients.entrySet().stream()
										.sorted((e1, e2) -> Integer.compare(wiki_ScienceOrdering.get(e1.getKey()),
												wiki_ScienceOrdering.get(e2.getKey())))
										.map(e -> "{{Icon|" + table.getWikiItemName(e.getKey()) + "|" + e.getValue()
												+ "}}")
										.collect(Collectors.joining(" "))
								+ " <big>X " + count + "</big>" + (showFormula ? (" " + formula)
										: "")
								+ " || "
								+ effects.stream()
										.map(e -> wiki_EffectModifierFormatter.getOrDefault(e.getType(), v -> "")
												.apply(e.getModifier()))
										.filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(" "))
								+ " || "
								+ effectTypeSum.entrySet().stream()
										.map(e -> wiki_EffectModifierFormatter
												.getOrDefault(e.getKey().split("\\|")[0], v -> "").apply(e.getValue()))
										.filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(" "));
						itemJson.put(markup);
					}
				});
		return json;
	}

	private static void wiki_GenerateIcons(DataTable table, File folder, File techIconFolder) {
		folder.mkdirs();
		techIconFolder.mkdirs();

		table.getRecipes().values().stream().filter(r -> (!r.isRecycling() && !table.getItems().containsKey(r.getName())
				&& !table.getFluids().containsKey(r.getName()))).forEach(recipe -> {
					try {
						ImageIO.write(FactorioData.getIcon(recipe), "PNG",
								new File(folder, table.getWikiRecipeName(recipe.getName()) + ".png"));
					} catch (IOException e) {
						e.printStackTrace();
					}
				});

		table.getItems().values().stream().forEach(item -> {
			try {
				ImageIO.write(FactorioData.getIcon(item), "PNG",
						new File(folder, table.getWikiItemName(item.getName()) + ".png"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		table.getFluids().values().stream().forEach(fluid -> {
			try {
				ImageIO.write(FactorioData.getIcon(fluid), "PNG",
						new File(folder, table.getWikiFluidName(fluid.getName()) + ".png"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		table.getTechnologies().values().stream().filter(t -> !t.isBonus() || t.isFirstBonus()).forEach(tech -> {
			try {
				ImageIO.write(FactorioData.getIcon(tech), "PNG",
						new File(techIconFolder, table.getWikiTechnologyName(tech.getName()) + ".png"));
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private static JSONObject wiki_Items(DataTable table) {
		JSONObject json = createOrderedJSONObject();

		Multimap<String, String> requiredTechnologies = LinkedHashMultimap.create();
		table.getTechnologies().values()
				.forEach(tech -> tech.getRecipeUnlocks().stream().map(table.getRecipes()::get)
						.flatMap(r -> r.getOutputs().keySet().stream())
						.forEach(name -> requiredTechnologies.put(name, tech.getName())));

		table.getItems().values().stream().sorted((i1, i2) -> i1.getName().compareTo(i2.getName())).forEach(item -> {
			JSONObject itemJson = createOrderedJSONObject();
			json.put(table.getWikiItemName(item.getName()), itemJson);

			List<String> names = table.getRecipes().values().stream()
					.filter(r -> (!r.isRecycling() && r.getInputs().containsKey(item.getName())))
					.map(RecipePrototype::getName).sorted().collect(Collectors.toList());
			if (!names.isEmpty()) {
				itemJson.put("consumers", names.stream().map(n -> table.getWikiRecipeName(n)).collect(toJsonArray()));
			}

			itemJson.put("stack-size", item.lua().get("stack_size").toint());

			Collection<String> reqTech = requiredTechnologies.get(item.getName());
			if (!reqTech.isEmpty()) {
				itemJson.put("required-technologies",
						// XXX filter to make recycling not show as unlock if there is another recipe
						reqTech.stream().sorted().filter(name -> (reqTech.size() == 1 || !name.equals("recycling")))
								.map(n -> table.getWikiTechnologyName(n)).collect(toJsonArray()));
			}

			LuaValue spoilTicksLua = item.lua().get("spoil_ticks");
			if (!spoilTicksLua.isnil())
				itemJson.put("spoil-ticks", spoilTicksLua.toint());
		});

		return json;
	}

	/**
	 *
	 * List all hand-craftable recipes.
	 *
	 * <pre>
	 * |recipe = Time, [ENERGY] + [Ingredient Name], [Ingredient Count] + ...  = [Ingredient Name], [Ingredient Count] + ...
	 * </pre>
	 *
	 * If there is only one output ingredient with just 1 count, do not include the
	 * = part
	 *
	 * <pre>
	 * |total-raw = Time, [ENERGY] + [Ingredient Name], [Ingredient Count] + ...
	 * </pre>
	 *
	 * @param table
	 * @param mappingJson
	 */
	private static JSONObject wiki_Recipes(DataTable table) {
		JSONObject json = createOrderedJSONObject();

		Map<String, RecipePrototype> normalRecipes = table.getRecipes();
		TotalRawCalculator normalTotalRawCalculator = new TotalRawCalculator(normalRecipes);

		normalRecipes.values().stream().filter(r -> !r.isRecycling())
				.sorted((r1, r2) -> r1.getName().compareTo(r2.getName())).forEach(recipe -> {
					JSONObject item = createOrderedJSONObject();
					json.put(table.getWikiItemName(recipe.getName()), item);

					JSONArray recipeJson = new JSONArray();
					item.put("recipe", recipeJson);
					if (!recipe.getInputs().isEmpty() && !recipe.getOutputs().isEmpty())
						recipeJson.put(pair("Time", recipe.getEnergyRequired()));
					recipe.getInputs().entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
							.forEach(entry -> {
								recipeJson.put(pair(table.getWikiItemName(entry.getKey()), entry.getValue()));
							});
					if (recipe.getOutputs().size() > 1 || (recipe.getOutputs().values().stream().findFirst().isPresent()
							&& recipe.getOutputs().values().stream().findFirst().get() != 1)) {
						JSONArray recipeOutputJson = new JSONArray();
						item.put("recipe-output", recipeOutputJson);
						recipe.getOutputs().entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
								.forEach(entry -> {
									recipeOutputJson.put(pair(table.getWikiItemName(entry.getKey()), entry.getValue()));
								});
					}

					Map<String, Double> totalRaw = normalTotalRawCalculator.compute(recipe);

					JSONArray totalRawJson = new JSONArray();
					item.put("total-raw", totalRawJson);
					if (totalRaw.size() > 1)
						totalRawJson.put(pair("Time", totalRaw.get(TotalRawCalculator.RAW_TIME)));
					totalRaw.entrySet().stream().filter(e -> !e.getKey().equals(TotalRawCalculator.RAW_TIME))
							.sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey())).forEach(entry -> {
								totalRawJson.put(pair(table.getWikiItemName(entry.getKey()), entry.getValue()));
							});

					String category = recipe.getCategory();
					Map<String, List<EntityPrototype>> craftingCategories = table.getCraftingCategories();

					if (!craftingCategories.containsKey(category)) {
						System.out.println("recipe " + recipe.getName() + " with recipe category " + category
								+ " has no producers");
					} else {
						item.put("producers",
								craftingCategories.get(category).stream()
										.sorted((e1, e2) -> e1.getName().compareTo(e2.getName()))
										.map(e -> table.getWikiEntityName(e.getName())).collect(toJsonArray()));
					}
				});

		return json;
	}

	/**
	 * | cost = Time,30 + Science pack 1,1 + Science pack 2,1 + Science pack 3,1<br>
	 * |cost-multiplier = 1000 <br>
	 * |expensive-cost-multiplier = 4000<br>
	 * |required-technologies = Advanced electronics + Concrete <br>
	 * |allows = Atomic bomb + Uranium ammo + Kovarex enrichment process + Nuclear
	 * fuel reprocessing <br>
	 * |effects = Nuclear reactor + Centrifuge + Uranium processing + Uranium fuel
	 * cell + Heat exchanger + Heat pipe + Steam turbine <br>
	 * <br>
	 * allows are the techs it unlocks, effects are the items it unlocks. <br>
	 * bonuses are handled weirdly, we do one infobox per kind of bonus that gives
	 * the required technologies for the first tier of the bonus, no effect and the
	 * other bonus research as the allows, like this: <br>
	 * | cost = time, 60 + science pack 1,1 + science pack 2,1 + science pack 3,1 +
	 * military science pack,1 <br>
	 * | cost-multiplier = 100 <br>
	 * | required-technologies = tanks <br>
	 * | allows = Cannon shell damage (research), 2-5<br>
	 * <br>
	 * - Bilka
	 */
	private static JSONObject wiki_Technologies(DataTable table) {
		JSONObject json = createOrderedJSONObject();

		Multimap<String, String> allowsMap = LinkedHashMultimap.create();
		table.getTechnologies().values().forEach(tech -> tech.getPrerequisites()
				.forEach(n -> allowsMap.put(n, tech.isBonus() ? tech.getBonusName() : tech.getName())));

		table.getTechnologies().values().stream().sorted((t1, t2) -> t1.getName().compareTo(t2.getName()))
				.filter(t -> !t.isBonus() || t.isFirstBonus()).forEach(tech -> {
					JSONObject itemJson = createOrderedJSONObject();
					json.put(table.getWikiTechnologyName(tech.isBonus() ? tech.getBonusName() : tech.getName()),
							itemJson);

					itemJson.put("internal-name", tech.getName());

					LuaValue trigger = tech.lua().get("research_trigger");

					if (!tech.getIngredients().isEmpty()) {
						JSONArray costJson = new JSONArray();
						costJson.put(pair("Time", tech.getTime()));
						tech.getIngredients().entrySet().stream().sorted((e1, e2) -> Integer
								.compare(wiki_ScienceOrdering.get(e1.getKey()), wiki_ScienceOrdering.get(e2.getKey())))
								.forEach(entry -> {
									costJson.put(pair(table.getWikiItemName(entry.getKey()), entry.getValue()));
								});
						itemJson.put("cost", costJson);
						int count = tech.getEffectiveCount();
						itemJson.put("cost-multiplier", count);
					} else if (!trigger.isnil()) {
						String trigger_type = trigger.get("type").toString();
						itemJson.put("trigger-type", trigger_type);
						String trigger_object = "";
						double trigger_object_count = 1;
						if (trigger_type.equals("mine-entity")) {
							trigger_object = table.getWikiEntityName(trigger.get("entity").toString());
						} else if (trigger_type.equals("craft-item")) {
							trigger_object = table.getWikiItemName(trigger.get("item").toString());
							trigger_object_count = trigger.get("count").optint(1);
						} else if (trigger_type.equals("craft-fluid")) {
							trigger_object = table.getWikiFluidName(trigger.get("fluid").toString());
							trigger_object_count = trigger.get("amount").optdouble(0);
						} else if (trigger_type.equals("send-item-to-orbit")) {
							// lazy parsing, could be table in modded
							trigger_object = table.getWikiItemName(trigger.get("item").toString());
						} else if (trigger_type.equals("build-entity")) {
							// lazy parsing, could be table in modded
							trigger_object = table.getWikiEntityName(trigger.get("entity").toString());
						}
						if (!trigger_object.isEmpty()) {
							itemJson.put("trigger-object", pair(trigger_object, trigger_object_count));
						}
					} else {
						System.err.println("Tech without unit and without research_trigger");
					}

					if (!tech.getPrerequisites().isEmpty()) {
						itemJson.put("required-technologies",
								tech.getPrerequisites().stream().sorted()
										.map(n -> wiki_fmtNumberedWikiName(table.getWikiTechnologyName(n)))
										.collect(toJsonArray()));
					}

					if (!tech.isFirstBonus()) {
						Collection<String> allows = allowsMap.get(tech.getName());
						if (!allows.isEmpty()) {
							itemJson.put("allows",
									allows.stream().sorted()
											.map(n -> wiki_fmtNumberedWikiName(table.getWikiTechnologyName(n)))
											.collect(toJsonArray()));
						}
					} else {
						if (!tech.isMaxLevelInfinite() && tech.getBonusGroup().size() == 2) {
							itemJson.put("allows", new JSONArray(
									new String[] { table.getWikiTechnologyName(tech.getBonusName()) + ", 2" }));
						} else {

							String lastLevel;
							if (tech.isMaxLevelInfinite()
									|| tech.getBonusGroup().get(tech.getBonusGroup().size() - 1).isMaxLevelInfinite())
								lastLevel = "&infin;";
							else
								lastLevel = String.valueOf(tech.getBonusGroup().size());
							itemJson.put("allows", new JSONArray(new String[] {
									table.getWikiTechnologyName(tech.getBonusName()) + ", 2-" + lastLevel }));
						}
					}

					if (!tech.getRecipeUnlocks().isEmpty()) {
						itemJson.put("effects", tech.getRecipeUnlocks().stream().sorted()
								.map(n -> table.getWikiRecipeName(n)).collect(toJsonArray()));
					}
				});
		return json;
	}

	private static JSONObject wiki_Types(DataTable table, Map<String, WikiTypeMatch> wikiTypes) {
		JSONObject json = createOrderedJSONObject();

		wikiTypes.entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey())).forEach(e -> {
			String name = e.getKey();
			WikiTypeMatch m = e.getValue();
			String type = m.toString();

			if (!m.item && !m.recipe && !m.fluid) {
				return;
			}

			if (m.recipe && table.getRecipe(name).get().isRecycling())
				return;

			JSONObject item = createOrderedJSONObject();
			json.put(m.item ? table.getWikiItemName(name) : table.getWikiRecipeName(name), item);
			item.put("internal-name", name);
			item.put("prototype-type", type);
		});

		return json;
	}

	@SuppressWarnings("unused")
	private static JSONObject wiki_TypeTree(DataTable table) {
		JSONObject json = createOrderedJSONObject();

		Multimap<String, String> links = LinkedHashMultimap.create();
		Multimap<String, String> leafs = LinkedHashMultimap.create();

		table.getTypeHierarchy().getParents().forEach((n, p) -> {
			links.put(p, n);
		});
		table.getTypeHierarchy().getRoots().forEach(n -> {
			links.put("__ROOT__", n);
		});

		Utils.forEach(table.getRawLua(), v -> {
			Utils.forEach(v.checktable(), protoLua -> {
				String type = protoLua.get("type").tojstring();
				String name = protoLua.get("name").tojstring();
				leafs.put(type, name);
				if (!table.getTypeHierarchy().getParents().containsKey(type)
						&& !table.getTypeHierarchy().getRoots().contains(type)) {
					System.err.println("MISSING PARENT FOR TYPE: " + type + " (" + name + ")");
				}
			});
		});

		Collection<String> rootTypes = links.get("__ROOT__");
		rootTypes.stream().sorted().forEach(n -> {
			json.put(n, wiki_TypeTree_GenerateNode(links, leafs, n));
		});

		return json;
	}

	private static JSONObject wiki_TypeTree_GenerateNode(Multimap<String, String> links, Multimap<String, String> leafs,
			String parent) {
		Collection<String> types = links.get(parent);
		Collection<String> names = leafs.get(parent);

		JSONObject nodeJson = createOrderedJSONObject();
		Streams.concat(types.stream(), names.stream()).sorted().forEach(n -> {
			if (types.contains(n)) {
				nodeJson.put(n, wiki_TypeTree_GenerateNode(links, leafs, n));
			} else {
				nodeJson.put(n, new JSONObject());
			}
		});
		return nodeJson;
	}

	private static void write(JSONObject json, String name) throws JSONException, IOException {
		Files.write(json.toString(2), new File(folder, name + "-" + baseInfo.getVersion() + ".json"),
				StandardCharsets.UTF_8);
	}
}
