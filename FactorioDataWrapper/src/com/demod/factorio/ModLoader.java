package com.demod.factorio;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONException;

import com.demod.factorio.ModInfo.Dependency;
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
		private final Map<String, byte[]> files = new LinkedHashMap<>();
		private ModInfo info;

		private volatile Optional<String> lastResourceFolder = Optional.empty();

		public ModZip(File zipFile) throws FileNotFoundException, IOException {
			String prefix = zipFile.getName().substring(0, zipFile.getName().length() - 4);
			try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
				ZipEntry entry;
				while ((entry = zis.getNextEntry()) != null) {
					byte[] data = new byte[(int) entry.getSize()];
					ByteStreams.readFully(zis, data);
					files.put(entry.getName().replace(prefix, ""), data);
					System.out.println("ZIP ENTRY " + zipFile.getName() + ": " + entry.getName());// XXX
				}
			}

			try (ByteArrayInputStream bais = new ByteArrayInputStream(files.get("/info.json"))) {
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

	private Map<String, Integer> getDepths() {
		Map<String, Integer> depths = new LinkedHashMap<>();
		Set<String> visited = new HashSet<>();

		for (Mod mod : this.mods.values()) {
			populateDepth(mod, depths, visited);
		}

		return depths;
	}

	private void populateDepth(Mod mod, Map<String, Integer> depths, Set<String> visited) {
		ModInfo modInfo = mod.getInfo();
		String modName = modInfo.getName();
		if (depths.containsKey(modName)) {
			return;
		}
		if (visited.contains(modName)) {
			return;
		}
		visited.add(modName);

		int depth = 0;
		for (Dependency dependency : modInfo.getDependencies()) {
			String dependencyName = dependency.getName();
			Optional<Mod> maybeDependencyMod = getMod(dependencyName);
			if (maybeDependencyMod.isPresent()) {
				Mod dependencyMod = maybeDependencyMod.get();
				populateDepth(dependencyMod, depths, visited);
				Integer dependencyDepth = depths.getOrDefault(dependencyMod.getInfo().getName(), 0);
				depth = Math.max(depth, dependencyDepth + 1);
			}
		}

		depths.put(modName, depth);
		visited.remove(modName);
	}

	public List<Mod> getModsInLoadOrder() {
		Map<String, Integer> depths = getDepths();
		Comparator<Mod> comparator = Comparator.comparing((Mod mod) -> depths.get(mod.getInfo().getName()));
		List<Mod> result = mods.values().stream().sorted(comparator).collect(Collectors.toList());
		result.remove(mods.get("core"));
		result.remove(mods.get("base"));
		result.add(0, mods.get("core"));
		result.add(1, mods.get("base"));
		return result;
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
