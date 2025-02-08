package com.demod.factorio;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.demod.factorio.ModLoader.Mod;
import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.fakelua.LuaValue;
import com.demod.factorio.port.SimpleMathFormula;
import com.demod.factorio.port.SimpleMathFormula.Expression;
import com.demod.factorio.port.SimpleMathFormula.InputException;
import com.demod.factorio.prototype.DataPrototype;

public class FactorioData {

	private static final int defaultIconSize = 64; // TODO read from defines

	private static Map<String, BufferedImage> modImageCache = new HashMap<>();
	private static Map<String, BufferedImage> modIconCache = new HashMap<>();

	private static DataTable dataTable = null;
	private static ModLoader modLoader;

	public static File folderFactorio;
	public static File folderMods;

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

	public static void factorioDataDump(File folderFactorio, File fileConfig, File folderMods) {
		try {
			ProcessBuilder pb = new ProcessBuilder(new File(folderFactorio, "bin/x64/factorio.exe").getAbsolutePath(),
					"--config", fileConfig.getAbsolutePath(), "--mod-directory", folderMods.getAbsolutePath(),
					"--dump-data");
			pb.directory(folderFactorio);
			System.out.println("Running command " + pb.command().stream().collect(Collectors.joining(",", "[", "]")));
			Process process = pb.start();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				throw new IOException("Factorio command failed with exit code: " + exitCode);
			}
		} catch (Exception e) {
			System.err.println("FAILED TO DUMP DATA FROM FACTORIO INSTALL!");
			System.out.println("\t factorio: " + folderFactorio.getAbsolutePath());
			System.out.println("\t config: " + fileConfig.getAbsolutePath());
			System.out.println("\t mods: " + folderMods.getAbsolutePath());
			e.printStackTrace();
			System.exit(-1);
		}
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
			Utils.forEach(iconsLua.totableArray(), l -> {
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
		return modImageCache.computeIfAbsent(path, p -> {
			try {
				BufferedImage image = loadImage(getModResource(path).get());
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

	public static synchronized DataTable getTable() {
		if (dataTable == null) {
			try {
				dataTable = initializeDataTable();
			} catch (JSONException | IOException e) {
				throw new InternalError(e);
			}
		}
		return dataTable;
	}

	private static DataTable initializeDataTable() throws JSONException, IOException {
//		setupWorkingDirectory();//TODO do we still need this?

		JSONObject config = Config.get();

		folderFactorio = new File(config.getString("factorio"));
		boolean forceDumpData = config.optBoolean("force-dump-data");

		// Setup data folder

		File folderData = new File("data");
		folderData.mkdirs();

		File fileConfig = new File(folderData, "config.ini");
		try (PrintWriter pw = new PrintWriter(fileConfig)) {
			pw.println("[path]");
			pw.println("read-data=__PATH__executable__/../../data");
			pw.println("write-data=" + folderData.getAbsolutePath());
		}

		folderMods = Optional.of(config.optString("mods", null)).map(File::new).orElse(new File(folderData, "mods"));
		folderMods.mkdirs();

		File fileModList = new File(folderMods, "mod-list.json");
		if (!fileModList.exists()) {
			Files.copy(FactorioData.class.getClassLoader().getResourceAsStream("mod-list.json"), fileModList.toPath());
		}

		File fileModRendering = new File(folderMods, "mod-rendering.json");
		if (!fileModRendering.exists()) {
			Files.copy(FactorioData.class.getClassLoader().getResourceAsStream("mod-rendering.json"),
					fileModRendering.toPath());
		}

		JSONObject jsonModList = new JSONObject(Files.readString(fileModList.toPath()));
		Set<String> modInclude = new HashSet<>();
		modInclude.add("core");
		Utils.<JSONObject>forEach(jsonModList.getJSONArray("mods"), j -> {
			if (j.getBoolean("enabled")) {
				modInclude.add(j.getString("name"));
			}
		});

		File folderScriptOutput = new File(folderData, "script-output");
		File fileDataRawDump = new File(folderScriptOutput, "data-raw-dump.json");

		File fileDumpStamp = new File(folderData, "dumpStamp.txt");
		boolean matchingDumpStamp = false;
		try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
			pw.println("Factorio Install: " + folderFactorio.getAbsolutePath());
			pw.println("Data: " + folderData.getAbsolutePath());
			pw.println("Mods: " + folderMods.getAbsolutePath());
			pw.flush();
			String stamp = sw.toString();
			System.out.println(stamp);
			if (fileDumpStamp.exists()) {
				String compareStamp = Files.readString(fileDumpStamp.toPath());
				if (stamp.equals(compareStamp)) {
					matchingDumpStamp = true;
				}
			}
			Files.writeString(fileDumpStamp.toPath(), stamp);
		}

		// Fetch data dump file from factorio.exe

		if (!fileDataRawDump.exists() || !matchingDumpStamp || forceDumpData) {
			factorioDataDump(folderFactorio, fileConfig, folderMods);
			if (!fileDataRawDump.exists()) {
				System.err.println("DATA DUMP FILE MISSING! " + fileDataRawDump.getAbsolutePath());
				System.exit(-1);
			}
		}

		LuaTable lua = null;
		try (FileInputStream fis = new FileInputStream(fileDataRawDump)) {
			lua = new LuaTable(new JSONObject(new JSONTokener(fis)));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}

		modLoader = new ModLoader(modInclude);
		modLoader.loadFolder(new File(folderFactorio, "data"));
		modLoader.loadFolder(folderMods);

		TypeHierarchy typeHiearchy = new TypeHierarchy(Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("type-hiearchy.json")));
		JSONObject excludeDataJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("exclude-data.json"));
		JSONObject includeDataJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("include-data.json"));
		JSONObject wikiNamingJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("wiki-naming.json"));
		DataTable dataTable = new DataTable(typeHiearchy, lua, excludeDataJson, includeDataJson, wikiNamingJson);

		return dataTable;
	}

	private static BufferedImage loadImage(InputStream is) throws IOException {
		BufferedImage image = ImageIO.read(is);
		if (image.getType() == BufferedImage.TYPE_CUSTOM) {
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

	// XXX do we still need this?
//	private static void setupWorkingDirectory() {
//		String className = FactorioData.class.getName().replace('.', '/');
//		String classJar = FactorioData.class.getResource("/" + className + ".class").toString();
//		if (classJar.startsWith("jar:")) {
//			try {
//				File jarFolder = new File(
//						FactorioData.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath())
//						.getParentFile();
//				// System.out.println("Jar Folder: " +
//				// jarFolder.getAbsolutePath());
//				System.setProperty("user.dir", jarFolder.getAbsolutePath());
//			} catch (URISyntaxException e) {
//				e.printStackTrace();
//				System.exit(-1);
//			}
//		}
//	}
}
