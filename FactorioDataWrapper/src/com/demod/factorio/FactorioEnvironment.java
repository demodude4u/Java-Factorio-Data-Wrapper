package com.demod.factorio;

import java.io.File;

import org.json.JSONObject;

public class FactorioEnvironment {

    private final File factorioInstall;
    private final File factorioExecutable;
    private final FactorioData factorioData;
    private final ModLoader modLoader;
    private final String version;

    public FactorioEnvironment(File factorioInstall, File factorioExecutable, FactorioData factorioData, ModLoader modLoader, String version) {
        this.factorioInstall = factorioInstall;
        this.factorioExecutable = factorioExecutable;
        this.factorioData = factorioData;
        this.modLoader = modLoader;
        this.version = version;
    }

    public static FactorioEnvironment buildAndInitialize(JSONObject config, boolean wikiMode) {
        File folder = new File(config.optString("temp", "temp"));
        File factorioInstall = new File(config.getString("install"));
        File factorioExecutable = new File(config.getString("executable"));

        return buildAndInitialize(folder, factorioInstall, factorioExecutable, wikiMode);
    }

    //Basic setup - good for temporary builds of vanilla
    public static FactorioEnvironment buildAndInitialize(File folder, File factorioInstall, File factorioExecutable, boolean wikiMode) {
        
        folder.mkdirs();
        File folderData = new File(folder, "data");
        File folderMods = new File(folder, "mods");

        File dataZip = new File(folder, "factorio-data.zip");
        if (!FactorioData.buildDataZip(dataZip, folderData, folderMods, factorioInstall, factorioExecutable, false)) {
            throw new RuntimeException("Failed to build Factorio data zip");
        }

        FactorioData factorioData = new FactorioData(dataZip);
        factorioData.initialize(wikiMode);
        
        ModLoader modLoader = new ModLoader(factorioInstall, folderMods);

        String version = modLoader.getMod("base").get().getInfo().getVersion();

        return new FactorioEnvironment(factorioInstall, factorioExecutable, factorioData, modLoader, version);
    }

    public File getFactorioInstall() {
        return factorioInstall;
    }

    public File getFactorioExecutable() {
        return factorioExecutable;
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
