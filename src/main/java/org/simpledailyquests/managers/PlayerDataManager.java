package org.simpledailyquests.managers;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.models.Quest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerDataManager {

    private final SimpleDailyQuests plugin;
    private File playerDataFile;
    private FileConfiguration playerData;
    private final Map<UUID, PlayerQuestData> playerCache;

    public PlayerDataManager(SimpleDailyQuests plugin) {
        this.plugin = plugin;
        this.playerCache = new HashMap<>();
    }

    /**
     * Charge les données des joueurs depuis le fichier
     */
    public void loadPlayerData() {
        playerDataFile = new File(plugin.getDataFolder(), "players-data.yml");

        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
                plugin.getLogger().info("Fichier players-data.yml créé.");
            } catch (IOException e) {
                plugin.getLogger().severe("Erreur lors de la création du fichier players-data.yml: " + e.getMessage());
                return;
            }
        }

        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        loadPlayerCache();
    }

    /**
     * Charge les données des joueurs en cache
     */
    private void loadPlayerCache() {
        if (!playerData.contains("players")) {
            return;
        }

        for (String uuidString : playerData.getConfigurationSection("players").getKeys(false)) {
            UUID playerUUID = UUID.fromString(uuidString);
            PlayerQuestData questData = loadPlayerQuestData(playerUUID);
            playerCache.put(playerUUID, questData);
        }

        plugin.getLogger().info("Données de " + playerCache.size() + " joueur(s) chargées.");
    }

    /**
     * Charge les données de quête d'un joueur spécifique
     */
    private PlayerQuestData loadPlayerQuestData(UUID playerUUID) {
        String path = "players." + playerUUID.toString();
        PlayerQuestData questData = new PlayerQuestData(playerUUID);

        // Chargement du nom
        questData.setPlayerName(playerData.getString(path + ".name", "Unknown"));

        // Chargement des temps de dernière réinitialisation
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            String rarityName = rarity.name().toLowerCase();
            long lastReset = playerData.getLong(path + ".last-reset." + rarityName, 0);
            questData.setLastReset(rarity, lastReset);
        }

        // Chargement des quêtes actives
        loadActiveQuests(questData, path);

        // Chargement des quêtes terminées
        loadCompletedQuests(questData, path);

        return questData;
    }

    /**
     * Charge les quêtes actives d'un joueur
     */
    private void loadActiveQuests(PlayerQuestData questData, String basePath) {
        String activePath = basePath + ".active-quests";

        if (!playerData.contains(activePath)) {
            return;
        }

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            String rarityName = rarity.name().toLowerCase();
            String rarityPath = activePath + "." + rarityName;

            if (!playerData.contains(rarityPath)) {
                continue;
            }

            List<Map<?, ?>> questMaps = playerData.getMapList(rarityPath);
            for (Map<?, ?> questMap : questMaps) {
                Quest quest = mapToQuest(questMap, rarity);
                if (quest != null) {
                    questData.addActiveQuest(quest);
                }
            }
        }
    }

    /**
     * Charge les quêtes terminées d'un joueur
     */
    private void loadCompletedQuests(PlayerQuestData questData, String basePath) {
        String completedPath = basePath + ".completed-quests";

        if (!playerData.contains(completedPath)) {
            return;
        }

        List<Map<?, ?>> completedMaps = playerData.getMapList(completedPath);
        for (Map<?, ?> completedMap : completedMaps) {
            String questId = (String) completedMap.get("quest-id");
            long completionTime = ((Number) completedMap.get("completion-time")).longValue();
            String rarityString = (String) completedMap.get("rarity");

            Quest.QuestRarity rarity = Quest.QuestRarity.valueOf(rarityString.toUpperCase());
            questData.addCompletedQuest(questId, completionTime, rarity);
        }
    }

    /**
     * Convertit une map en objet Quest
     */
    private Quest mapToQuest(Map<?, ?> questMap, Quest.QuestRarity rarity) {
        try {
            String questId = (String) questMap.get("quest-id");
            String typeString = (String) questMap.get("type");
            String target = (String) questMap.get("target");
            int required = ((Number) questMap.get("required")).intValue();
            int progress = ((Number) questMap.get("progress")).intValue();
            long assignedTime = ((Number) questMap.get("assigned-time")).longValue();

            Quest.QuestType type = Quest.QuestType.valueOf(typeString.toUpperCase());

            // Récupération des récompenses depuis la configuration
            List<String> rewards = plugin.getConfigManager().getQuestConfig(rarity).getStringList("rewards");

            Quest quest = new Quest(questId, type, rarity, target, required, rewards);
            quest.setProgress(progress);
            quest.setAssignedTime(assignedTime);

            return quest;
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du chargement d'une quête: " + e.getMessage());
            return null;
        }
    }

    /**
     * Sauvegarde toutes les données des joueurs
     */
    public void savePlayerData() {
        if (playerData == null) return;

        // Effacement des données existantes
        playerData.set("players", null);

        // Sauvegarde de chaque joueur
        for (PlayerQuestData questData : playerCache.values()) {
            savePlayerQuestData(questData);
        }

        try {
            playerData.save(playerDataFile);
            plugin.getLogger().info("Données des joueurs sauvegardées.");
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des données des joueurs: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde les données d'un joueur spécifique
     */
    private void savePlayerQuestData(PlayerQuestData questData) {
        String path = "players." + questData.getPlayerUUID().toString();

        playerData.set(path + ".name", questData.getPlayerName());

        // Sauvegarde des temps de dernière réinitialisation
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            String rarityName = rarity.name().toLowerCase();
            playerData.set(path + ".last-reset." + rarityName, questData.getLastReset(rarity));
        }

        // Sauvegarde des quêtes actives
        saveActiveQuests(questData, path);

        // Sauvegarde des quêtes terminées
        saveCompletedQuests(questData, path);
    }

    /**
     * Sauvegarde les quêtes actives d'un joueur
     */
    private void saveActiveQuests(PlayerQuestData questData, String basePath) {
        String activePath = basePath + ".active-quests";

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            String rarityName = rarity.name().toLowerCase();
            List<Quest> activeQuests = questData.getActiveQuests(rarity);

            if (activeQuests.isEmpty()) {
                playerData.set(activePath + "." + rarityName, null);
                continue;
            }

            List<Map<String, Object>> questMaps = new ArrayList<>();
            for (Quest quest : activeQuests) {
                Map<String, Object> questMap = new HashMap<>();
                questMap.put("quest-id", quest.getQuestId());
                questMap.put("type", quest.getType().name());
                questMap.put("target", quest.getTarget());
                questMap.put("required", quest.getRequired());
                questMap.put("progress", quest.getProgress());
                questMap.put("assigned-time", quest.getAssignedTime());
                questMaps.add(questMap);
            }

            playerData.set(activePath + "." + rarityName, questMaps);
        }
    }

    /**
     * Sauvegarde les quêtes terminées d'un joueur
     */
    private void saveCompletedQuests(PlayerQuestData questData, String basePath) {
        String completedPath = basePath + ".completed-quests";

        List<Map<String, Object>> completedMaps = new ArrayList<>();
        for (PlayerQuestData.CompletedQuest completed : questData.getCompletedQuests()) {
            Map<String, Object> completedMap = new HashMap<>();
            completedMap.put("quest-id", completed.getQuestId());
            completedMap.put("completion-time", completed.getCompletionTime());
            completedMap.put("rarity", completed.getRarity().name());
            completedMaps.add(completedMap);
        }

        playerData.set(completedPath, completedMaps);
    }

    /**
     * Obtient les données de quête d'un joueur
     */
    public PlayerQuestData getPlayerData(UUID playerUUID) {
        return playerCache.computeIfAbsent(playerUUID, uuid -> new PlayerQuestData(uuid));
    }

    /**
     * Obtient les données de quête d'un joueur
     */
    public PlayerQuestData getPlayerData(Player player) {
        PlayerQuestData data = getPlayerData(player.getUniqueId());
        data.setPlayerName(player.getName()); // Met à jour le nom au cas où il aurait changé
        return data;
    }

    /**
     * Supprime un joueur du cache et des données
     */
    public void removePlayerData(UUID playerUUID) {
        playerCache.remove(playerUUID);
        playerData.set("players." + playerUUID.toString(), null);
        savePlayerData();
    }

    /**
     * Sauvegarde automatique périodique
     */
    public void startAutoSave() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!playerCache.isEmpty()) {
                savePlayerData();
            }
        }, 20L * 60 * 5, 20L * 60 * 5); // Sauvegarde toutes les 5 minutes
    }
}