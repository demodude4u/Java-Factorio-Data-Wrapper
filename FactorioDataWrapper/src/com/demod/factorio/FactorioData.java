package com.demod.factorio;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
	private static final String DATA_ZIP_ENTRY_STAMP = "stamp.txt";
	private static final String DATA_ZIP_ENTRY_VERSION = "version.txt";

	@SuppressWarnings("resource")
	private static synchronized boolean factorioDataDump(File factorioInstall, Optional<File> factorioExecutableOverride, File fileConfig,
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

	private final File dataZip;

	private boolean initialized = false;
	private DataTable dataTable = null;
	private String version = null;

	public FactorioData(File dataZip) {
		this.dataZip = dataZip;
	}

	private static String fileMD5(File file) {
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

	private static Optional<String> generateStamp(File factorioInstall, Optional<File> factorioExecutableOverride, File folderMods) {

		Optional<String> version = getVersionFromInstall(factorioInstall, factorioExecutableOverride);
		if (version.isEmpty()) {
			return Optional.empty();
		}

		try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
			pw.println("Factorio Version: " + version.get());
			pw.println("mod-list.json MD5: " + fileMD5(new File(folderMods, "mod-list.json")));
			pw.println("mod-settings.dat MD5: " + fileMD5(new File(folderMods, "mod-settings.dat")));
			pw.println("Mod Zips:");
			File[] files = folderMods.listFiles();
			Arrays.sort(files, Comparator.comparing(File::getName));
			for (File file : files) {
				if (file.getName().endsWith(".zip")) {
					pw.println("\t" + file.getName());
				}
			}
			pw.flush();
			return Optional.of(sw.toString());

		} catch (IOException e) {
			LOGGER.error("Failed to generate stamp for Factorio data zip", e);
			System.exit(-1);
			return Optional.empty();
		}
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
			return new File(factorioInstall, EXEC_MACOS);
		} else if (osName.contains("nix") || osName.contains("nux")) {
			return new File(factorioInstall, EXEC_LINUX);
		} else {
			LOGGER.error("Unsupported operating system: {}", osName);
			System.exit(-1);
			return null;
		}
	}

	public static boolean buildDataZip(File targetDataZip, File folderData, File folderMods, File factorioInstall, Optional<File> factorioExecutableOverride, boolean forceBuild) {

		folderData.mkdirs();

		File folderScriptOutput = new File(folderData, "script-output");
		File fileDataRawDump = new File(folderScriptOutput, "data-raw-dump.json");

		File fileModList = new File(folderMods, "mod-list.json");
		if (!fileModList.exists()) {
			try {
				Files.copy(FactorioData.class.getClassLoader().getResourceAsStream("mod-list.json"), fileModList.toPath());
			} catch (IOException e) {
				LOGGER.error("Failed to copy mod-list.json to mods folder", e);
				return false;
			}
		}

		File fileConfig = new File(folderData, "config.ini");
		try (PrintWriter pw = new PrintWriter(fileConfig)) {
			pw.println("[path]");
			pw.println("read-data=" + new File(factorioInstall, "data").getAbsolutePath());
			pw.println("write-data=" + folderData.getAbsolutePath());
		} catch (IOException e) {
			LOGGER.error("Failed to write config.ini", e);
			return false;
		}

		// Prevent unnecessary changes so github doesn't get confused
		File fileModSettings = new File(folderMods, "mod-settings.dat");
		fileModList.setReadOnly();
		fileModSettings.setReadOnly();

		Optional<String> stamp = generateStamp(factorioInstall, factorioExecutableOverride, folderMods);
		if (stamp.isEmpty()) {
			LOGGER.error("Failed to generate stamp for Factorio data zip.");
			return false;
		}

		boolean needBuild = forceBuild;

		if (!targetDataZip.exists()) {
			needBuild = true;
		
		} else {
			try (ZipFile zipFile = new ZipFile(targetDataZip)) {
				ZipEntry entryStamp = zipFile.getEntry(DATA_ZIP_ENTRY_STAMP);
				if (entryStamp == null) {
					needBuild = true;
				} else {
					try (InputStream is = zipFile.getInputStream(entryStamp)) {
						byte[] existingStampBytes = is.readAllBytes();
						String existingStamp = new String(existingStampBytes);
						if (!existingStamp.equals(stamp.get())) {
							needBuild = true;
						}
					} catch (IOException e) {
						LOGGER.error("Error reading stamp from zip file", e);
						needBuild = true;
					}
				}
			} catch (IOException e) {
				LOGGER.error("Error opening data zip file", e);
				needBuild = true;
			}
		}

		if (needBuild) {
			LOGGER.info("Starting data dump...");

			if (!factorioDataDump(factorioInstall, factorioExecutableOverride, fileConfig, folderMods)) {
				LOGGER.error("Failed to dump data from Factorio install.");
				return false;
			}

			Optional<String> version = getVersionFromInstall(factorioInstall, factorioExecutableOverride);
			if (version.isEmpty()) {
				LOGGER.error("Failed to get Factorio version from install.");
				return false;
			}

			try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetDataZip))) {
				zos.putNextEntry(new ZipEntry(DATA_ZIP_ENTRY_DUMP));
				zos.write(Files.readAllBytes(fileDataRawDump.toPath()));
				zos.closeEntry();

				zos.putNextEntry(new ZipEntry(DATA_ZIP_ENTRY_STAMP));
				zos.write(stamp.get().getBytes());
				zos.closeEntry();

				zos.putNextEntry(new ZipEntry(DATA_ZIP_ENTRY_VERSION));
				zos.write(version.get().getBytes());
				zos.closeEntry();
			} catch (IOException e) {
				LOGGER.error("Failed to write data zip", e);
				return false;
			}
			LOGGER.info("Write Data Zip: {}", targetDataZip.getAbsolutePath());
		}

		return true;
	}

	public boolean initialize(boolean wikiMode) {
		if (initialized) {
			return true;
		}
		initialized = true;

		LOGGER.info("Read Data Zip: {}", dataZip.getAbsolutePath());
		LuaTable lua = null;

		try (ZipFile zipFile = new ZipFile(dataZip)) {
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
				LOGGER.info("Factorio Version: {}", version);
			} catch (IOException e) {
				LOGGER.error("Failed to read version.txt from data zip", e);
				return false;
			}
		} catch (IOException e) {
			LOGGER.error("Failed to open data zip", e);
			return false;
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
