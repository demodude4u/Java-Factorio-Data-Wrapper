package com.demod.factorio;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

public class ModLoader {
	public interface Mod {
		public ModInfo getInfo();

		public Optional<InputStream> getResource(String path) throws IOException;
	}

	public static class ModFolder implements Mod {
		private final ModInfo info;
		private final File folder;

		public ModFolder(File folder) throws JSONException, FileNotFoundException, IOException {
			this.folder = folder;
			try (FileInputStream fis = new FileInputStream(new File(folder, "info.json"))) {
				info = new ModInfo(Utils.readJsonFromStream(fis));
			}
		}

		@Override
		public ModInfo getInfo() {
			return info;
		}

		@Override
		public Optional<InputStream> getResource(String path) throws FileNotFoundException {
			File file = new File(folder, path);
			if (file.isFile() && file.exists()) {
				return Optional.of(new FileInputStream(file));
			} else {
				return Optional.empty();
			}
		}
	}

	public static class ModZip implements Mod {
		private static String stripDirectoryName(String name) throws RuntimeException {
			int firstSlash = name.indexOf('/');
			if (firstSlash == -1) {
				return name;
			}
			return name.substring(firstSlash);
		}

		private final Map<String, byte[]> files = new LinkedHashMap<>();

		private ModInfo info;

		private volatile Optional<String> lastResourceFolder = Optional.empty();

		public ModZip(File file) throws FileNotFoundException, IOException {
			try (ZipFile zipFile = new ZipFile(file)) {
				zipFile.stream().forEach(entry -> {
					String name = stripDirectoryName(entry.getName());
					if (name.equals(entry.getName())) {
						throw new RuntimeException(file.getName() + " missing the inner directory for the mod files.");
					}

					try (InputStream inputStream = zipFile.getInputStream(entry)) {
						files.put(name, ByteStreams.toByteArray(inputStream));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			} catch (Exception e) {
				LOGGER.debug("MODZIP " + file.getAbsolutePath());
				throw e;
			}

			byte[] buf = files.get("/info.json");
			if (buf == null) {
				throw new FileNotFoundException(file.getName() + " does not contain info.json");
			}
			try (ByteArrayInputStream bais = new ByteArrayInputStream(buf)) {
				info = new ModInfo(Utils.readJsonFromStream(bais));
			}
		}

		@Override
		public ModInfo getInfo() {
			return info;
		}

		@Override
		public Optional<InputStream> getResource(String path) {
			path = path.replace("\\", "/");
			path = path.replace("//", "/");
			Optional<byte[]> resource = Optional.ofNullable(files.get(path));

			if (!resource.isPresent() && lastResourceFolder.isPresent()) {
				path = lastResourceFolder.get() + path;
				LOGGER.warn("FRANKENPATH: {}", path);
				resource = Optional.ofNullable(files.get(path));
			}

			lastResourceFolder = Optional.of(path.substring(0, path.lastIndexOf("/")));

			return resource.map(ByteArrayInputStream::new);
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(ModLoader.class);

	private final Map<String, Mod> mods = new LinkedHashMap<>();

	public ModLoader(File factorioInstall, File folderMods) {
		loadFolder(new File(factorioInstall, "data"));
		loadFolder(folderMods);
	}

	public Optional<Mod> getMod(String name) {
		return Optional.ofNullable(mods.get(name));
	}

	public Map<String, Mod> getMods() {
		return mods;
	}

	private void loadFolder(File folder) {
		File[] files = folder.listFiles();
		Objects.requireNonNull(files, folder.toString());
		for (File file : files) {
			try {
				if (file.isDirectory()) {
					if (new File(file, "info.json").exists()) {
						ModFolder mod = new ModFolder(file);
						mods.put(mod.getInfo().getName(), mod);
					} else {
						loadFolder(file);
					}
				} else if (file.getName().endsWith(".zip")) {
					ModZip mod = new ModZip(file);
					mods.put(mod.getInfo().getName(), mod);
				}
			} catch (IOException e) {
				LOGGER.error("Error loading mod from file: {}", file.getAbsolutePath(), e);
				System.exit(-1);
			}
		}
	}

	public Optional<InputStream> getModResource(String path) {
		String firstSegment = path.split("\\/")[0];
		if (firstSegment.length() < 4) {
			throw new IllegalArgumentException("Path is not valid: \"" + path + "\"");
		}
		String modName = firstSegment.substring(2, firstSegment.length() - 2);
		Optional<Mod> mod = getMod(modName);
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

	public BufferedImage getModImage(String path) {
		try {
			try (InputStream is = getModResource(path).get()) {
				BufferedImage image = loadImage(is);
				return image;
			}
		} catch (Exception e) {
			LOGGER.error("MISSING MOD IMAGE: " + path);
			e.printStackTrace();
			System.exit(-1);
			return null;
		}
	}

	private BufferedImage loadImage(InputStream is) throws IOException {
		BufferedImage image = ImageIO.read(is);
		if (image.getType() == BufferedImage.TYPE_CUSTOM) {
			image = convertCustomImage(image);
		}
		return image;
	}

	private BufferedImage convertCustomImage(BufferedImage image) {
		BufferedImage ret = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = ret.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return ret;
	}
}
