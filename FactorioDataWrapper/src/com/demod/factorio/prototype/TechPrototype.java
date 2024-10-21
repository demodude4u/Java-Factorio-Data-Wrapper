package com.demod.factorio.prototype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import com.demod.factorio.Utils;

public class TechPrototype extends DataPrototype {

	public static class Effect {
		private final LuaValue lua;
		private final String type;
		private final double modifier;

		public Effect(LuaValue lua) {
			this.lua = lua;
			type = lua.get("type").tojstring();
			LuaValue modifierLua = lua.get("modifier");
			if (modifierLua.isnumber()) {
				modifier = modifierLua.todouble();
			} else {
				modifier = 0;
			}
		}

		public String getKey() {
			if (type.equals("ammo-damage")) {
				return type + "|" + lua.get("ammo_category").tojstring();
			}
			return type;
		}

		public double getModifier() {
			return modifier;
		}

		public String getType() {
			return type;
		}

		public LuaValue lua() {
			return lua;
		}
	}

	private final boolean upgrade;
	private final List<String> prerequisites = new ArrayList<>();
	private final List<Effect> effects = new ArrayList<>();
	private final LinkedHashMap<String, Integer> ingredients = new LinkedHashMap<>();
	private int count;
	private final double time;
	private final String order;
	private final List<String> recipeUnlocks = new ArrayList<>();
	private final Optional<String> maxLevel;
	private final boolean maxLevelInfinite;

	private boolean firstBonus;
	private boolean bonus;
	private List<TechPrototype> bonusGroup;
	private String bonusName;
	private int bonusLevel;
	private Optional<IntUnaryOperator> bonusCountFormula = Optional.empty();
	private Optional<String> bonusCountFormulaVisual = Optional.empty();

	public TechPrototype(LuaTable lua, String name, String type, Set<String> excludedRecipesAndItems) {
		super(lua, name, type);

		upgrade = lua.get("upgrade").toboolean();
		order = lua.get("order").tojstring();

		Utils.forEach(lua.get("prerequisites").opttable(new LuaTable()), l -> {
			prerequisites.add(l.tojstring());
		});

		Utils.forEach(lua.get("effects").opttable(new LuaTable()), l -> {
			effects.add(new Effect(l));
		});

		LuaValue unitLua = lua.get("unit"); // TODO research triggers?
		if (!unitLua.isnil()) {
			Utils.forEach(unitLua.get("ingredients").opttable(new LuaTable()), lv -> {
				ingredients.put(lv.get(1).tojstring(), lv.get(2).toint());
			});
			count = unitLua.get("count").toint();
			time = unitLua.get("time").todouble();
		} else {
			time = 0;
		}

		for (Effect effect : getEffects()) {
			if (effect.getType().equals("unlock-recipe")) {
				String recipeName = effect.lua().get("recipe").tojstring();
				if (!excludedRecipesAndItems.contains(recipeName)) {
					recipeUnlocks.add(recipeName);
				}
			}
		}

		LuaValue maxLevelLua = lua.get("max_level");
		if (!maxLevelLua.isnil()) {
			String value = maxLevelLua.tojstring();
			maxLevel = Optional.of(value);
			maxLevelInfinite = value.equals("infinite");
		} else {
			maxLevel = Optional.empty();
			maxLevelInfinite = false;
		}
	}

	public Optional<IntUnaryOperator> getBonusCountFormula() {
		return bonusCountFormula;
	}

	public Optional<String> getBonusCountFormulaVisual() {
		return bonusCountFormulaVisual;
	}

	public List<TechPrototype> getBonusGroup() {
		return bonusGroup;
	}

	public int getBonusLevel() {
		return bonusLevel;
	}

	public String getBonusName() {
		return bonusName;
	}

	public int getCount() {
		return count;
	}

	public int getEffectiveCount() {
		return (isBonus() && getBonusCountFormula().isPresent())
				? getBonusCountFormula().get().applyAsInt(getBonusLevel())
				: getCount();
	}

	public List<Effect> getEffects() {
		return effects;
	}

	public LinkedHashMap<String, Integer> getIngredients() {
		return ingredients;
	}

	public Optional<String> getMaxLevel() {
		return maxLevel;
	}

	public String getOrder() {
		return order;
	}

	public List<String> getPrerequisites() {
		return prerequisites;
	}

	public List<String> getRecipeUnlocks() {
		return recipeUnlocks;
	}

	public double getTime() {
		return time;
	}

	public boolean isBonus() {
		return bonus;
	}

	public boolean isFirstBonus() {
		return firstBonus;
	}

	public boolean isMaxLevelInfinite() {
		return maxLevelInfinite;
	}

	public boolean isUpgrade() {
		return upgrade;
	}

	public void setBonus(boolean bonus) {
		this.bonus = bonus;
	}

	public void setBonusFormula(Optional<String> bonusCountFormulaVisual,
			Optional<IntUnaryOperator> bonusCountFormula) {
		this.bonusCountFormulaVisual = bonusCountFormulaVisual;
		this.bonusCountFormula = bonusCountFormula;
	}

	public void setBonusGroup(List<TechPrototype> bonusGroup) {
		this.bonusGroup = bonusGroup;
	}

	public void setBonusLevel(int bonusLevel) {
		this.bonusLevel = bonusLevel;
	}

	public void setBonusName(String bonusName) {
		this.bonusName = bonusName;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public void setFirstBonus(boolean firstBonus) {
		this.firstBonus = firstBonus;
	}
}
