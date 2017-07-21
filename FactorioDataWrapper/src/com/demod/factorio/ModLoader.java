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
import java.util.Optional;
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

	private final Map<String, Mod> mods = new LinkedHashMap<>();

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
						if (!order.contains(depName)) {
							missingDep = true;
							break;
						}
					} else if (!dependency.isOptional()) {
						throw new InternalError("MISSING DEPENDENCY FOR " + mod.getInfo().getName() + " : " + depName);
					}
				}
				if (!missingDep) {
					order.add(mod.getInfo().getName());
					iter.remove();
				}
			}
		}
		return order.stream().map(this::getMod).map(Optional::get).collect(Collectors.toList());
	}

	public void loadFolder(File folder) throws IOException {
		for (File file : folder.listFiles()) {
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
