package com.demod.factorio.apps;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.json.JSONException;
import org.json.JSONObject;

import com.demod.factorio.DataTable;
import com.demod.factorio.FactorioData;
import com.demod.factorio.FactorioEnvironment;
import com.demod.factorio.Utils;
import com.demod.factorio.prototype.RecipePrototype;

public class FactorioJSONMain {

	private static void json_Recipes(DataTable table, PrintWriter pw) {
		JSONObject json = new JSONObject();
		Utils.terribleHackToHaveOrderedJSONObject(json);

		table.getRecipes().entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey())).forEach(e -> {
			RecipePrototype recipe = e.getValue();
			JSONObject recipeJson = new JSONObject();
			Utils.terribleHackToHaveOrderedJSONObject(recipeJson);
			json.put(recipe.getName(), recipeJson);

			recipeJson.put("wiki-name", table.getWikiRecipeName(recipe.getName()));
			recipeJson.put("type", recipe.getType());
			recipeJson.put("energy-required", recipe.getEnergyRequired());

			JSONObject inputsJson = new JSONObject();
			Utils.terribleHackToHaveOrderedJSONObject(inputsJson);
			recipeJson.put("inputs", inputsJson);
			recipe.getInputs().entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
					.forEach(e2 -> {
						inputsJson.put(e2.getKey(), e2.getValue());
					});

			JSONObject outputsJson = new JSONObject();
			Utils.terribleHackToHaveOrderedJSONObject(outputsJson);
			recipeJson.put("outputs", outputsJson);
			recipe.getOutputs().entrySet().stream().sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
					.forEach(e2 -> {
						outputsJson.put(e2.getKey(), e2.getValue());
					});
		});

		pw.println(json.toString(2));
	}

	public static void main(String[] args) throws JSONException, IOException {
		JSONObject jsonConfig = new JSONObject(Files.readString(Path.of("config.json")));
		FactorioEnvironment env = FactorioEnvironment.buildAndInitialize(jsonConfig, true);
		FactorioData data = env.getFactorioData();
		DataTable table = data.getTable();
		

		File outputFolder = new File("output/" + env.getVersion());
		outputFolder.mkdirs();

		try (PrintWriter pw = new PrintWriter(
				new File(outputFolder, "json-recipes-" + env.getVersion() + ".txt"))) {
			json_Recipes(table, pw);
		}

		Desktop.getDesktop().open(outputFolder);
	}

}
