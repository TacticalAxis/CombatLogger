package io.github.tacticalaxis.combatlogger;


import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ConfigurationManager {

    private static final ConfigurationManager instance = new ConfigurationManager();
    private final ArrayList<String> ymlFiles = new ArrayList<>();
    private final HashMap<String, FileConfiguration> configs = new HashMap<>();

    public static String CONFIG_MAIN = "config.yml";
    public static String[] configurations = {CONFIG_MAIN};

    private ConfigurationManager() {
    }

    public static ConfigurationManager getInstance() {
        return instance;
    }

    // test if config exists, if not, create files
    private static File create(String configName) {
        CombatLogger main = CombatLogger.getInstance();
        try {
            if (!main.getDataFolder().exists()) {
                boolean success = main.getDataFolder().mkdirs();
                if (!success) {
                    System.out.println("Configuration files could not be created!");
                    Bukkit.shutdown();
                }
            }
            File file = new File(main.getDataFolder(), configName);
            if (!file.exists()) {
                main.getLogger().info(configName + " not found, creating!");
                main.saveResource(configName, true);
            }
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void setupConfiguration() {
        for (String file : configurations) {
            File configFile = create(file);
            if (configFile != null) {
                FileConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
                ymlFiles.add(file);
                configs.put(file, configuration);
            } else {
                System.out.println("Error loading " + file);
            }
        }
    }

    // MAIN CONFIGURATION
    public FileConfiguration getConfiguration(String name) {
        return configs.get(name);
    }

    public void saveConfiguration(String name) {
        try {
            configs.get(name).save(new File(CombatLogger.getInstance().getDataFolder(), name));
        } catch (IOException e) {
            Bukkit.getServer().getLogger().severe("Could not save " + name + "!");
        }
    }

    public void reloadConfiguration() {
        for (String ymlFile : ymlFiles) {
            try {
                configs.get(ymlFile).load(new File(CombatLogger.getInstance().getDataFolder(), ymlFile));
            } catch (Exception ignored) {
            }
        }
        setupConfiguration();
    }
}