package com.demod.factorio;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.json.JSONException;

import com.demod.factorio.ModInfo.Dependency;
import com.demod.factorio.ModInfo.DependencyType;

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
						System.out.println("ZIP ENTRY " + file.getName() + ": " + entry.getName());// XXX
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
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
			Optional<byte[]> resource = Optional.ofNullable(files.get(path));

			if (!resource.isPresent() && lastResourceFolder.isPresent()) {
				path = lastResourceFolder.get() + path;
				System.out.println("FRANKENPATH: " + path);
				resource = Optional.ofNullable(files.get(path));
			}

			lastResourceFolder = Optional.of(path.substring(0, path.lastIndexOf("/")));

			return resource.map(ByteArrayInputStream::new);
		}
	}

	private final Set<String> modExclude;

	private final Map<String, Mod> mods = new LinkedHashMap<>();

	public ModLoader(Set<String> modExclude) {
		this.modExclude = modExclude;
	}

	public Optional<Mod> getMod(String name) {
		return Optional.ofNullable(mods.get(name));
	}

	public List<Mod> getModsInLoadOrder() {
		LinkedList<Mod> work = new LinkedList<>();
		mods.values().stream().sorted((m1, m2) -> m1.getInfo().getName().compareTo(m2.getInfo().getName()))
				.forEach(n -> work.add(n));
		List<String> order = new ArrayList<>();
		while (!work.isEmpty()) {
			Iterator<Mod> iter = work.iterator();
			while (iter.hasNext()) {
				Mod mod = iter.next();
				boolean missingDep = false;
				for (Dependency dependency : mod.getInfo().getDependencies()) {
					String depName = dependency.getName();
					if (getMod(depName).isPresent()) {
						if (!order.contains(depName) && dependency.getType() != DependencyType.DOES_NOT_AFFECT_LOAD_ORDER) {
							missingDep = true;
							break;
						}
					} else if (!dependency.isOptional() && dependency.getType() != DependencyType.INCOMPATIBLE) {
						throw new InternalError("MISSING DEPENDENCY FOR " + mod.getInfo().getName() + " : " + depName);
					}
				}
				if (!missingDep) {
					order.add(mod.getInfo().getName());
					iter.remove();
				}
			}
		}
		// Make sure core loads first
		if (order.contains("core") && order.indexOf("core") != 0) {
			order.remove("core");
			order.add(0, "core");
		}
		return order.stream().map(this::getMod).map(Optional::get).collect(Collectors.toList());
	}

	public void loadFolder(File folder) throws IOException {
		File[] files = folder.listFiles();
		Objects.requireNonNull(files, folder.toString());
		for (File file : files) {
			if (modExclude.contains(file.getName())) {
				continue;
			}
			if (file.isDirectory()) {
				if (new File(file, "info.json").exists()) {
					ModFolder mod = new ModFolder(file);
					mods.put(mod.getInfo().getName(), mod);
					System.out.println("MOD FOLDER LOADED: " + file.getName());
				} else {
					loadFolder(file);
				}
			} else if (file.getName().endsWith(".zip")) {
				ModZip mod = new ModZip(file);
				mods.put(mod.getInfo().getName(), mod);
				System.out.println("MOD ZIP LOADED: " + file.getName());
			}
		}
	}
}
