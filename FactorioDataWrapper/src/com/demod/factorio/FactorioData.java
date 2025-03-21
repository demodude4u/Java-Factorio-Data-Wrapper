package com.demod.factorio;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demod.factorio.ModLoader.Mod;
import com.demod.factorio.fakelua.LuaTable;
import com.demod.factorio.fakelua.LuaValue;
import com.demod.factorio.prototype.DataPrototype;
import com.google.common.util.concurrent.Uninterruptibles;

public class FactorioData {
	private static final Logger LOGGER = LoggerFactory.getLogger(FactorioData.class);

	private static FactorioData defaultInstance;

	private static BufferedImage convertCustomImage(BufferedImage image) {
		BufferedImage ret = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = ret.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return ret;
	}

	@SuppressWarnings("resource")
	public static void factorioDataDump(File folderFactorio, File factorioExecutable, File fileConfig,
			File folderMods) {
		try {
			ProcessBuilder pb = new ProcessBuilder(factorioExecutable.getAbsolutePath(), "--config",
					fileConfig.getAbsolutePath(), "--mod-directory", folderMods.getAbsolutePath(), "--dump-data");
			pb.directory(folderFactorio);

			LOGGER.debug("Running command " + pb.command().stream().collect(Collectors.joining(",", "[", "]")));

			Process process = pb.start();

			// Create separate threads to handle the output streams
			ExecutorService executor = Executors.newFixedThreadPool(2);
			executor.submit(() -> streamLogging(process.getInputStream(), false));
			executor.submit(() -> streamLogging(process.getErrorStream(), true));
			executor.shutdown();

			// Wait for Factorio to finish
			boolean finished = process.waitFor(1, TimeUnit.MINUTES);
			if (!finished) {
				LOGGER.error("Factorio did not exit!");
				process.destroyForcibly();
				process.onExit().get();
				LOGGER.warn("Factorio was force killed.");
			}

			int exitCode = process.exitValue();
			if (exitCode != 0) {
				throw new IOException("Factorio command failed with exit code: " + exitCode);
			}
		} catch (Exception e) {
			LOGGER.error("FAILED TO DUMP DATA FROM FACTORIO INSTALL!");
			LOGGER.error("\t factorio: " + folderFactorio.getAbsolutePath());
			LOGGER.error("\t executable: " + factorioExecutable.getAbsolutePath());
			LOGGER.error("\t config: {}", fileConfig.getAbsolutePath());
			LOGGER.error("\t mods: " + folderMods.getAbsolutePath());
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static synchronized DataTable getDefaultTable() {
		if (defaultInstance == null) {
			try {
				defaultInstance = new FactorioData(Config.get());
				defaultInstance.initialize();
			} catch (JSONException | IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		return defaultInstance.getTable();
	}

	private static BufferedImage loadImage(InputStream is) throws IOException {
		BufferedImage image = ImageIO.read(is);
		if (image.getType() == BufferedImage.TYPE_CUSTOM) {
			image = convertCustomImage(image);
		}
		return image;
	}

	private static void streamLogging(InputStream inputStream, boolean error) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (error) {
					LOGGER.error(line);
				} else {
					LOGGER.debug(line);
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private final int defaultIconSize = 64; // TODO read from define

	private DataTable dataTable = null;

	private Optional<ModLoader> modLoader;

	public Optional<File> folderFactorio;
	public File folderMods;
	private File folderData;

	public Optional<File> factorioExecutable;

	private final JSONObject config;

	private boolean hasFactorioInstall;

	private List<String> mods;

	public FactorioData(JSONObject config) {
		this.config = config;
	}

	private String fileMD5(File file) {
		if (!file.exists()) {
			return "<none>";
		}
		try {
			return new BigInteger(1, MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file.toPath())))
					.toString(16);
		} catch (NoSuchAlgorithmException | IOException e) {
			e.printStackTrace();
			System.exit(0);
			return null;
		}
	}

	private String generateStamp() {
		try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
			pw.println("Factorio Install: " + folderFactorio.get().getAbsolutePath());
			pw.println("Data Folder: " + folderData.getAbsolutePath());
			pw.println("Mods Folder: " + folderMods.getAbsolutePath());
			pw.println("mod-list.json MD5: " + fileMD5(new File(folderMods, "mod-list.json")));
			pw.println("mod-settings.dat MD5: " + fileMD5(new File(folderMods, "mod-settings.dat")));
			pw.println("Mods Manifest:");
			for (File file : folderMods.listFiles()) {
				if (file.isDirectory() || file.getName().endsWith(".zip")) {
					pw.println("\t" + file.getName());
				}
			}
			pw.flush();
			return sw.toString();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	public File getFolderData() {
		return folderData;
	}

	public Optional<File> getFolderFactorio() {
		return folderFactorio;
	}

	public File getFolderMods() {
		return folderMods;
	}

	public BufferedImage getModImage(String path) {
		try {
			BufferedImage image = loadImage(getModResource(path).get());
			return image;
		} catch (Exception e) {
			LOGGER.error("MISSING MOD IMAGE: " + path);
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	public Optional<ModLoader> getModLoader() {
		return modLoader;
	}

	public Optional<InputStream> getModResource(String path) {
		String firstSegment = path.split("\\/")[0];
		if (firstSegment.length() < 4) {
			throw new IllegalArgumentException("Path is not valid: \"" + path + "\"");
		}
		String modName = firstSegment.substring(2, firstSegment.length() - 2);
		Optional<Mod> mod = modLoader.get().getMod(modName);
		if (!mod.isPresent()) {
			throw new IllegalStateException("Mod does not exist: " + modName);
		}
		String modPath = path.replace(firstSegment, "");
		try {
			return mod.get().getResource(modPath);
		} catch (IOException e) {
			LOGGER.error(path);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public List<String> getMods() {
		return mods;
	}

	public DataTable getTable() {
		return dataTable;
	}

	public BufferedImage getWikiIcon(DataPrototype prototype) {
		String name = prototype.getName();
		if (prototype.lua().get("type").checkjstring().equals("technology")) {
			name += ".tech"; // HACK
		}
		LuaValue iconLua = prototype.lua().get("icon");
		if (!iconLua.isnil()) {
			int iconSize = prototype.lua().get("icon_size").optint(defaultIconSize);

			// TODO skip this call if layer.getWidth() == layerIconSize
			return getModImage(iconLua.tojstring()).getSubimage(0, 0, iconSize, iconSize);
		}
		LuaValue iconsLua = prototype.lua().get("icons");

		if (iconsLua.isnil()) {
			LOGGER.warn(prototype.lua().get("type").checkjstring() + " " + name + " has no icon.");
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
	}

	public boolean hasFactorioInstall() {
		return hasFactorioInstall;
	}

	public void initialize() throws JSONException, IOException {
//		setupWorkingDirectory();//TODO do we still need this?

		// Setup data folder
		folderData = new File(config.optString("data", "data"));
		folderData.mkdirs();

		File folderScriptOutput = new File(folderData, "script-output");
		File fileDataRawDump = new File(folderScriptOutput, "data-raw-dump.json");
		File fileDataRawDumpZip = new File(folderScriptOutput, "data-raw-dump.zip");

		folderMods = Optional.of(config.optString("mods", null)).map(File::new).orElse(new File(folderData, "mods"));
		folderMods.mkdirs();

		File fileModList = new File(folderMods, "mod-list.json");
		if (!fileModList.exists()) {
			Files.copy(FactorioData.class.getClassLoader().getResourceAsStream("mod-list.json"), fileModList.toPath());
		}

		JSONObject jsonModList = new JSONObject(Files.readString(fileModList.toPath()));
		mods = new ArrayList<>();
		mods.add("core");
		Utils.<JSONObject>forEach(jsonModList.getJSONArray("mods"), j -> {
			if (j.getBoolean("enabled")) {
				mods.add(j.getString("name"));
			}
		});

		File fileModRendering = new File(folderMods, "mod-rendering.json");
		if (!fileModRendering.exists()) {
			Files.copy(FactorioData.class.getClassLoader().getResourceAsStream("mod-rendering.json"),
					fileModRendering.toPath());
		}

		hasFactorioInstall = config.has("factorio") && config.has("executable");

		if (hasFactorioInstall) {
			folderFactorio = Optional.of(new File(config.getString("factorio")));
			factorioExecutable = Optional.of(new File(config.getString("executable")));

			File fileConfig = new File(folderData, "config.ini");
			try (PrintWriter pw = new PrintWriter(fileConfig)) {
				pw.println("[path]");
				pw.println("read-data=" + folderFactorio.get().getAbsolutePath());
				pw.println("write-data=" + folderData.getAbsolutePath());
			}

			// Prevent unnecessary changes so github doesn't get confused
			File fileModSettings = new File(folderMods, "mod-settings.dat");
			fileModList.setReadOnly();
			fileModSettings.setReadOnly();

			boolean forceDumpData = config.optBoolean("force-dump-data");

			File fileDumpStamp = new File(folderData, "dumpStamp.txt");
			boolean matchingDumpStamp = false;
			String stamp = generateStamp();

			LOGGER.debug("============================");
			LOGGER.debug("============================");
			LOGGER.debug(stamp);
			if (fileDumpStamp.exists()) {
				String compareStamp = Files.readString(fileDumpStamp.toPath());
				if (stamp.equals(compareStamp)) {
					matchingDumpStamp = true;
				}
			}

			// Fetch data dump file from factorio.exe

			if (!fileDataRawDumpZip.exists() || !matchingDumpStamp || forceDumpData) {
				factorioDataDump(folderFactorio.get(), factorioExecutable.get(), fileConfig, folderMods);

				Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
				if (!fileDataRawDump.exists()) {
					LOGGER.error("DATA DUMP FILE MISSING! " + fileDataRawDump.getAbsolutePath());
					System.exit(-1);
				}

				try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(fileDataRawDumpZip))) {
					zos.putNextEntry(new ZipEntry(fileDataRawDump.getName()));
					zos.write(Files.readAllBytes(fileDataRawDump.toPath()));
					zos.closeEntry();
				}
				LOGGER.info("Write Data Zip: {}", fileDataRawDumpZip.getAbsolutePath());
				fileDataRawDump.delete();
				LOGGER.info("Delete Data: {}", fileDataRawDump.getAbsolutePath());

				Files.writeString(fileDumpStamp.toPath(), generateStamp());
			}

			modLoader = Optional.of(new ModLoader(mods));
			modLoader.get().loadFolder(folderFactorio.get());
			modLoader.get().loadFolder(folderMods);

		} else {
			folderFactorio = Optional.empty();
			factorioExecutable = Optional.empty();
		}

		LOGGER.info("Read Data Zip: {}", fileDataRawDumpZip.getAbsolutePath());
		LuaTable lua = null;

		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(fileDataRawDumpZip))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.getName().equals(fileDataRawDump.getName())) {
					lua = new LuaTable(new JSONObject(new JSONTokener(zis)));
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		if (lua == null) {
			LOGGER.error("{} not found in data zip! {}", fileDataRawDump.getName(),
					fileDataRawDumpZip.getAbsolutePath());
			System.exit(-1);
		}

		TypeHierarchy typeHiearchy = new TypeHierarchy(Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("type-hiearchy.json")));
		JSONObject excludeDataJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("exclude-data.json"));
		JSONObject includeDataJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("include-data.json"));
		JSONObject wikiNamingJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("wiki-naming.json"));
		dataTable = new DataTable(typeHiearchy, lua, excludeDataJson, includeDataJson, wikiNamingJson);
		dataTable.setData(this);
	}
}
