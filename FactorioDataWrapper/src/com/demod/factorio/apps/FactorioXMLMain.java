package com.demod.factorio.apps;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import com.demod.factorio.DataTable;
import com.demod.factorio.FactorioData;
import com.demod.factorio.ModInfo;
import com.demod.factorio.Utils;
import com.demod.factorio.prototype.RecipePrototype;

public class FactorioXMLMain {
	private static Element createItemQuantityElement(Document doc, DataTable table,
			Map<String, ? extends Number> itemMap, String elementName) {
		Element itemsElement = doc.createElement(elementName);
		itemMap.forEach((name, qty) -> {
			Element itemQuantityElement = doc.createElement("item");
			itemsElement.appendChild(itemQuantityElement);

			Attr itemIdAttr = doc.createAttribute("id");
			itemIdAttr.setValue(name);
			itemQuantityElement.setAttributeNode(itemIdAttr);

			Element itemNameElement = doc.createElement("wikiName");
			itemNameElement.appendChild(doc.createTextNode(table.getWikiItemName(name)));
			itemQuantityElement.appendChild(itemNameElement);

			Element quantityElement = doc.createElement("quantity");
			quantityElement.appendChild(doc.createTextNode(qty.toString()));
			itemQuantityElement.appendChild(quantityElement);
		});
		return itemsElement;
	}

	private static void generateRecipesXML(Map<String, RecipePrototype> recipes, DataTable table, String fileName)
			throws ParserConfigurationException, ClassNotFoundException, InstantiationException, IllegalAccessException,
			IOException {
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

		Document doc = documentBuilder.newDocument();

		Element rootElement = doc.createElement("recipes");
		doc.appendChild(rootElement);

		recipes.values().stream().sorted((r1, r2) -> r1.getName().compareTo(r2.getName())).forEach(r -> {
			Element recipeElement = doc.createElement("recipe");
			rootElement.appendChild(recipeElement);

			Attr idAttr = doc.createAttribute("id");
			idAttr.setValue(r.getName());
			recipeElement.setAttributeNode(idAttr);

			Element nameElement = doc.createElement("wikiName");
			nameElement.appendChild(doc.createTextNode(table.getWikiRecipeName(r.getName())));
			recipeElement.appendChild(nameElement);

			Element energyRequiredElement = doc.createElement("energyRequired");
			energyRequiredElement.appendChild(doc.createTextNode(Double.toString(r.getEnergyRequired())));
			recipeElement.appendChild(energyRequiredElement);

			recipeElement.appendChild(createItemQuantityElement(doc, table, r.getInputs(), "inputs"));

			recipeElement.appendChild(createItemQuantityElement(doc, table, r.getOutputs(), "outputs"));
		});

		// TransformerFactory transformerFactory =
		// TransformerFactory.newInstance();
		// transformerFactory.setAttribute("indent-number", 2);
		// Transformer transformer = transformerFactory.newTransformer();
		// transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		//
		// DOMSource source = new DOMSource(doc);
		// StreamResult result = new StreamResult(new File("recipes-" +
		// baseInfo.getVersion() + ".xml"));
		//
		// transformer.transform(source, result);

		DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
		DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
		LSSerializer writer = impl.createLSSerializer();
		writer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
		LSOutput output = impl.createLSOutput();
		output.setCharacterStream(new FileWriter(fileName));
		writer.write(doc, output);
	}

	public static void main(String[] args) throws JSONException, IOException, ParserConfigurationException,
			ClassNotFoundException, InstantiationException, IllegalAccessException, ClassCastException {
		DataTable table = FactorioData.getTable();
		ModInfo baseInfo = new ModInfo(
				Utils.readJsonFromStream(new FileInputStream(new File(FactorioData.factorio, "data/base/info.json"))));

		generateRecipesXML(table.getRecipes(), table, "recipes-normal-" + baseInfo.getVersion() + ".xml");
	}
}
