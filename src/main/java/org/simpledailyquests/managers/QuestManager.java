package org.simpledailyquests.managers;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.models.Quest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        boolean hasUpdate = false;

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            // Vérifie si le timer de cette rareté est écoulé (temps de rotation)
            if (shouldRotateQuest(playerData, rarity)) {
                rotateQuestForRarity(player, rarity);
                hasUpdate = true;
            }
            // Si le joueur n'a aucune quête de cette rareté ET n'a jamais eu de reset, génère une initiale
            else if (playerData.getActiveQuests(rarity).isEmpty() && playerData.getLastReset(rarity) == 0) {
                generateRandomQuestForRarity(player, rarity);
                playerData.setLastReset(rarity, System.currentTimeMillis()); // Démarre le timer
                hasUpdate = true;
            }
        }

        // Supprime les quêtes expirées (selon leur durée individuelle, pas le timer de rotation)
        List<Quest> expiredQuests = playerData.removeExpiredQuests();
        if (!expiredQuests.isEmpty()) {
            for (Quest expired : expiredQuests) {
                String message = plugin.getConfigManager().getMessagesConfig()
                        .getString("quest-expired", "&cQuête expirée: &f{description}")
                        .replace("{description}", expired.getDescription())
                        .replace("&", "§");
                String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
                player.sendMessage(prefix + message);
            }
            hasUpdate = true;
        }

        if (hasUpdate) {
            // Met à jour le scoreboard
            plugin.getScoreboardManager().updatePlayerScoreboard(player);
        }
    }

    /**
     * Vérifie si le timer de rotation d'une rareté est écoulé
     */
    private boolean shouldRotateQuest(PlayerQuestData playerData, Quest.QuestRarity rarity) {
        long lastReset = playerData.getLastReset(rarity);
        if (lastReset == 0) {
            return false; // Pas encore de timer démarré
        }

        long currentTime = System.currentTimeMillis();
        long rotationInterval = plugin.getConfigManager().getResetHours(rarity) * 60 * 60 * 1000L;

        return (currentTime - lastReset) >= rotationInterval;
    }

    /**
     * Fait la rotation d'une quête (remplace l'ancienne par une nouvelle)
     */
    private void rotateQuestForRarity(Player player, Quest.QuestRarity rarity) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Supprime l'ancienne quête (terminée ou non)
        List<Quest> oldQuests = new ArrayList<>(playerData.getActiveQuests(rarity));
        playerData.clearActiveQuests(rarity);

        // Met à jour le timestamp pour le prochain timer
        playerData.setLastReset(rarity, System.currentTimeMillis());

        // Génère une nouvelle quête
        generateRandomQuestForRarity(player, rarity);

        // Notifie le joueur
        String message = plugin.getConfigManager().getMessagesConfig()
                .getString("quest-rotated", "&6✨ Nouvelle quête {rarity} disponible !")
                .replace("{rarity}", rarity.name())
                .replace("&", "§");
        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
        player.sendMessage(prefix + message);

        // Si une ancienne quête non terminée est remplacée
        for (Quest oldQuest : oldQuests) {
            if (!oldQuest.isCompleted()) {
                String lostMessage = plugin.getConfigManager().getMessagesConfig()
                        .getString("quest-replaced", "&cQuête non terminée remplacée: &f{description}")
                        .replace("{description}", oldQuest.getDescription())
                        .replace("&", "§");
                player.sendMessage(prefix + lostMessage);
            }
        }

        if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-assignment", true)) {
            plugin.getLogger().info("Rotation de quête " + rarity.name() + " pour " + player.getName());
        }
    }

    /**
     * Vérifie si les quêtes d'une rareté doivent être réinitialisées
     * DEPRECATED: Cette méthode n'est plus utilisée avec le nouveau système de cooldown
     */
    @Deprecated
    private boolean shouldResetQuests(PlayerQuestData playerData, Quest.QuestRarity rarity) {
        long lastReset = playerData.getLastReset(rarity);
        long currentTime = System.currentTimeMillis();
        long resetInterval = plugin.getConfigManager().getResetHours(rarity) * 60 * 60 * 1000L; // Conversion en millisecondes

        return (currentTime - lastReset) >= resetInterval;
    }

    /**
     * Réinitialise les quêtes d'une rareté spécifique pour un joueur (ADMIN ONLY)
     */
    public void resetQuestsForRarity(Player player, Quest.QuestRarity rarity) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Supprime les quêtes actives de cette rareté
        playerData.clearActiveQuests(rarity);

        // Met à jour le timestamp pour redémarrer le timer
        playerData.setLastReset(rarity, System.currentTimeMillis());

        // GÉNÈRE UNE NOUVELLE QUÊTE pour cette rareté (pour les commandes admin seulement)
        generateRandomQuestForRarity(player, rarity);

        if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-assignment", true)) {
            plugin.getLogger().info("Reset ADMIN des quêtes " + rarity.name() + " pour " + player.getName() + " + timer redémarré");
        }
    }

    /**
     * Génère une quête aléatoire pour une rareté spécifique
     * ATTENTION: Cette méthode ne modifie PAS le timer lastReset
     */
    public void generateRandomQuestForRarity(Player player, Quest.QuestRarity rarity) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Vérifie si le joueur peut recevoir une nouvelle quête (limite : 1 par rareté)
        int maxQuests = 1; // Toujours 1 quête maximum par rareté
        if (!playerData.canReceiveQuest(rarity, maxQuests)) {
            if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-assignment", true)) {
                plugin.getLogger().info("Joueur " + player.getName() + " ne peut pas recevoir de quête " + rarity.name() + " (déjà une active)");
            }
            return;
        }

        // Récupère la configuration de cette rareté
        org.bukkit.configuration.file.FileConfiguration questConfig = plugin.getConfigManager().getQuestConfig(rarity);
        if (questConfig == null) {
            plugin.getLogger().warning("Configuration de quête introuvable pour " + rarity.name());
            return;
        }

        // Génère une quête aléatoire
        Quest newQuest = createRandomQuest(rarity, questConfig);
        if (newQuest != null) {
            playerData.addActiveQuest(newQuest);

            // Message de nouvelle quête
            String message = plugin.getConfigManager().getMessagesConfig()
                    .getString("quest-assigned", "&eNouvelle quête {rarity}: &f{description}")
                    .replace("{rarity}", rarity.name())
                    .replace("{description}", newQuest.getDescription())
                    .replace("&", "§");
            String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
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

            if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-assignment", true)) {
                plugin.getLogger().info("Nouvelle quête " + rarity.name() + " assignée à " + player.getName() + ": " + newQuest.getQuestId());
            }
        }
    }

    /**
     * Crée une quête aléatoire basée sur la configuration
     */
    private Quest createRandomQuest(Quest.QuestRarity rarity, org.bukkit.configuration.file.FileConfiguration config) {
        if (!config.contains("quests-pool")) {
            return null;
        }

        org.bukkit.configuration.ConfigurationSection poolSection = config.getConfigurationSection("quests-pool");
        if (poolSection == null) {
            return null;
        }

        // Récupère tous les types de quêtes disponibles
        List<String> questTypes = new ArrayList<>(poolSection.getKeys(false));
        if (questTypes.isEmpty()) {
            return null;
        }

        // Choisit un type aléatoire
        Random random = new Random();
        String randomType = questTypes.get(random.nextInt(questTypes.size()));

        try {
            Quest.QuestType questType = Quest.QuestType.valueOf(randomType.toUpperCase());
            org.bukkit.configuration.ConfigurationSection typeSection = poolSection.getConfigurationSection(randomType);

            if (typeSection == null) {
                return null;
            }

            // Récupère tous les targets disponibles pour ce type
            List<String> targets = new ArrayList<>(typeSection.getKeys(false));
            if (targets.isEmpty()) {
                return null;
            }

            // Choisit un target aléatoire
            String randomTarget = targets.get(random.nextInt(targets.size()));
            int required = typeSection.getInt(randomTarget, 1);

            // Récupère les récompenses
            List<String> rewards = config.getStringList("rewards");
            if (rewards.isEmpty()) {
                rewards = Arrays.asList("say " + rarity.name() + " quest completed by %player%");
            }

            // Génère un ID unique
            String questId = rarity.name().toLowerCase() + "_" + questType.name().toLowerCase() + "_" + randomTarget + "_" + System.currentTimeMillis();

            return new Quest(questId, questType, rarity, randomTarget, required, rewards);

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Type de quête invalide: " + randomType);
            return null;
        }
    }

    /**
     * Génère des quêtes initiales pour un nouveau joueur
     */
    public void generateInitialQuests(Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            // Génère seulement si le joueur n'a pas de quête active ET n'a jamais eu de timer démarré
            if (playerData.getActiveQuests(rarity).isEmpty() && playerData.getLastReset(rarity) == 0) {
                generateRandomQuestForRarity(player, rarity);
                // Démarre le timer pour cette rareté
                playerData.setLastReset(rarity, System.currentTimeMillis());
            }
        }
    }

    /**
     * Force la génération de quêtes (commande admin - redémarre les timers)
     */
    public void forceGenerateInitialQuests(Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            // Efface l'ancienne quête
            playerData.clearActiveQuests(rarity);
            // Génère une nouvelle quête
            generateRandomQuestForRarity(player, rarity);
            // Redémarre le timer
            playerData.setLastReset(rarity, System.currentTimeMillis());
        }
    }

    /**
     * Notifie le joueur que ses quêtes ont été reset (sans en assigner automatiquement)
     * DEPRECATED: Cette méthode n'est plus utilisée car les quêtes sont générées automatiquement
     */
    @Deprecated
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
            markQuestAsCompleted(player, quest);
        }

        // Met à jour le scoreboard si activé
        plugin.getScoreboardManager().updatePlayerScoreboard(player);
    }

    /**
     * Marque une quête comme terminée et affiche le titre (sans donner les récompenses)
     */
    public void markQuestAsCompleted(Player player, Quest quest) {
        // Affiche le titre de quête terminée
        String title = plugin.getConfigManager().getMessagesConfig()
                .getString("quest-completed-title", "&a&lQuête Terminée!")
                .replace("&", "§");

        String subtitle = plugin.getConfigManager().getMessagesConfig()
                .getString("quest-completed-subtitle", "&b/q &7pour récupérer tes récompenses")
                .replace("&", "§");

        player.sendTitle(title, subtitle, 10, 60, 20);

        // Message dans le chat
        String message = plugin.getConfigManager().getMessagesConfig()
                .getString("quest-completed-notification", "&a✓ Quête terminée: &f{description} &7- Cliquez dans le menu pour récupérer vos récompenses!")
                .replace("{description}", quest.getDescription())
                .replace("&", "§");
        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
        player.sendMessage(prefix + message);

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
     * Vérifie si le joueur a assez de place dans son inventaire
     */
    public boolean hasEnoughInventorySpace(Player player, int requiredSlots) {
        int emptySlots = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();

        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                emptySlots++;
            }
        }

        return emptySlots >= requiredSlots;
    }

    /**
     * Termine une quête et donne les récompenses directement (pour les commandes admin)
     */
    public void forceCompleteQuest(Player player, Quest quest) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Marque la quête comme terminée et la retire des quêtes actives
        playerData.completeQuest(quest);

        // Donne les récompenses directement (sans vérification d'inventaire pour admin)
        giveQuestRewards(player, quest);

        // Message de completion
        String message = plugin.getConfigManager().getMessagesConfig()
                .getString("rewards-claimed", "&a✓ Récompenses récupérées pour: &f{description}")
                .replace("{description}", quest.getDescription())
                .replace("&", "§");
        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
        player.sendMessage(prefix + message);

        // Son de completion
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

        plugin.getLogger().info("Quête forcée terminée par admin pour " + player.getName() + ": " + quest.getQuestId());
    }

    /**
     * Termine une quête et donne les récompenses (appelé lors du clic dans le GUI)
     */
    public boolean claimQuestRewards(Player player, Quest quest) {
        // Vérifie si le joueur a assez de place (2 slots minimum)
        if (!hasEnoughInventorySpace(player, 2)) {
            String message = plugin.getConfigManager().getMessagesConfig()
                    .getString("inventory-full", "&cVous devez avoir au moins 2 slots libres dans votre inventaire pour récupérer vos récompenses!")
                    .replace("&", "§");
            String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
            player.sendMessage(prefix + message);
            return false;
        }

        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Marque la quête comme terminée et la retire des quêtes actives
        playerData.completeQuest(quest);

        // IMPORTANT : On ne modifie PAS le lastReset ici
        // La nouvelle quête viendra au moment du timer normal

        // Donne les récompenses
        giveQuestRewards(player, quest);

        // Message de récupération
        String message = plugin.getConfigManager().getMessagesConfig()
                .getString("rewards-claimed", "&a✓ Récompenses récupérées pour: &f{description}")
                .replace("{description}", quest.getDescription())
                .replace("&", "§");
        String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
        player.sendMessage(prefix + message);

        // Message informatif sur la prochaine quête
        long lastReset = playerData.getLastReset(quest.getRarity());
        long currentTime = System.currentTimeMillis();
        long rotationInterval = plugin.getConfigManager().getResetHours(quest.getRarity()) * 60 * 60 * 1000L;
        long timeUntilNext = rotationInterval - (currentTime - lastReset);

        if (timeUntilNext > 0) {
            long hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(timeUntilNext);
            long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(timeUntilNext) % 60;

            String nextQuestMessage = plugin.getConfigManager().getMessagesConfig()
                    .getString("next-quest-info", "&7Prochaine quête {rarity} dans &e{time}")
                    .replace("{rarity}", quest.getRarity().name())
                    .replace("{time}", hours > 0 ? hours + "h " + minutes + "m" : minutes + "m")
                    .replace("&", "§");
            player.sendMessage(prefix + nextQuestMessage);
        }

        // Son de récupération
        String sound = plugin.getConfigManager().getConfig().getString("sounds.rewards-claimed", "ENTITY_PLAYER_LEVELUP");
        if (sound != null && !sound.isEmpty()) {
            try {
                player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
            } catch (Exception e) {
                // Ignore les erreurs de sons
            }
        }

        return true;
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