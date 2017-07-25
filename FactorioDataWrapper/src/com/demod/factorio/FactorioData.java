package com.demod.factorio;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.ResourceFinder;
import org.luaj.vm2.lib.jse.JsePlatform;

import com.demod.factorio.ModLoader.Mod;
import com.demod.factorio.port.SimpleMathFormula;
import com.demod.factorio.port.SimpleMathFormula.Expression;
import com.demod.factorio.port.SimpleMathFormula.InputException;
import com.demod.factorio.prototype.DataPrototype;
import com.diffplug.common.base.Box;

public class FactorioData {

	private static final String SEARCH_MOD = "__MOD__";
	private static final String SEARCH_RESOURCE = "__RESOURCE__";

	private static Map<String, BufferedImage> modImageCache = new HashMap<>();
	private static Map<String, BufferedImage> modIconCache = new HashMap<>();

	public static File factorio;

	private static DataTable dataTable = null;
	private static ModLoader modLoader;

	/**
	 * I'm assuming this is some weird grayscale image...
	 */
	private static BufferedImage convertCustomImage(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		WritableRaster raster = image.getRaster();
		byte[] pixelData = (byte[]) raster.getDataElements(0, 0, width, height, null);
		int[] correctedPixels = new int[width * height];

		for (int i = 0; i < correctedPixels.length; i++) {
			int v = pixelData[i * 2] & 0xFF;
			int a = pixelData[i * 2 + 1] & 0xFF;
			correctedPixels[i] = (a << 24) | (v << 16) | (v << 8) | v;
		}

		BufferedImage ret = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		ret.setRGB(0, 0, width, height, correctedPixels, 0, width);
		return ret;
	}

	public static BufferedImage getIcon(DataPrototype prototype) {
		String name = prototype.getName();
		return modIconCache.computeIfAbsent(name, n -> {
			LuaValue iconLua = prototype.lua().get("icon");
			if (!iconLua.isnil()) {
				return getModImage(iconLua.tojstring());
			}
			LuaValue iconsLua = prototype.lua().get("icons");
			BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = icon.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			AffineTransform pat = g.getTransform();
			Utils.forEach(iconsLua, l -> {
				BufferedImage image = getModImage(l.get("icon").tojstring());

				LuaValue tintLua = l.get("tint");
				if (!tintLua.isnil()) {
					image = Utils.tintImage(image, Utils.parseColor(tintLua));
				}

				double scale = l.get("scale").optdouble(1.0);
				Point shift = Utils.parsePoint(l.get("shift"));

				g.translate(shift.x, shift.y);
				g.translate((icon.getWidth() / 2) - (image.getWidth() * (scale)) / 2,
						(icon.getHeight() / 2) - (image.getHeight() * (scale)) / 2);
				g.scale(scale, scale);
				g.drawImage(image, 0, 0, null);
				g.setTransform(pat);
			});
			g.dispose();
			return icon;
		});
	}

	public static BufferedImage getModImage(String path) {
		return getModImage(path, Optional.empty());
	}

	public static BufferedImage getModImage(String path, Color tint) {
		return getModImage(path, Optional.of(tint));
	}

	private static BufferedImage getModImage(String path, Optional<Color> tint) {
		return modImageCache.computeIfAbsent(path, p -> {
			String firstSegment = path.split("\\/")[0];
			if (firstSegment.length() < 4) {
				throw new IllegalArgumentException("Path is not valid: \"" + path + "\"");
			}
			String modName = firstSegment.substring(2, firstSegment.length() - 2);
			Optional<Mod> mod = modLoader.getMod(modName);
			if (!mod.isPresent()) {
				throw new IllegalStateException("Mod does not exist: " + modName);
			}
			String modPath = path.replace(firstSegment, "");
			try {
				BufferedImage image = loadImage(mod.get().getResource(modPath).get());
				if (tint.isPresent()) {
					image = Utils.tintImage(image, tint.get());
				}
				return image;
			} catch (Exception e) {
				System.err.println("MISSING MOD IMAGE " + modName + " : " + modPath);
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		});
	}

	public static synchronized DataTable getTable() throws JSONException, IOException {
		if (dataTable == null) {
			dataTable = initializeDataTable();
		}
		return dataTable;
	}

	private static DataTable initializeDataTable() throws JSONException, IOException {
		setupWorkingDirectory();

		factorio = new File(Config.get().getString("factorio"));

		File[] luaFolders = new File[] { //
				new File(factorio, "data/core/luaLib"), //
				// new File(factorio, "data"), //
				// new File(factorio, "data/core"), //
				// new File(factorio, "data/base"), //
		};

		modLoader = new ModLoader();
		modLoader.loadFolder(new File(factorio, "data"));

		File modsFolder = new File("mods");
		if (modsFolder.exists()) {
			modLoader.loadFolder(modsFolder);
		}

		String luaPath = SEARCH_MOD + "/?.lua;" + SEARCH_RESOURCE + "/?.lua;"
				+ Arrays.stream(luaFolders).map(f -> f.getAbsolutePath() + File.separator + "?.lua")
						.collect(Collectors.joining(";")).replace('\\', '/');
		// System.out.println("LUA_PATH: " + luaPath);

		TypeHiearchy typeHiearchy = new TypeHiearchy(Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("type-hiearchy.json")));

		Box<Mod> currentMod = Box.of(modLoader.getMod("core").get());

		Globals globals = JsePlatform.standardGlobals();
		globals.load(new BaseLib());
		globals.load(new DebugLib());
		globals.load(new StringReader("package.path = package.path .. ';" + luaPath + "'"), "initLuaPath").call();
		globals.finder = new ResourceFinder() {
			@Override
			public InputStream findResource(String filename) {
				if (filename.startsWith(SEARCH_MOD) && currentMod.get() != null) {
					try {
						return currentMod.get().getResource(filename.replace(SEARCH_MOD, "")).orElse(null);
					} catch (Exception e) {
						e.printStackTrace();
						throw new InternalError(e);
					}
				} else if (filename.startsWith(SEARCH_RESOURCE)) {
					InputStream stream = FactorioData.class.getClassLoader()
							.getResourceAsStream(filename.replace(SEARCH_RESOURCE, "lua"));
					// System.out.println(stream != null);
					return stream;
				} else {
					File file = new File(filename);
					// System.out.println(file.exists());
					try {
						return file.exists() ? new FileInputStream(file) : null;
					} catch (FileNotFoundException e) {
						e.printStackTrace();
						throw new InternalError(e);
					}
				}
			}
		};

		globals.load(new InputStreamReader(globals.finder.findResource(SEARCH_RESOURCE + "/loader.lua")), "loader")
				.call();

		List<Mod> loadOrder = modLoader.getModsInLoadOrder();

		loadStage(globals, loadOrder, currentMod, "/settings.lua");
		loadStage(globals, loadOrder, currentMod, "/settings-updates.lua");
		loadStage(globals, loadOrder, currentMod, "/settings-final-fixes.lua");

		initializeSettings(globals);

		loadStage(globals, loadOrder, currentMod, "/data.lua");
		loadStage(globals, loadOrder, currentMod, "/data-updates.lua");
		loadStage(globals, loadOrder, currentMod, "/data-final-fixes.lua");

		// XXX Do I need to do anything with control.lua things?
		// http://lua-api.factorio.com/latest/Data-Lifecycle.html

		JSONObject excludeDataJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("exclude-data.json"));
		JSONObject wikiNamingJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("wiki-naming.json"));
		DataTable dataTable = new DataTable(typeHiearchy, globals.get("data").checktable(), excludeDataJson,
				wikiNamingJson);

		return dataTable;
	}

	private static void initializeSettings(Globals globals) {
		LuaTable settingsLua = new LuaTable();
		LuaTable startupLua = new LuaTable();
		settingsLua.set("startup", startupLua);
		LuaTable runtimeLua = new LuaTable();
		settingsLua.set("runtime", runtimeLua);
		LuaTable runtimePerUserLua = new LuaTable();
		settingsLua.set("runtime-per-user", runtimePerUserLua);

		Utils.forEach(globals.get("data").get("raw"), v -> {
			Utils.forEach(v.checktable(), protoLua -> {
				String type = protoLua.get("type").tojstring();
				String name = protoLua.get("name").tojstring();

				if (type.endsWith("-setting")) {
					LuaTable lua = new LuaTable();
					lua.set("value", protoLua.get("default_value"));

					LuaTable settingTypeLua = null;
					switch (protoLua.get("setting_type").tojstring()) {
					case "startup":
						settingTypeLua = startupLua;
						break;
					case "runtime-global":
						settingTypeLua = runtimeLua;
						break;
					case "runtime-per-user":
						settingTypeLua = runtimePerUserLua;
						break;
					}
					settingTypeLua.set(name, lua);
				}
			});
		});

		globals.set("settings", settingsLua);
	}

	private static BufferedImage loadImage(InputStream is) throws IOException {
		BufferedImage image = ImageIO.read(is);
		if (image.getType() == BufferedImage.TYPE_CUSTOM) {
			image = convertCustomImage(image);
		}
		return image;
	}

	private static void loadStage(Globals globals, List<Mod> loadOrder, Box<Mod> currentMod, String filename)
			throws IOException {
		for (Mod mod : loadOrder) {
			currentMod.set(mod);
			Optional<InputStream> resource = mod.getResource(filename);
			if (resource.isPresent()) {
				globals.load(new InputStreamReader(resource.get()), mod.getInfo().getName() + "_" + filename).call();
			}
		}
	}

	public static IntUnaryOperator parseCountFormula(String countFormula) {
		Expression expression;
		try {
			expression = SimpleMathFormula.Expression.parse(countFormula, 0);
		} catch (InputException e) {
			System.err.println("COUNT FORMULA PARSE FAIL: " + countFormula);
			e.printStackTrace();
			return l -> -1;
		}
		Map<String, Double> values = new HashMap<>();
		return level -> {
			values.put("L", (double) level);
			return (int) expression.evaluate(values);
		};
	}

	private static void setupWorkingDirectory() {
		String className = FactorioData.class.getName().replace('.', '/');
		String classJar = FactorioData.class.getResource("/" + className + ".class").toString();
		if (classJar.startsWith("jar:")) {
			try {
				File jarFolder = new File(
						FactorioData.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())
								.getParentFile();
				// System.out.println("Jar Folder: " +
				// jarFolder.getAbsolutePath());
				System.setProperty("user.dir", jarFolder.getAbsolutePath());
			} catch (URISyntaxException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
}
