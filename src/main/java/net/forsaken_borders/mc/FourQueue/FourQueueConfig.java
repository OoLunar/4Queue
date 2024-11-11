package net.forsaken_borders.mc.FourQueue;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public final class FourQueueConfig {
    private String _mainHostname = "";
    private String _limboHostname = "";
    private int _movePlayerDelay = 2500;
    private int _quickMovePlayerDelay = 100;

    public FourQueueConfig() {
    }

    public static FourQueueConfig Load(Path relativePath, Logger logger) throws IOException {
        File configDir = relativePath.toFile();
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new IOException("Could not create config directory");
        } else if (!configDir.isDirectory()) {
            throw new IOException(configDir.getAbsolutePath() + " is not a directory");
        }

        Yaml yaml = new Yaml(new CustomClassLoaderConstructor(FourQueueConfig.class.getClassLoader()));
        File configFile = relativePath.resolve("config.yml").toFile();
        if (!configFile.exists()) {
            logger.info("Creating default config file...");
            return CreateDefaultConfig(yaml, configFile);
        } else if (!configFile.isFile()) {
            throw new IOException(configFile.getAbsolutePath() + " is not a file");
        }

        return yaml.loadAs(new FileInputStream(configFile), FourQueueConfig.class);
    }

    private static FourQueueConfig CreateDefaultConfig(Yaml yaml, File configFile) throws IOException {
        if (!configFile.exists() && !configFile.createNewFile()) {
            throw new IOException("Could not create config file");
        }

        FourQueueConfig defaultConfig = new FourQueueConfig();
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(yaml.dumpAsMap(defaultConfig));
        }

        return defaultConfig;
    }

    public String getMainHostname() {
        return _mainHostname;
    }

    public void setMainHostname(String mainHostname) {
        _mainHostname = mainHostname;
    }

    public String getLimboHostname() {
        return _limboHostname;
    }

    public void setLimboHostname(String limboHostname) {
        _limboHostname = limboHostname;
    }

    public int getMovePlayerDelay() {
        return _movePlayerDelay;
    }

    public void setMovePlayerDelay(int movePlayerDelay) {
        _movePlayerDelay = movePlayerDelay;
    }

    public int getQuickMovePlayerDelay() {
        return _quickMovePlayerDelay;
    }

    public void setQuickMovePlayerDelay(int quickMovePlayerDelay) {
        _quickMovePlayerDelay = quickMovePlayerDelay;
    }
}
