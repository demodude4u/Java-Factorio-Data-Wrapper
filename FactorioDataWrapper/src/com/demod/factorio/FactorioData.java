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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
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
	private static final int defaultIconSize = 64; // TODO read from defines

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
		if (prototype.lua().get("type").checkjstring().equals("technology")) {
			name += ".tech"; // HACK
		}
		return modIconCache.computeIfAbsent(name, n -> {
			LuaValue iconLua = prototype.lua().get("icon");
			if (!iconLua.isnil()) {
				int iconSize = prototype.lua().get("icon_size").optint(defaultIconSize);

				// TODO skip this call if layer.getWidth() == layerIconSize
				return getModImage(iconLua.tojstring()).getSubimage(0, 0, iconSize, iconSize);
			}
			LuaValue iconsLua = prototype.lua().get("icons");

			if (iconsLua.isnil()) {
				System.err.println(prototype.lua().get("type").checkjstring() + " " + n + " has no icon.");
				return new BufferedImage(defaultIconSize, defaultIconSize, BufferedImage.TYPE_INT_ARGB);
			}

			int sizeOfFirstLayer = iconsLua.get(1).get("icon_size").optint(defaultIconSize);

			BufferedImage icon = new BufferedImage(sizeOfFirstLayer, sizeOfFirstLayer, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = icon.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			AffineTransform pat = g.getTransform();
			Utils.forEach(iconsLua, l -> {
				BufferedImage layer = getModImage(l.get("icon").tojstring());
				int layerIconSize = l.get("icon_size").optint(defaultIconSize);
				// TODO skip this call if layer.getWidth() == layerIconSize
				layer = layer.getSubimage(0, 0, layerIconSize, layerIconSize);

				LuaValue tintLua = l.get("tint");
				if (!tintLua.isnil()) {
					layer = Utils.tintImage(layer, Utils.parseColor(tintLua));
				}

				int expectedSize = 32; // items and recipes (and most other things)
				if (prototype.lua().get("type").checkjstring().equals("technology"))
					expectedSize = 128;

				/*
				 * All vanilla item and recipe icons are defined with icon size 64 (technologies
				 * with 256). However, the game "expects" icons to have a size of 32 (or 128 for
				 * technologies). Because these sizes differ, we observe the behavior that the
				 * game does not apply shift and scale values directly. Instead, shift and scale
				 * are multiplied by real_size / expected_size. In the case of items case, that
				 * means we have to multiply them by 2, because 64 / 32 = 2; this value is
				 * represented by the below variable.
				 */
				int scaleAndShiftScaling = sizeOfFirstLayer / expectedSize;

				double scale = l.get("scale").optdouble(1.0);
				// scale has to be multiplied by scaleAndShiftScaling, see above
				if (!l.get("scale").isnil()) // but only if it was defined
					scale *= scaleAndShiftScaling;

				// move icon into the center
				g.translate((icon.getWidth() / 2) - (layer.getWidth() * (scale)) / 2,
						(icon.getHeight() / 2) - (layer.getHeight() * (scale)) / 2);

				Point shift = Utils.parsePoint(l.get("shift"));
				// shift has to be multiplied by scaleAndShiftScaling, see above
				shift.x *= scaleAndShiftScaling;
				shift.y *= scaleAndShiftScaling;
				g.translate(shift.x, shift.y);

				// HACK
				// Overlay icon of equipment technology icons are outside bounds of base icon.
				// So, move the overlay icon up. Do the same for mining productivity tech.
				String path = l.get("icon").tojstring();
				if (path.equals("__core__/graphics/icons/technology/constants/constant-mining-productivity.png")) {
					g.translate(-8, -7);
				} else if (path.equals("__core__/graphics/icons/technology/constants/constant-equipment.png")) {
					g.translate(0, -20);
				}

				g.scale(scale, scale);
				g.drawImage(layer, 0, 0, null);
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
			try {
				BufferedImage image = loadImage(getModResource(path).get());
				if (tint.isPresent()) {
					image = Utils.tintImage(image, tint.get());
				}
				return image;
			} catch (Exception e) {
				System.err.println("MISSING MOD IMAGE: " + path);
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		});
	}

	public static Optional<InputStream> getModResource(String path) {
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
			return mod.get().getResource(modPath);
		} catch (IOException e) {
			System.err.println(path);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
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
				new File(factorio, "data/core/lualib"), //
		};

		JSONArray modExcludeJson = Config.get().optJSONArray("mod-exclude");
		Set<String> modExclude = new HashSet<>();
		if (modExcludeJson != null) {
			Utils.forEach(modExcludeJson, modExclude::add);
		}

		modLoader = new ModLoader(modExclude);
		modLoader.loadFolder(new File(factorio, "data"));

		File modsFolder = new File("mods");
		if (modsFolder.exists()) {
			modLoader.loadFolder(modsFolder);
		}

		String luaPath = SEARCH_MOD + "/?.lua;" + SEARCH_RESOURCE + "/?.lua;"
				+ Arrays.stream(luaFolders).map(f -> f.getAbsolutePath() + File.separator + "?.lua")
						.collect(Collectors.joining(";")).replace('\\', '/');
		// System.out.println("LUA_PATH: " + luaPath);

		TypeHierarchy typeHiearchy = new TypeHierarchy(Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("type-hiearchy.json")));

		List<Mod> loadOrder = modLoader.getModsInLoadOrder();

		System.out.println("LOAD ORDER:");
		for (Mod mod : loadOrder) {
			ModInfo info = mod.getInfo();
			System.out.println(" " + info.getName());
		}

		Box<Mod> currentMod = Box.of(modLoader.getMod("core").get());

		Globals globals = setupLuaState(luaPath, loadOrder, currentMod);
		List<LuaString> initialPackages = new ArrayList<>();
		Utils.forEach(globals.get("package").get("loaded"), (k, v) -> {
			initialPackages.add(k.checkstring());
		});

		loadStage(globals, initialPackages, loadOrder, currentMod, "/settings.lua");
		loadStage(globals, initialPackages, loadOrder, currentMod, "/settings-updates.lua");
		loadStage(globals, initialPackages, loadOrder, currentMod, "/settings-final-fixes.lua");

		LuaTable settingsLua = initializeSettings(globals);

		// Discard the LuaState from settings and create a new one for data
		globals = setupLuaState(luaPath, loadOrder, currentMod);
		globals.set("settings", settingsLua);

		loadStage(globals, initialPackages, loadOrder, currentMod, "/data.lua");
		loadStage(globals, initialPackages, loadOrder, currentMod, "/data-updates.lua");
		loadStage(globals, initialPackages, loadOrder, currentMod, "/data-final-fixes.lua");

		JSONObject excludeDataJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("exclude-data.json"));
		JSONObject includeDataJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("include-data.json"));
		JSONObject wikiNamingJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("wiki-naming.json"));
		DataTable dataTable = new DataTable(typeHiearchy, globals.get("data").checktable(), excludeDataJson,
				includeDataJson, wikiNamingJson);

		return dataTable;
	}

	private static LuaTable initializeSettings(Globals globals) {
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

		return settingsLua;
	}

	private static BufferedImage loadImage(InputStream is) throws IOException {
		BufferedImage image = ImageIO.read(is);
		if (image.getType() == BufferedImage.TYPE_CUSTOM) {
			image = convertCustomImage(image);
		}
		return image;
	}

	private static void loadStage(Globals globals, List<LuaString> initialPackages, List<Mod> loadOrder,
			Box<Mod> currentMod, String filename) throws IOException {
		for (Mod mod : loadOrder) {
			resetPackagesLoaded(globals, initialPackages);
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

	private static void resetPackagesLoaded(Globals globals, List<LuaString> initialPackages) {
		List<LuaString> toErase = new ArrayList<>();
		LuaTable loaded = globals.get("package").get("loaded").checktable();
		Utils.forEach(loaded, (k, v) -> {
			LuaString packageName = k.checkstring();
			if (!initialPackages.contains(packageName))
				toErase.add(packageName);
		});
		for (LuaString packageName : toErase) {
			loaded.set(packageName, LuaValue.NIL);
		}
	}

	private static Globals setupLuaState(String luaPath, List<Mod> loadOrder, Box<Mod> currentMod) {
		Globals globals = JsePlatform.standardGlobals();
		globals.load(new BaseLib());
		globals.load(new DebugLib());
		globals.load(new Bit32Lib());
		globals.load(new StringReader("package.path = package.path .. ';" + luaPath + "'"), "initLuaPath").call();
		globals.finder = new ResourceFinder() {
			@Override
			public InputStream findResource(String filename) {
				/*
				 * Problematic call stack: PackageLib$searchpath.invoke(Varargs) line: 291
				 * PackageLib$lua_searcher.invoke(Varargs) line: 263
				 * PackageLib$require.call(LuaValue) line: 217
				 * 
				 * PackageLib$searchpath.invoke(Varargs) line: 291 replaces all "." with
				 * FILE_SEP ( = System.getProperty("file.separator")), on Windows this is "\".
				 * This means that if a require uses "file.lua" as the file name, this is
				 * converted to "file\lua".
				 * 
				 * So now when applying the package path, LuaJ will add the ".lua" from the
				 * "?.lua" package path back on it and it turns into "file\lua.lua". And then
				 * the file isn't found!
				 * 
				 * Solution: Hack it by turning "\lua.lua" back into ".lua".
				 * 
				 * This will give wrong results if someone has a file named "lua.lua". I
				 * consider this less likely than a require that contains the file extension.
				 * 
				 */
				if (filename.endsWith(File.separator + "lua.lua")) {
					filename = filename.replace(File.separator + "lua.lua", ".lua");
				}
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
				} else if (filename.startsWith("__") && (filename.indexOf("__", 2) > -1)) {
					int matchEnd = filename.indexOf("__", 2);
					String modName = filename.substring(2, matchEnd);
					Optional<Mod> mod = modLoader.getMod(modName);
					if (!mod.isPresent()) {
						throw new IllegalStateException("Mod does not exist: " + modName);
					}
					try {
						return mod.get().getResource(filename.substring(matchEnd + 2)).orElse(null);
					} catch (Exception e) {
						e.printStackTrace();
						throw new InternalError(e);
					}
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

		LuaValue modsTable = LuaValue.tableOf(0, loadOrder.size());
		for (Mod mod : loadOrder) {
			ModInfo info = mod.getInfo();
			if (!info.getName().equals("core"))
				modsTable.set(info.getName(), info.getVersion());
		}
		globals.set("mods", modsTable);

		globals.load(new StringReader(
				"old_tonumber = tonumber; tonumber = function(e, base) local n = old_tonumber(e, base); if e == \"-\" then return nil end; return n; end"),
				"overwriteTonumber").call();

		return globals;
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
