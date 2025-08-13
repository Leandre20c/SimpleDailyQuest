package org.simpledailyquests.managers;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.models.Quest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class QuestManager {

    private final SimpleDailyQuests plugin;

    public QuestManager(SimpleDailyQuests plugin) {
        this.plugin = plugin;
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
            notifyPlayerQuestsReset(player);
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

        if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-assignment", true)) {
            plugin.getLogger().info("Reset des quêtes " + rarity.name() + " pour " + player.getName());
        }
    }

    /**
     * Notifie le joueur que ses quêtes ont été reset (sans en assigner automatiquement)
     */
    private void notifyPlayerQuestsReset(Player player) {
        String message = plugin.getConfigManager().getMessagesConfig()
                .getString("new-quests-available", "&6✨ Nouvelles quêtes disponibles ! &b/quete")
                .replace("&", "§");
        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");

        // Délai pour éviter le spam au login
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(prefix + message);

                // Son optionnel
                String sound = plugin.getConfigManager().getConfig().getString("sounds.quest-assigned");
                if (sound != null && !sound.isEmpty()) {
                    try {
                        player.playSound(player.getLocation(), sound, 0.7f, 1.2f);
                    } catch (Exception e) {
                        // Ignore les erreurs de son
                    }
                }
            }
        }, 40L); // 2 secondes de délai
    }

    /**
     * Ajoute manuellement une quête à un joueur
     */
    public void addQuestToPlayer(Player player, Quest quest) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        playerData.addActiveQuest(quest);

        // Message de nouvelle quête
        String message = plugin.getConfigManager().getMessagesConfig()
                .getString("quest-assigned", "&eNouvelle quête {rarity}: &f{description}")
                .replace("{rarity}", quest.getRarity().name())
                .replace("{description}", quest.getDescription());
        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
        player.sendMessage(prefix + message.replace("&", "§"));

        if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-assignment", true)) {
            plugin.getLogger().info("Quête assignée manuellement à " + player.getName() + ": " + quest.getQuestId());
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
     * Force le reset de toutes les quêtes d'un joueur (commande admin)
     */
    public void forceResetQuests(Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Clear toutes les quêtes actives
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            playerData.clearActiveQuests(rarity);
            playerData.setLastReset(rarity, System.currentTimeMillis());
        }

        // Notifie le joueur
        notifyPlayerQuestsReset(player);
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
            status.append("§e").append(rarity.name()).append(" (").append(activeQuests.size()).append("):\n");

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