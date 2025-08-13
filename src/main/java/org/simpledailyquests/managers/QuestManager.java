package org.simpledailyquests.managers;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.models.Quest;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;

public class QuestManager {

    private final SimpleDailyQuests plugin;
    private final Random random;

    public QuestManager(SimpleDailyQuests plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    /**
     * Vérifie et effectue les resets de quêtes pour tous les joueurs connectés
     */
    public void checkAndResetQuests() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkAndResetPlayerQuests(player);
        }
    }

    /**
     * Vérifie et effectue le reset des quêtes pour un joueur spécifique
     */
    public void checkAndResetPlayerQuests(Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        boolean hasReset = false;

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            if (shouldResetQuests(playerData, rarity)) {
                resetQuestsForRarity(player, rarity);
                hasReset = true;
            }
        }

        // Supprime les quêtes expirées
        List<Quest> expiredQuests = playerData.removeExpiredQuests();
        if (!expiredQuests.isEmpty()) {
            for (Quest expired : expiredQuests) {
                String message = plugin.getConfigManager().getMessagesConfig()
                        .getString("quest-expired", "&cQuête expirée: &f{description}")
                        .replace("{description}", expired.getDescription());
                player.sendMessage(message);
            }
            hasReset = true;
        }

        if (hasReset) {
            assignNewQuests(player);
        }
    }

    /**
     * Vérifie si les quêtes d'une rareté doivent être réinitialisées
     */
    private boolean shouldResetQuests(PlayerQuestData playerData, Quest.QuestRarity rarity) {
        long lastReset = playerData.getLastReset(rarity);
        long currentTime = System.currentTimeMillis();
        long resetInterval = plugin.getConfigManager().getResetHours(rarity) * 60 * 60 * 1000L; // Conversion en millisecondes

        return (currentTime - lastReset) >= resetInterval;
    }

    /**
     * Réinitialise les quêtes d'une rareté spécifique pour un joueur
     */
    private void resetQuestsForRarity(Player player, Quest.QuestRarity rarity) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Supprime les quêtes actives de cette rareté
        playerData.clearActiveQuests(rarity);

        // Met à jour le timestamp de dernière réinitialisation
        playerData.setLastReset(rarity, System.currentTimeMillis());

        // Envoie un message de notification
        String messageKey = "quest-reset-" + rarity.name().toLowerCase();
        String message = plugin.getConfigManager().getMessagesConfig().getString(messageKey);
        if (message != null && !message.isEmpty()) {
            String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
            player.sendMessage(prefix + message.replace("&", "§"));
        }

        if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-assignment", true)) {
            plugin.getLogger().info("Reset des quêtes " + rarity.name() + " pour " + player.getName());
        }
    }

    /**
     * Assigne de nouvelles quêtes à un joueur
     */
    public void assignNewQuests(Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            int maxQuests = plugin.getConfigManager().getMaxActiveQuests(rarity);
            int currentQuests = playerData.getActiveQuestCount(rarity);
            int questsToAssign = maxQuests - currentQuests;

            for (int i = 0; i < questsToAssign; i++) {
                Quest newQuest = generateRandomQuest(rarity);
                if (newQuest != null) {
                    playerData.addActiveQuest(newQuest);

                    // Message de nouvelle quête
                    String message = plugin.getConfigManager().getMessagesConfig()
                            .getString("quest-assigned", "&eNouvelle quête {rarity}: &f{description}")
                            .replace("{rarity}", rarity.name())
                            .replace("{description}", newQuest.getDescription());
                    String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
                    player.sendMessage(prefix + message.replace("&", "§"));

                    if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-assignment", true)) {
                        plugin.getLogger().info("Nouvelle quête assignée à " + player.getName() + ": " + newQuest.getQuestId());
                    }
                }
            }
        }
    }

    /**
     * Génère une quête aléatoire d'une rareté donnée
     */
    private Quest generateRandomQuest(Quest.QuestRarity rarity) {
        ConfigurationSection questsPool = plugin.getConfigManager()
                .getQuestConfig(rarity)
                .getConfigurationSection("quests-pool");

        if (questsPool == null) {
            plugin.getLogger().warning("Aucune quête trouvée pour la rareté: " + rarity.name());
            return null;
        }

        // Collecte tous les types de quêtes disponibles
        List<String> questTypes = new ArrayList<>(questsPool.getKeys(false));
        if (questTypes.isEmpty()) {
            return null;
        }

        // Sélectionne un type aléatoire
        String selectedType = questTypes.get(random.nextInt(questTypes.size()));
        ConfigurationSection typeSection = questsPool.getConfigurationSection(selectedType);

        if (typeSection == null) {
            return null;
        }

        // Sélectionne un target aléatoire dans ce type
        List<String> targets = new ArrayList<>(typeSection.getKeys(false));
        if (targets.isEmpty()) {
            return null;
        }

        String selectedTarget = targets.get(random.nextInt(targets.size()));
        int required = typeSection.getInt(selectedTarget);

        // Applique le multiplicateur de rareté
        double multiplier = plugin.getConfigManager().getRewardsMultiplier(rarity);
        required = (int) Math.max(1, required * Math.sqrt(multiplier)); // Racine carrée pour éviter des valeurs trop élevées

        // Récupère les récompenses
        List<String> rewards = plugin.getConfigManager()
                .getQuestConfig(rarity)
                .getStringList("rewards");

        // Génère l'ID unique de la quête
        String questId = selectedType + "_" + selectedTarget + "_" + required + "_" + System.currentTimeMillis();

        try {
            Quest.QuestType type = Quest.QuestType.valueOf(selectedType.toUpperCase());
            return new Quest(questId, type, rarity, selectedTarget, required, rewards);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type de quête invalide: " + selectedType);
            return null;
        }
    }

    /**
     * Traite le progrès d'une quête pour un joueur
     */
    public void processQuestProgress(Player player, Quest.QuestType type, String target, int amount) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Trouve la quête correspondante
        Quest quest = playerData.findQuestByTypeAndTarget(type, target);
        if (quest == null) {
            return;
        }

        // Ajoute le progrès
        int oldProgress = quest.getProgress();
        quest.addProgress(amount);

        // Log du progrès si activé
        if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
            plugin.getLogger().info(player.getName() + " a progressé sur " + quest.getQuestId() +
                    ": " + oldProgress + " -> " + quest.getProgress() + "/" + quest.getRequired());
        }

        // Vérifie si la quête est terminée
        if (quest.isCompleted() && oldProgress < quest.getRequired()) {
            completeQuest(player, quest);
        }

        // Met à jour le scoreboard si activé
        plugin.getScoreboardManager().updatePlayerScoreboard(player);
    }

    /**
     * Termine une quête et donne les récompenses
     */
    public void completeQuest(Player player, Quest quest) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Marque la quête comme terminée
        playerData.completeQuest(quest);

        // Donne les récompenses
        giveQuestRewards(player, quest);

        // Message de completion
        String message = plugin.getConfigManager().getMessagesConfig()
                .getString("quest-completed", "&a✓ Quête terminée: &f{description} &7(Récompenses reçues!)")
                .replace("{description}", quest.getDescription());
        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
        player.sendMessage(prefix + message.replace("&", "§"));

        // Son de completion si configuré
        String sound = plugin.getConfigManager().getConfig().getString("sounds.quest-completed");
        if (sound != null && !sound.isEmpty()) {
            try {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception e) {
                // Ignore les erreurs de sons
            }
        }

        // Met à jour le scoreboard
        plugin.getScoreboardManager().updatePlayerScoreboard(player);

        plugin.getLogger().info(player.getName() + " a terminé la quête: " + quest.getQuestId());
    }

    /**
     * Donne les récompenses d'une quête à un joueur
     */
    private void giveQuestRewards(Player player, Quest quest) {
        List<String> rewards = quest.getRewards();
        double multiplier = plugin.getConfigManager().getRewardsMultiplier(quest.getRarity());

        for (String reward : rewards) {
            // Remplace les placeholders
            String processedReward = reward
                    .replace("%player%", player.getName())
                    .replace("{player}", player.getName());

            // Exécute la commande de récompense
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedReward);
        }
    }

    /**
     * Force l'attribution de nouvelles quêtes à un joueur (commande admin)
     */
    public void forceAssignQuests(Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Clear toutes les quêtes actives
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            playerData.clearActiveQuests(rarity);
            playerData.setLastReset(rarity, System.currentTimeMillis());
        }

        // Assigne de nouvelles quêtes
        assignNewQuests(player);
    }

    /**
     * Obtient le statut des quêtes d'un joueur
     */
    public String getQuestStatus(Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        StringBuilder status = new StringBuilder();

        status.append("§6=== Statut des Quêtes ===\n");

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            List<Quest> activeQuests = playerData.getActiveQuests(rarity);
            status.append("§e").append(rarity.name()).append(" (").append(activeQuests.size()).append("/")
                    .append(plugin.getConfigManager().getMaxActiveQuests(rarity)).append("):\n");

            if (activeQuests.isEmpty()) {
                status.append("  §7Aucune quête active\n");
            } else {
                for (Quest quest : activeQuests) {
                    double percentage = quest.getProgressPercentage();
                    status.append("  §f").append(quest.getDescription())
                            .append(" §7(").append(quest.getProgress()).append("/").append(quest.getRequired())
                            .append(" - ").append(String.format("%.1f", percentage)).append("%)\n");
                }
            }
        }

        status.append("§6Total terminées: §e").append(playerData.getTotalCompletedQuests());

        return status.toString();
    }
}


