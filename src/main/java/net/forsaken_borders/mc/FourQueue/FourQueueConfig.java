package net.forsaken_borders.mc.FourQueue;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

public record FourQueueConfig(
        String MainHostname,
        String LimboHostname,
        long MovePlayerDelay,
        long SuccessfulMoveDelay
) {
    public static FourQueueConfig Load(File configFile, Logger logger) throws IOException {
        Yaml yaml = new Yaml();
        if (!configFile.exists()) {
            logger.info("Creating default config file...");
            return CreateDefaultConfig(yaml, configFile);
        } else if (!configFile.isFile()) {
            throw new IOException(configFile.getAbsolutePath() + " is not a file");
        }

        // Read the file
        String contents = Files.readString(configFile.toPath());
        if (contents.isBlank()) {
            logger.info("Creating default config file...");
            return CreateDefaultConfig(yaml, configFile);
        }

        return yaml.loadAs(contents, FourQueueConfig.class);
    }

    private static FourQueueConfig CreateDefaultConfig(Yaml yaml, File configFile) throws IOException {
        if (!configFile.exists()) {
            if (!configFile.createNewFile()) {
                throw new IOException("Could not create config file");
            }
        }

        // Dump the default config
        FourQueueConfig defaultConfig = new FourQueueConfig("", "", 5000, 500);
        try (FileWriter writer = new FileWriter(configFile)) {
            yaml.dump(defaultConfig, writer);
        }

        return defaultConfig;
    }
}
