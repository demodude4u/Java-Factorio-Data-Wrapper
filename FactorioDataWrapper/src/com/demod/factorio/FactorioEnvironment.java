package com.demod.factorio;

import java.io.File;
import java.util.Optional;

import org.json.JSONObject;

public class FactorioEnvironment {

    private final File factorioInstall;
    private final Optional<File> factorioExecutableOverride;
    private final FactorioData factorioData;
    private final ModLoader modLoader;
    private final String version;

    public FactorioEnvironment(File factorioInstall, Optional<File> factorioExecutableOverride, FactorioData factorioData, ModLoader modLoader, String version) {
        this.factorioInstall = factorioInstall;
        this.factorioExecutableOverride = factorioExecutableOverride;
        this.factorioData = factorioData;
        this.modLoader = modLoader;
        this.version = version;
    }

    public static FactorioEnvironment buildAndInitialize(JSONObject config, boolean wikiMode) {
        File folder = new File(config.optString("temp", "temp"));
        File factorioInstall = new File(config.getString("install"));
        Optional<File> factorioExecutableOverride = Optional.of(config.optString("executable", null)).map(path -> new File(factorioInstall, path));

        return buildAndInitialize(folder, factorioInstall, factorioExecutableOverride, wikiMode);
    }

    //Basic setup - good for temporary builds of vanilla
    public static FactorioEnvironment buildAndInitialize(File folder, File factorioInstall, Optional<File> factorioExecutableOverride, boolean wikiMode) {
        
        folder.mkdirs();
        File folderData = new File(folder, "data");
        File folderMods = new File(folder, "mods");

        File dataZip = new File(folder, "factorio-data.zip");
        if (!FactorioData.buildDataZip(dataZip, folderData, folderMods, factorioInstall, factorioExecutableOverride, false)) {
            throw new RuntimeException("Failed to build Factorio data zip");
        }

        FactorioData factorioData = new FactorioData(dataZip);
        factorioData.initialize(wikiMode);
        
        ModLoader modLoader = new ModLoader(factorioInstall, folderMods);

        String version = modLoader.getMod("base").get().getInfo().getVersion();

        return new FactorioEnvironment(factorioInstall, factorioExecutableOverride, factorioData, modLoader, version);
    }

    public File getFactorioInstall() {
        return factorioInstall;
    }

    public Optional<File> getFactorioExecutableOverride() {
        return factorioExecutableOverride;
    }

    public FactorioData getFactorioData() {
        return factorioData;
    }

    public ModLoader getModLoader() {
        return modLoader;
    }

    public String getVersion() {
        return version;
    }
}
