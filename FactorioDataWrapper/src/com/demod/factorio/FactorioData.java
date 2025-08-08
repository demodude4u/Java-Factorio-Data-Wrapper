package com.demod.factorio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.demod.factorio.fakelua.LuaTable;

public class FactorioData {
	private static final Logger LOGGER = LoggerFactory.getLogger(FactorioData.class);

	public static final String EXEC_WINDOWS = "bin\\x64\\factorio.exe";
	public static final String EXEC_LINUX = "bin/x64/factorio";
	public static final String EXEC_MACOS = "MacOS/factorio";
	
	private static final String DATA_ZIP_ENTRY_DUMP = "dump.json";
	private static final String DATA_ZIP_ENTRY_VERSION = "version.txt";

	@SuppressWarnings("resource")
	private static synchronized boolean executeFactorioDumpData(File factorioInstall, Optional<File> factorioExecutableOverride, File fileConfig,
			File folderMods) {

		File factorioExecutable;
		if (factorioExecutableOverride.isPresent()) {
			factorioExecutable = factorioExecutableOverride.get();
		} else {
			factorioExecutable = getFactorioExecutable(factorioInstall);
		}

		try {
			ProcessBuilder pb = new ProcessBuilder(factorioExecutable.getAbsolutePath(), "--config",
					fileConfig.getAbsolutePath(), "--mod-directory", folderMods.getAbsolutePath(), "--dump-data");
			pb.directory(factorioInstall);

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
			LOGGER.error("\t install: " + factorioInstall.getAbsolutePath());
			LOGGER.error("\t executable: " + factorioExecutable.getAbsolutePath());
			LOGGER.error("\t config: {}", fileConfig.getAbsolutePath());
			LOGGER.error("\t mods: " + folderMods.getAbsolutePath());
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public static Optional<String> getVersionFromInstall(File factorioInstall, Optional<File> factorioExecutableOverride) {
		
		File factorioExecutable;
		if (factorioExecutableOverride.isPresent()) {
			factorioExecutable = factorioExecutableOverride.get();
		} else {
			factorioExecutable = getFactorioExecutable(factorioInstall);
		}

		try {
			ProcessBuilder pb = new ProcessBuilder(factorioExecutable.getAbsolutePath(), "--version");
			Process process = pb.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (line.startsWith("Version:")) {
						String[] parts = line.split(" ");
						if (parts.length > 1) {
							return Optional.of(parts[1]); // Extract version number
						}
					}
				}

			} finally {
				int exitCode = process.waitFor();
				if (exitCode != 0) {
					throw new IOException("Factorio command failed with exit code: " + exitCode);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to retrieve Factorio version from executable: {}", factorioExecutable.getAbsolutePath(), e);
			System.exit(-1);
			return Optional.empty();
		}
		return Optional.empty();
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

	private final boolean hasZip;
	private final File fileDataZip;
	private final File fileDataJson;

	private boolean initialized = false;
	private DataTable dataTable = null;
	private String version = null;

	public FactorioData(File fileDataZip) {
		this.hasZip = true;
		this.fileDataZip = fileDataZip;
		fileDataJson = null;
	}

	public FactorioData(File fileDataJson, File fileVersion) {
		this.hasZip = true;
		this.fileDataZip = null;
		this.fileDataJson = fileDataJson;
		try {
			this.version = Files.readString(fileVersion.toPath()).trim();
		} catch (IOException e) {
			LOGGER.error("Failed to read version file: {}", fileVersion.getAbsolutePath(), e);
		}
	}

	public FactorioData(File fileDataJson, String version) {
		this.hasZip = false;
		this.fileDataZip = null;
		this.fileDataJson = fileDataJson;
		this.version = version;
	}

	public DataTable getTable() {
		return dataTable;
	}

	public String getVersion() {
		return version;
	}

	public static File getFactorioExecutable(File factorioInstall) {
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("win")) {
			return new File(factorioInstall, EXEC_WINDOWS);
		} else if (osName.contains("mac")) {
			File steamAppBundle = new File(factorioInstall, "factorio.app/Contents/MacOS/factorio");
			if (steamAppBundle.exists()) {
				return steamAppBundle;
			}
			return new File(factorioInstall, EXEC_MACOS);
		} else if (osName.contains("nix") || osName.contains("nux")) {
			return new File(factorioInstall, EXEC_LINUX);
		} else {
			LOGGER.error("Unsupported operating system: {}", osName);
			System.exit(-1);
			return null;
		}
	}

	public static boolean generateDumpAndVersion(File folderData, File folderMods, File factorioInstall, Optional<File> factorioExecutableOverride) {
		folderData.mkdirs();

		File fileModSettings = new File(folderMods, "mod-settings.dat");
		File folderScriptOutput = new File(folderData, "script-output");
		File fileDataRawDump = new File(folderScriptOutput, "data-raw-dump.json");
		File fileVersion = new File(folderScriptOutput, "version.txt");
		File fileModList = new File(folderMods, "mod-list.json");
		File fileConfig = new File(folderData, "config.ini");
		
		if (!fileModList.exists()) {
			try {
				Files.copy(FactorioData.class.getClassLoader().getResourceAsStream("mod-list.json"), fileModList.toPath());
			} catch (IOException e) {
				LOGGER.error("Failed to copy mod-list.json to mods folder", e);
				return false;
			}
		}

		try (PrintWriter pw = new PrintWriter(fileConfig)) {
			pw.println("[path]");
			pw.println("read-data=" + new File(factorioInstall, "data").getAbsolutePath());
			pw.println("write-data=" + folderData.getAbsolutePath());
		} catch (IOException e) {
			LOGGER.error("Failed to write config.ini", e);
			return false;
		}

		if (fileDataRawDump.exists()) {
			if (!fileDataRawDump.delete()) {
				LOGGER.error("Failed to delete old data dump file: {}", fileDataRawDump.getAbsolutePath());
				return false;
			}
		}

		LOGGER.info("Starting data dump...");

		// Prevent unnecessary changes so github doesn't get confused
		fileModList.setReadOnly();
		fileModSettings.setReadOnly();

		if (!executeFactorioDumpData(factorioInstall, factorioExecutableOverride, fileConfig, folderMods)) {
			LOGGER.error("Failed to dump data from Factorio install.");
			return false;
		}

		if (!fileDataRawDump.exists()) {
			LOGGER.error("Data dump file does not exist: {}", fileDataRawDump.getAbsolutePath());
			return false;
		}

		Optional<String> version = getVersionFromInstall(factorioInstall, factorioExecutableOverride);
		if (version.isEmpty()) {
			LOGGER.error("Failed to get Factorio version from install.");
			return false;
		}

		try {
			Files.writeString(fileVersion.toPath(), version.get());
		} catch (IOException e) {
			LOGGER.error("Failed to write version.txt", e);
			return false;
		}

		return true;
	}

	public boolean initialize(boolean wikiMode) {
		if (initialized) {
			return true;
		}
		initialized = true;

		LuaTable lua = null;
		
		if (hasZip) {
			LOGGER.info("Read Data Zip: {}", fileDataZip.getAbsolutePath());

			try (ZipFile zipFile = new ZipFile(fileDataZip)) {
				ZipEntry entryDump = zipFile.getEntry(DATA_ZIP_ENTRY_DUMP);
				if (entryDump == null) {
					LOGGER.error("Data zip does not contain dump.json entry!");
					return false;
				}
				try (InputStream is = zipFile.getInputStream(entryDump)) {
					lua = new LuaTable(new JSONObject(new JSONTokener(is)));
				} catch (IOException e) {
					LOGGER.error("Failed to read dump.json from data zip", e);
					return false;
				}

				ZipEntry entryVersion = zipFile.getEntry(DATA_ZIP_ENTRY_VERSION);
				if (entryVersion == null) {
					LOGGER.error("Data zip does not contain version.txt entry!");
					return false;
				}
				try (InputStream is = zipFile.getInputStream(entryVersion)) {
					version = new String(is.readAllBytes());
					// LOGGER.info("Factorio Version: {}", version);
				} catch (IOException e) {
					LOGGER.error("Failed to read version.txt from data zip", e);
					return false;
				}
			} catch (IOException e) {
				LOGGER.error("Failed to open data zip", e);
				return false;
			}
		
		} else {
			LOGGER.info("Read Data JSON: {}", fileDataJson.getAbsolutePath());

			try (FileInputStream fis = new FileInputStream(fileDataJson)) {
				lua = new LuaTable(new JSONObject(new JSONTokener(fis)));
			} catch (IOException e) {
				LOGGER.error("Failed to read {}", fileDataJson.getAbsolutePath(), e);
				return false;
			}
		}

		TypeHierarchy typeHiearchy = new TypeHierarchy(Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("type-hiearchy.json")));
		JSONObject excludeDataJson;
		JSONObject includeDataJson;
		if (wikiMode) {
			excludeDataJson = Utils
					.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("wiki-exclude-data.json"));
			includeDataJson = Utils
					.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("wiki-include-data.json"));
		} else {
			excludeDataJson = new JSONObject();
			includeDataJson = new JSONObject();
		}
		JSONObject wikiNamingJson = Utils
				.readJsonFromStream(FactorioData.class.getClassLoader().getResourceAsStream("wiki-naming.json"));
		dataTable = new DataTable(typeHiearchy, lua, excludeDataJson, includeDataJson, wikiNamingJson);
		dataTable.setData(this);

		return true;
	}
}
