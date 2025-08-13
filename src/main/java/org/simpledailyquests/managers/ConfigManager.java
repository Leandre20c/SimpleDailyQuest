package org.simpledailyquests.managers;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.models.Quest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class ConfigManager {

    private final SimpleDailyQuests plugin;
    private FileConfiguration config;
    private FileConfiguration messagesConfig;
    private final Map<Quest.QuestRarity, FileConfiguration> questConfigs;
    private final Map<Quest.QuestRarity, Integer> resetHours;
    private final Map<Quest.QuestRarity, Integer> maxActiveQuests;
    private final Map<Quest.QuestRarity, Double> rewardsMultiplier;

    // Fichiers de configuration
    private File configFile;
    private File messagesFile;

    public ConfigManager(SimpleDailyQuests plugin) {
        this.plugin = plugin;
        this.questConfigs = new HashMap<>();
        this.resetHours = new HashMap<>();
        this.maxActiveQuests = new HashMap<>();
        this.rewardsMultiplier = new HashMap<>();
    }

    /**
     * Charge toutes les configurations
     */
    public void loadConfigs() {
        // Création des dossiers nécessaires
        createDirectories();

        // Chargement du config.yml principal
        loadMainConfig();

        // Chargement du messages.yml
        loadMessagesConfig();

        // Chargement des configurations de quêtes par rareté
        loadQuestConfigs();

        plugin.getLogger().info("Toutes les configurations ont été chargées avec succès!");
    }

    /**
     * Crée les dossiers nécessaires
     */
    private void createDirectories() {
        // Dossier principal du plugin
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
            plugin.getLogger().info("Dossier principal créé: " + plugin.getDataFolder().getName());
        }

        // Dossier des quêtes
        File questsDir = new File(plugin.getDataFolder(), "quests");
        if (!questsDir.exists()) {
            questsDir.mkdirs();
            plugin.getLogger().info("Dossier quests/ créé");
        }
    }

    /**
     * Charge la configuration principale (config.yml)
     */
    private void loadMainConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");

        // Crée le fichier par défaut s'il n'existe pas
        if (!configFile.exists()) {
            createDefaultMainConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Charge les paramètres de rareté
        loadRaritySettings();

        plugin.getLogger().info("config.yml chargé");
    }

    /**
     * Crée le fichier config.yml par défaut
     */
    private void createDefaultMainConfig() {
        try {
            // Essaie de copier depuis les ressources du JAR
            plugin.saveResource("config.yml", false);
            plugin.getLogger().info("config.yml par défaut créé depuis les ressources");
        } catch (Exception e) {
            // Si pas de ressource, crée manuellement
            createManualDefaultConfig();
        }
    }

    /**
     * Crée manuellement le config.yml par défaut
     */
    private void createManualDefaultConfig() {
        config = new YamlConfiguration();

        // Configuration des raretés
        config.set("rarity-config.commune.reset-hours", 12);
        config.set("rarity-config.rare.reset-hours", 24);
        config.set("rarity-config.mythique.reset-hours", 72);
        config.set("rarity-config.legendaire.reset-hours", 168);

        // Configuration GUI
        config.set("gui.size", 27);
        config.set("gui.title", "&6Mes Quêtes Journalières");
        config.set("gui.filler-item.material", "GRAY_STAINED_GLASS_PANE");
        config.set("gui.filler-item.name", " ");

        // Slots des raretés
        config.set("gui.slots.commune", 10);
        config.set("gui.slots.rare", 12);
        config.set("gui.slots.mythique", 14);
        config.set("gui.slots.legendaire", 16);

        // Items par rareté
        setupDefaultRarityItems();

        // Sons
        config.set("sounds.quest-completed", "ENTITY_PLAYER_LEVELUP");
        config.set("sounds.quest-assigned", "ENTITY_EXPERIENCE_ORB_PICKUP");
        config.set("sounds.menu-open", "BLOCK_CHEST_OPEN");
        config.set("sounds.menu-click", "UI_BUTTON_CLICK");

        // Debug
        config.set("debug.enabled", false);
        config.set("debug.log-quest-progress", false);
        config.set("debug.log-quest-assignment", true);

        try {
            config.save(configFile);
            plugin.getLogger().info("config.yml par défaut créé manuellement");
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la création du config.yml: " + e.getMessage());
        }
    }

    /**
     * Configure les items par rareté par défaut
     */
    private void setupDefaultRarityItems() {
        // Commune
        config.set("gui.rarity-items.commune.material", "GREEN_CONCRETE_POWDER");
        config.set("gui.rarity-items.commune.name", "&aQuête Commune");
        config.set("gui.rarity-items.commune.lore", Arrays.asList(
                "&6Quête : &f%quest%",
                "",
                "&7Progression: &e{progress}&7/&e{required}",
                "&7Pourcentage: &e{percentage}%",
                "",
                "&7Récompenses:",
                "&8• &aClé Commune",
                "",
                "&7Temps restant: &e{time-left}"
        ));

        // Rare
        config.set("gui.rarity-items.rare.material", "BLUE_CONCRETE_POWDER");
        config.set("gui.rarity-items.rare.name", "&9Quête Rare");
        config.set("gui.rarity-items.rare.lore", Arrays.asList(
                "&6Quête : &f%quest%",
                "",
                "&7Progression: &e{progress}&7/&e{required}",
                "&7Pourcentage: &e{percentage}%",
                "",
                "&7Récompenses:",
                "&8• &9Clé Rare",
                "",
                "&7Temps restant: &e{time-left}"
        ));

        // Mythique
        config.set("gui.rarity-items.mythique.material", "MAGENTA_CONCRETE_POWDER");
        config.set("gui.rarity-items.mythique.name", "&dQuête Mythique");
        config.set("gui.rarity-items.mythique.lore", Arrays.asList(
                "&6Quête : &f%quest%",
                "",
                "&7Progression: &e{progress}&7/&e{required}",
                "&7Pourcentage: &e{percentage}%",
                "",
                "&7Récompenses:",
                "&8• &dClé Mythique",
                "",
                "&7Temps restant: &e{time-left}"
        ));

        // Légendaire
        config.set("gui.rarity-items.legendaire.material", "RED_CONCRETE_POWDER");
        config.set("gui.rarity-items.legendaire.name", "&6Quête Légendaire");
        config.set("gui.rarity-items.legendaire.lore", Arrays.asList(
                "&6Quête : &f%quest%",
                "",
                "&7Progression: &e{progress}&7/&e{required}",
                "&7Pourcentage: &e{percentage}%",
                "",
                "&7Récompenses:",
                "&8• &cClé Légendaire",
                "",
                "&7Temps restant: &e{time-left}"
        ));

        // Lore pour quête terminée
        config.set("gui.quest-completed-lore", Arrays.asList(
                "&a✓ Quête terminée!",
                "&7Cliquez pour récupérer vos récompenses"
        ));
    }

    /**
     * Charge les paramètres de rareté depuis config.yml
     */
    private void loadRaritySettings() {
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            String rarityName = rarity.name().toLowerCase();
            String path = "rarity-config." + rarityName;

            resetHours.put(rarity, config.getInt(path + ".reset-hours", getDefaultResetHours(rarity)));
            maxActiveQuests.put(rarity, config.getInt(path + ".max-active-quests", 1));
            rewardsMultiplier.put(rarity, config.getDouble(path + ".rewards-multiplier", getDefaultMultiplier(rarity)));
        }
    }

    /**
     * Charge la configuration des messages (messages.yml)
     */
    private void loadMessagesConfig() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        // Crée le fichier par défaut s'il n'existe pas
        if (!messagesFile.exists()) {
            createDefaultMessagesFile();
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("messages.yml chargé");
    }

    /**
     * Crée le fichier messages.yml par défaut
     */
    private void createDefaultMessagesFile() {
        messagesConfig = new YamlConfiguration();

        // Messages par défaut
        messagesConfig.set("prefix", "&8[&6SimpleDailyQuests&8] ");

        // Messages de quêtes
        messagesConfig.set("quest-completed", "&a✓ Quête terminée: &f{description} &7(Récompenses reçues!)");
        messagesConfig.set("quest-assigned", "&eNouvelle quête {rarity}: &f{description}");
        messagesConfig.set("quest-progress", "&7Progression: &e{progress}&7/&e{required} &7({percentage}%)");
        messagesConfig.set("quest-expired", "&cQuête expirée: &f{description}");

        // Messages de reset
        messagesConfig.set("quest-reset-commune", "&6Nouvelles quêtes communes disponibles!");
        messagesConfig.set("quest-reset-rare", "&6Nouvelle quête rare disponible!");
        messagesConfig.set("quest-reset-mythique", "&6Nouvelle quête mythique disponible!");
        messagesConfig.set("quest-reset-legendaire", "&6Nouvelle quête légendaire disponible!");

        // Messages d'erreur
        messagesConfig.set("no-active-quests", "&cVous n'avez aucune quête active.");
        messagesConfig.set("quest-not-found", "&cQuête introuvable.");
        messagesConfig.set("max-quests-reached", "&cVous avez déjà le maximum de quêtes actives pour cette rareté.");

        // Messages de commandes
        messagesConfig.set("reload-success", "&aConfigurations rechargées avec succès!");
        messagesConfig.set("player-not-found", "&cJoueur introuvable.");
        messagesConfig.set("invalid-command", "&cCommande invalide. Utilisez /dqa help");

        try {
            messagesConfig.save(messagesFile);
            plugin.getLogger().info("messages.yml par défaut créé");
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la création du messages.yml: " + e.getMessage());
        }
    }

    /**
     * Charge les configurations de quêtes pour chaque rareté
     */
    private void loadQuestConfigs() {
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            String fileName = rarity.name().toLowerCase() + ".yml";
            File questFile = new File(plugin.getDataFolder() + "/quests", fileName);

            // Création du fichier s'il n'existe pas
            if (!questFile.exists()) {
                createDefaultQuestFile(questFile, rarity);
            }

            FileConfiguration questConfig = YamlConfiguration.loadConfiguration(questFile);
            questConfigs.put(rarity, questConfig);
            plugin.getLogger().info("Configuration " + fileName + " chargée");
        }
    }

    /**
     * Crée un fichier de quête par défaut
     */
    private void createDefaultQuestFile(File file, Quest.QuestRarity rarity) {
        FileConfiguration config = new YamlConfiguration();

        // Création d'exemples selon la rareté
        switch (rarity) {
            case COMMUNE:
                createCommuneDefaults(config);
                break;
            case RARE:
                createRareDefaults(config);
                break;
            case MYTHIQUE:
                createMythiqueDefaults(config);
                break;
            case LEGENDAIRE:
                createLegendaireDefaults(config);
                break;
        }

        // Ajout des récompenses par défaut
        List<String> defaultRewards = getDefaultRewards(rarity);
        config.set("rewards", defaultRewards);

        try {
            config.save(file);
            plugin.getLogger().info("Fichier de quête créé: " + file.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la création du fichier " + file.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Crée les quêtes communes par défaut
     */
    private void createCommuneDefaults(FileConfiguration config) {
        // Quêtes de kill
        config.set("quests-pool.kill.zombie", 15);
        config.set("quests-pool.kill.skeleton", 12);
        config.set("quests-pool.kill.spider", 20);
        config.set("quests-pool.kill.creeper", 8);
        config.set("quests-pool.kill.cow", 25);
        config.set("quests-pool.kill.pig", 30);
        config.set("quests-pool.kill.chicken", 35);
        config.set("quests-pool.kill.sheep", 28);
        config.set("quests-pool.kill.slime", 10);
        config.set("quests-pool.kill.witch", 3);

        // Quêtes de craft
        config.set("quests-pool.craft.wooden_pickaxe", 5);
        config.set("quests-pool.craft.stone_sword", 3);
        config.set("quests-pool.craft.bread", 20);
        config.set("quests-pool.craft.torch", 64);
        config.set("quests-pool.craft.chest", 8);
        config.set("quests-pool.craft.furnace", 4);
        config.set("quests-pool.craft.stick", 32);
        config.set("quests-pool.craft.wooden_planks", 128);
        config.set("quests-pool.craft.crafting_table", 2);
        config.set("quests-pool.craft.bed", 1);

        // Quêtes de mine
        config.set("quests-pool.mine.stone", 128);
        config.set("quests-pool.mine.coal_ore", 32);
        config.set("quests-pool.mine.iron_ore", 16);
        config.set("quests-pool.mine.oak_log", 48);
        config.set("quests-pool.mine.cobblestone", 200);
        config.set("quests-pool.mine.dirt", 150);
        config.set("quests-pool.mine.sand", 100);
        config.set("quests-pool.mine.gravel", 80);
        config.set("quests-pool.mine.andesite", 64);
        config.set("quests-pool.mine.diorite", 64);

        // Quêtes de fish
        config.set("quests-pool.fish.cod", 12);
        config.set("quests-pool.fish.salmon", 8);
        config.set("quests-pool.fish.tropical_fish", 6);
        config.set("quests-pool.fish.pufferfish", 4);
        config.set("quests-pool.fish.fish", 15);
        config.set("quests-pool.fish.leather_boots", 2);
        config.set("quests-pool.fish.stick", 8);
        config.set("quests-pool.fish.bone", 5);
        config.set("quests-pool.fish.ink_sac", 3);
        config.set("quests-pool.fish.lily_pad", 4);
    }

    /**
     * Crée les quêtes rares par défaut
     */
    private void createRareDefaults(FileConfiguration config) {
        config.set("quests-pool.kill.enderman", 5);
        config.set("quests-pool.kill.witch", 3);
        config.set("quests-pool.kill.blaze", 8);
        config.set("quests-pool.kill.ghast", 2);
        config.set("quests-pool.kill.pillager", 10);
        config.set("quests-pool.kill.vindicator", 6);
        config.set("quests-pool.kill.evoker", 2);
        config.set("quests-pool.kill.guardian", 4);
        config.set("quests-pool.kill.drowned", 12);
        config.set("quests-pool.kill.phantom", 8);

        config.set("quests-pool.craft.enchanting_table", 1);
        config.set("quests-pool.craft.brewing_stand", 2);
        config.set("quests-pool.craft.anvil", 1);
        config.set("quests-pool.craft.iron_sword", 3);
        config.set("quests-pool.craft.diamond_pickaxe", 1);
        config.set("quests-pool.craft.iron_chestplate", 1);
        config.set("quests-pool.craft.bow", 2);
        config.set("quests-pool.craft.crossbow", 1);
        config.set("quests-pool.craft.shield", 2);
        config.set("quests-pool.craft.cauldron", 1);

        config.set("quests-pool.mine.diamond_ore", 8);
        config.set("quests-pool.mine.gold_ore", 24);
        config.set("quests-pool.mine.redstone_ore", 32);
        config.set("quests-pool.mine.lapis_ore", 16);
        config.set("quests-pool.mine.emerald_ore", 4);
        config.set("quests-pool.mine.nether_quartz_ore", 48);
        config.set("quests-pool.mine.copper_ore", 40);
        config.set("quests-pool.mine.deepslate_iron_ore", 20);
        config.set("quests-pool.mine.deepslate_coal_ore", 35);
        config.set("quests-pool.mine.obsidian", 12);

        config.set("quests-pool.fish.cod", 24);
        config.set("quests-pool.fish.salmon", 18);
        config.set("quests-pool.fish.tropical_fish", 12);
        config.set("quests-pool.fish.pufferfish", 8);
        config.set("quests-pool.fish.enchanted_book", 1);
        config.set("quests-pool.fish.name_tag", 2);
        config.set("quests-pool.fish.saddle", 1);
        config.set("quests-pool.fish.nautilus_shell", 3);
        config.set("quests-pool.fish.bow", 1);
        config.set("quests-pool.fish.fishing_rod", 2);
    }

    /**
     * Crée les quêtes mythiques par défaut
     */
    private void createMythiqueDefaults(FileConfiguration config) {
        config.set("quests-pool.kill.wither_skeleton", 4);
        config.set("quests-pool.kill.ghast", 2);
        config.set("quests-pool.kill.shulker", 6);
        config.set("quests-pool.kill.guardian", 8);
        config.set("quests-pool.kill.elder_guardian", 1);
        config.set("quests-pool.kill.evoker", 3);
        config.set("quests-pool.kill.ravager", 2);
        config.set("quests-pool.kill.piglin_brute", 5);
        config.set("quests-pool.kill.hoglin", 8);
        config.set("quests-pool.kill.zoglin", 4);

        config.set("quests-pool.craft.beacon", 1);
        config.set("quests-pool.craft.netherite_sword", 1);
        config.set("quests-pool.craft.enchanted_golden_apple", 2);
        config.set("quests-pool.craft.totem_of_undying", 1);
        config.set("quests-pool.craft.netherite_pickaxe", 1);
        config.set("quests-pool.craft.netherite_helmet", 1);
        config.set("quests-pool.craft.ender_chest", 2);
        config.set("quests-pool.craft.shulker_box", 3);
        config.set("quests-pool.craft.conduit", 1);
        config.set("quests-pool.craft.respawn_anchor", 1);

        config.set("quests-pool.mine.ancient_debris", 4);
        config.set("quests-pool.mine.emerald_ore", 8);
        config.set("quests-pool.mine.deepslate_diamond_ore", 12);
        config.set("quests-pool.mine.deepslate_emerald_ore", 6);
        config.set("quests-pool.mine.nether_gold_ore", 32);
        config.set("quests-pool.mine.gilded_blackstone", 16);
        config.set("quests-pool.mine.crying_obsidian", 8);
        config.set("quests-pool.mine.blackstone", 128);
        config.set("quests-pool.mine.basalt", 200);
        config.set("quests-pool.mine.soul_sand", 64);

        config.set("quests-pool.fish.enchanted_book", 3);
        config.set("quests-pool.fish.name_tag", 5);
        config.set("quests-pool.fish.saddle", 2);
        config.set("quests-pool.fish.nautilus_shell", 8);
        config.set("quests-pool.fish.heart_of_the_sea", 1);
        config.set("quests-pool.fish.mending_book", 1);
        config.set("quests-pool.fish.treasure_map", 2);
        config.set("quests-pool.fish.trident", 1);
        config.set("quests-pool.fish.fishing_rod", 5);
        config.set("quests-pool.fish.bow", 3);
    }

    /**
     * Crée les quêtes légendaires par défaut
     */
    private void createLegendaireDefaults(FileConfiguration config) {
        config.set("quests-pool.kill.ender_dragon", 1);
        config.set("quests-pool.kill.wither", 2);
        config.set("quests-pool.kill.elder_guardian", 3);
        config.set("quests-pool.kill.ravager", 5);
        config.set("quests-pool.kill.evoker", 8);
        config.set("quests-pool.kill.wither_skeleton", 20);
        config.set("quests-pool.kill.ghast", 10);
        config.set("quests-pool.kill.shulker", 25);
        config.set("quests-pool.kill.piglin_brute", 15);
        config.set("quests-pool.kill.hoglin", 30);

        config.set("quests-pool.craft.netherite_chestplate", 1);
        config.set("quests-pool.craft.enchanted_book", 5);
        config.set("quests-pool.craft.netherite_leggings", 1);
        config.set("quests-pool.craft.netherite_boots", 1);
        config.set("quests-pool.craft.netherite_helmet", 1);
        config.set("quests-pool.craft.beacon", 3);
        config.set("quests-pool.craft.conduit", 2);
        config.set("quests-pool.craft.end_crystal", 4);
        config.set("quests-pool.craft.firework_rocket", 64);
        config.set("quests-pool.craft.enchanted_golden_apple", 8);

        config.set("quests-pool.mine.ancient_debris", 20);
        config.set("quests-pool.mine.emerald_ore", 32);
        config.set("quests-pool.mine.diamond_ore", 64);
        config.set("quests-pool.mine.netherite_scrap", 16);
        config.set("quests-pool.mine.deepslate_diamond_ore", 48);
        config.set("quests-pool.mine.deepslate_emerald_ore", 24);
        config.set("quests-pool.mine.end_stone", 500);
        config.set("quests-pool.mine.purpur_block", 200);
        config.set("quests-pool.mine.chorus_fruit", 32);
        config.set("quests-pool.mine.dragon_egg", 1);

        config.set("quests-pool.fish.mending_book", 3);
        config.set("quests-pool.fish.heart_of_the_sea", 2);
        config.set("quests-pool.fish.nautilus_shell", 20);
        config.set("quests-pool.fish.enchanted_book", 10);
        config.set("quests-pool.fish.trident", 2);
        config.set("quests-pool.fish.treasure_map", 5);
        config.set("quests-pool.fish.name_tag", 15);
        config.set("quests-pool.fish.saddle", 8);
        config.set("quests-pool.fish.enchanted_fishing_rod", 3);
        config.set("quests-pool.fish.enchanted_bow", 2);
    }

    /**
     * Obtient les récompenses par défaut selon la rareté
     */
    private List<String> getDefaultRewards(Quest.QuestRarity rarity) {
        List<String> rewards = new ArrayList<>();

        switch (rarity) {
            case COMMUNE:
                rewards.add("cc give %player% commune 1");
                rewards.add("eco give %player% 100");
                rewards.add("xp give %player% 250");
                break;
            case RARE:
                rewards.add("cc give %player% rare 1");
                rewards.add("eco give %player% 500");
                rewards.add("xp give %player% 1000");
                rewards.add("broadcast &6%player% &ea terminé une quête rare!");
                break;
            case MYTHIQUE:
                rewards.add("cc give %player% mythique 1");
                rewards.add("eco give %player% 2000");
                rewards.add("xp give %player% 5000");
                rewards.add("broadcast &d%player% &ea accompli une quête mythique!");
                rewards.add("give %player% diamond 5");
                break;
            case LEGENDAIRE:
                rewards.add("cc give %player% legendaire 1");
                rewards.add("eco give %player% 10000");
                rewards.add("xp give %player% 20000");
                rewards.add("broadcast &6&l%player% &ea accompli une quête LÉGENDAIRE!");
                rewards.add("give %player% netherite_ingot 3");
                rewards.add("give %player% diamond 16");
                rewards.add("give %player% emerald 8");
                break;
        }

        return rewards;
    }

    // Méthodes pour obtenir les valeurs par défaut
    private int getDefaultResetHours(Quest.QuestRarity rarity) {
        switch (rarity) {
            case COMMUNE: return 12;
            case RARE: return 24;
            case MYTHIQUE: return 72;
            case LEGENDAIRE: return 168;
            default: return 24;
        }
    }


    private double getDefaultMultiplier(Quest.QuestRarity rarity) {
        switch (rarity) {
            case COMMUNE: return 1.0;
            case RARE: return 2.0;
            case MYTHIQUE: return 5.0;
            case LEGENDAIRE: return 10.0;
            default: return 1.0;
        }
    }

    // Getters publics
    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getQuestConfig(Quest.QuestRarity rarity) {
        return questConfigs.get(rarity);
    }

    public int getResetHours(Quest.QuestRarity rarity) {
        return resetHours.getOrDefault(rarity, getDefaultResetHours(rarity));
    }

    public int getMaxActiveQuests(Quest.QuestRarity rarity) {
        return maxActiveQuests.getOrDefault(rarity, 1);
    }

    public double getRewardsMultiplier(Quest.QuestRarity rarity) {
        return rewardsMultiplier.getOrDefault(rarity, getDefaultMultiplier(rarity));
    }
}