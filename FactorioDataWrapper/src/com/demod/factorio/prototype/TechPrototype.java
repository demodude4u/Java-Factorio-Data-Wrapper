package com.demod.factorio.prototype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import com.demod.factorio.Utils;

public class TechPrototype extends DataPrototype {

	public static class Effect {
		private final LuaValue lua;
		private final String type;

		public Effect(LuaValue lua) {
			this.lua = lua;
			type = lua.get("type").tojstring();
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
	private final Map<String, Integer> ingredients = new LinkedHashMap<>();
	private int count;
	private final double time;
	private final String order;
	private final List<String> recipeUnlocks = new ArrayList<>();

	private boolean firstBonus;
	private boolean bonus;
	private List<TechPrototype> bonusGroup;
	private String bonusName;
	private int bonusLevel;

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

		LuaValue unitLua = lua.get("unit");
		Utils.forEach(unitLua.get("ingredients"), lv -> {
			ingredients.put(lv.get(1).tojstring(), lv.get(2).toint());
		});
		count = unitLua.get("count").toint();
		time = unitLua.get("time").todouble();

		for (Effect effect : getEffects()) {
			if (effect.getType().equals("unlock-recipe")) {
				String recipeName = effect.lua().get("recipe").tojstring();
				if (!excludedRecipesAndItems.contains(recipeName)) {
					recipeUnlocks.add(recipeName);
				}
			}
		}
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

	public List<Effect> getEffects() {
		return effects;
	}

	public Map<String, Integer> getIngredients() {
		return ingredients;
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

	public boolean isUpgrade() {
		return upgrade;
	}

	public void setBonus(boolean bonus) {
		this.bonus = bonus;
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
