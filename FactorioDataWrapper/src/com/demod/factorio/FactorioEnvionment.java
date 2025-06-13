package com.demod.factorio;

import java.io.File;

import org.json.JSONObject;

public class FactorioEnvionment {

    private final FactorioData factorioData;
    private final ModLoader modLoader;

    public FactorioEnvionment(FactorioData factorioData, ModLoader modLoader) {
        this.factorioData = factorioData;
        this.modLoader = modLoader;
    }

    public static FactorioEnvionment buildAndInitialize(JSONObject config, boolean wikiMode) {
        File folder = new File(config.optString("temp", "temp"));
        File factorioInstall = new File(config.getString("install"));
        File factorioExecutable = new File(config.getString("executable"));

        return buildAndInitialize(folder, factorioInstall, factorioExecutable, wikiMode);
    }

    //Basic setup - good for temporary builds of vanilla
    public static FactorioEnvionment buildAndInitialize(File folder, File factorioInstall, File factorioExecutable, boolean wikiMode) {
        
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
        
        return new FactorioEnvionment(factorioData, modLoader);
    }

    public FactorioData getFactorioData() {
        return factorioData;
    }

    public ModLoader getModLoader() {
        return modLoader;
    }
}
