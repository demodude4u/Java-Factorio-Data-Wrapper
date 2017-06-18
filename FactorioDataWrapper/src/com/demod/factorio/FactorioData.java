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
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.ResourceFinder;
import org.luaj.vm2.lib.jse.JsePlatform;

import com.demod.factorio.port.SimpleMathFormula;
import com.demod.factorio.port.SimpleMathFormula.Expression;
import com.demod.factorio.port.SimpleMathFormula.InputException;
import com.demod.factorio.prototype.DataPrototype;

public class FactorioData {

	private static final String SEARCHJAR = "SEARCHJAR";

	private static Map<String, BufferedImage> modImageCache = new HashMap<>();
	private static Map<String, BufferedImage> modIconCache = new HashMap<>();

	public static File factorio;

	private static DataTable dataTable = null;
	private static Globals globals;

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
				return getModImage(iconLua);
			}
			LuaValue iconsLua = prototype.lua().get("icons");
			BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = icon.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			AffineTransform pat = g.getTransform();
			Utils.forEach(iconsLua, l -> {
				BufferedImage image = getModImage(l.get("icon"));

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

	public static BufferedImage getModImage(LuaValue value) {
		return getModImage(value, Optional.empty());
	}

	public static BufferedImage getModImage(LuaValue value, Color tint) {
		return getModImage(value, Optional.of(tint));
	}

	private static BufferedImage getModImage(LuaValue value, Optional<Color> tint) {
		String path = value.toString();
		return modImageCache.computeIfAbsent(path, p -> {
			String firstSegment = path.split("\\/")[0];
			String mod = firstSegment.substring(2, firstSegment.length() - 2);
			File modFolder = new File(factorio, "data/" + mod);
			try {
				BufferedImage image = loadImage(new File(modFolder, path.replace(firstSegment, "").substring(1)));
				if (tint.isPresent()) {
					image = Utils.tintImage(image, tint.get());
				}
				return image;
			} catch (IOException e) {
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
				new File(factorio, "data"), //
				new File(factorio, "data/core"), //
				new File(factorio, "data/base"), //
		};

		String luaPath = SEARCHJAR + "/?.lua;"
				+ Arrays.stream(luaFolders).map(f -> f.getAbsolutePath() + File.separator + "?.lua")
						.collect(Collectors.joining(";")).replace('\\', '/');
		// System.out.println("LUA_PATH: " + luaPath);

		TypeHiearchy typeHiearchy = new TypeHiearchy(
				Utils.readJsonFromStream(new URL(Config.get().getString("type_schema")).openStream()));

		globals = JsePlatform.standardGlobals();
		globals.load(new BaseLib());
		globals.load(new DebugLib());
		globals.load(new StringReader("package.path = package.path .. ';" + luaPath + "'"), "initLuaPath").call();
		globals.finder = new ResourceFinder() {
			@Override
			public InputStream findResource(String filename) {
				// System.out.print(filename + "? ");
				if (filename.startsWith(SEARCHJAR)) {
					InputStream stream = FactorioData.class.getClassLoader()
							.getResourceAsStream(filename.replace(SEARCHJAR, "lua"));
					// System.out.println(stream != null);
					return stream;
				} else {
					File file = new File(filename);
					// System.out.println(file.exists());
					try {
						return file.exists() ? new FileInputStream(file) : null;
					} catch (FileNotFoundException e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}
			}
		};

		globals.load(new InputStreamReader(globals.finder.findResource(SEARCHJAR + "/loader.lua")), "loader").call();

		JSONObject excludeDataJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("exclude-data.json"));
		DataTable dataTable = new DataTable(typeHiearchy, globals.get("data").checktable(), excludeDataJson);

		return dataTable;
	}

	private static BufferedImage loadImage(File file) throws IOException {
		BufferedImage image = ImageIO.read(file);
		if (image.getType() == BufferedImage.TYPE_CUSTOM) {
			System.err.println("CUSTOM IMAGE: " + file.getAbsolutePath());
			image = convertCustomImage(image);
		}
		return image;
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
