package org.simpledailyquests;

import org.simpledailyquests.commands.DailyQuestCommand;
import org.simpledailyquests.commands.DailyQuestAdminCommand;
import org.simpledailyquests.listeners.PlayerListener;
import org.simpledailyquests.managers.PlayerDataManager;
import org.simpledailyquests.managers.ConfigManager;
import org.simpledailyquests.managers.QuestManager;
import org.simpledailyquests.managers.ScoreboardManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class SimpleDailyQuests extends JavaPlugin {

    private static SimpleDailyQuests instance;
    private QuestManager questManager;
    private PlayerDataManager playerDataManager;
    private ConfigManager configManager;
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Démarrage de SimpleDailyQuests...");

        // Initialisation des managers
        this.configManager = new ConfigManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.questManager = new QuestManager(this);
        this.scoreboardManager = new ScoreboardManager(this);

        // Chargement des configurations
        configManager.loadConfigs();
        playerDataManager.loadPlayerData();

        // Enregistrement des listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Enregistrement des commandes
        getCommand("dailyquest").setExecutor(new DailyQuestCommand(this));
        getCommand("dailyquestadmin").setExecutor(new DailyQuestAdminCommand(this));

        // Démarrage du système de reset automatique
        startQuestResetTask();

        // Démarrage de la sauvegarde automatique
        playerDataManager.startAutoSave();

        getLogger().info("SimpleDailyQuests activé avec succès!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Arrêt de SimpleDailyQuests...");

        // Sauvegarde des données avant fermeture
        if (playerDataManager != null) {
            playerDataManager.savePlayerData();
        }

        // Suppression des scoreboards
        if (scoreboardManager != null) {
            scoreboardManager.removeAllScoreboards();
        }

        getLogger().info("SimpleDailyQuests désactivé!");
    }

    /**
     * Démarre la tâche de reset automatique des quêtes
     */
    private void startQuestResetTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (questManager != null) {
                    questManager.checkAndResetQuests();
                }
            }
        }.runTaskTimer(this, 20L * 60, 20L * 60); // Vérifie toutes les minutes
    }

    // Getters pour les autres classes
    public static SimpleDailyQuests getInstance() {
        return instance;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    /**
     * Recharge toutes les configurations
     */
    public void reloadConfigs() {
        configManager.loadConfigs();
        getLogger().info("Configurations rechargées!");
    }
}