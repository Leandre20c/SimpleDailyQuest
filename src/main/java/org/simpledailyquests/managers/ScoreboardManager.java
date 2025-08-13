package org.simpledailyquests.managers;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.models.Quest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class ScoreboardManager {

    private final SimpleDailyQuests plugin;
    private final Set<UUID> enabledPlayers;
    private final Map<UUID, Scoreboard> playerScoreboards;
    private BukkitRunnable updateTask;

    public ScoreboardManager(SimpleDailyQuests plugin) {
        this.plugin = plugin;
        this.enabledPlayers = new HashSet<>();
        this.playerScoreboards = new HashMap<>();
        startUpdateTask();
    }

    /**
     * Toggle le scoreboard pour un joueur
     */
    public boolean toggleScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (enabledPlayers.contains(playerUUID)) {
            // Désactive le scoreboard
            disableScoreboard(player);
            return false;
        } else {
            // Active le scoreboard
            enableScoreboard(player);
            return true;
        }
    }

    /**
     * Active le scoreboard pour un joueur
     */
    public void enableScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();
        enabledPlayers.add(playerUUID);

        // Crée et affiche le scoreboard
        createScoreboard(player);
        updateScoreboard(player);
    }

    /**
     * Désactive le scoreboard pour un joueur
     */
    public void disableScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();
        enabledPlayers.remove(playerUUID);
        playerScoreboards.remove(playerUUID);

        // Remet le scoreboard par défaut du serveur
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    /**
     * Crée un scoreboard pour un joueur
     */
    private void createScoreboard(Player player) {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("quests", "dummy", "§2§lQuêtes");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        playerScoreboards.put(player.getUniqueId(), scoreboard);
        player.setScoreboard(scoreboard);
    }

    /**
     * Met à jour le scoreboard d'un joueur (format simple avec lignes vides)
     */
    public void updateScoreboard(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!enabledPlayers.contains(playerUUID)) {
            return;
        }

        Scoreboard scoreboard = playerScoreboards.get(playerUUID);
        if (scoreboard == null) {
            createScoreboard(player);
            scoreboard = playerScoreboards.get(playerUUID);
        }

        Objective objective = scoreboard.getObjective("quests");
        if (objective == null) return;

        // Efface les scores existants
        clearScoreboard(objective);

        // Récupère les données du joueur
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        int line = 15; // Position de départ (en haut)
        int emptyLineCounter = 0; // Pour créer des lignes vides uniques
        boolean hasDisplayedCategory = false; // Pour savoir si on a affiché une catégorie

        // Affiche les quêtes pour chaque rareté
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            List<Quest> activeQuests = playerData.getActiveQuests(rarity);

            if (!activeQuests.isEmpty()) {
                // Si on a déjà affiché une catégorie, ajouter une ligne vide
                if (hasDisplayedCategory) {
                    String emptyLine = createEmptyLine(emptyLineCounter++);
                    objective.getScore(emptyLine).setScore(line--);
                }

                // Titre de la rareté
                String rarityTitle = getRarityTitle(rarity);
                objective.getScore(rarityTitle).setScore(line--);

                // Affiche chaque quête (1 ligne par quête avec progression)
                for (Quest quest : activeQuests) {
                    String questLine = formatCombinedQuestLine(quest);
                    objective.getScore(questLine).setScore(line--);
                }

                hasDisplayedCategory = true;
            }
        }
    }

    /**
     * Crée une ligne vide unique pour le scoreboard
     */
    private String createEmptyLine(int counter) {
        // Utilise des codes de couleur pour créer des lignes vides uniques
        String[] emptyLines = {
                "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9",
                "§a", "§b", "§c", "§d", "§e", "§f",
                "§0§0", "§1§1", "§2§2", "§3§3", "§4§4"
        };

        if (counter < emptyLines.length) {
            return emptyLines[counter];
        } else {
            // Fallback pour plus de lignes vides si nécessaire
            return "§" + (counter % 16) + "§" + ((counter / 16) % 16);
        }
    }

    /**
     * Efface toutes les entrées du scoreboard
     */
    private void clearScoreboard(Objective objective) {
        for (String entry : objective.getScoreboard().getEntries()) {
            objective.getScoreboard().resetScores(entry);
        }
    }

    /**
     * Obtient le titre coloré d'une rareté
     */
    private String getRarityTitle(Quest.QuestRarity rarity) {
        switch (rarity) {
            case COMMUNE:
                return "§a§l● COMMUNE";
            case RARE:
                return "§9§l● RARE";
            case MYTHIQUE:
                return "§d§l● MYTHIQUE";
            case LEGENDAIRE:
                return "§6§l✦ LÉGENDAIRE";
            default:
                return "§7§l● INCONNUES";
        }
    }

    /**
     * Formate une ligne combinée quête + progression
     */
    private String formatCombinedQuestLine(Quest quest) {
        String description = quest.getDescription();
        int progress = quest.getProgress();
        int required = quest.getRequired();

        // Calcule la longueur disponible pour la description
        String progressText = "(" + progress + "/" + required + ")";
        int maxDescLength = 35 - progressText.length(); // Limite à ~40 caractères total

        // Raccourcit la description si nécessaire
        if (description.length() > maxDescLength) {
            description = description.substring(0, maxDescLength - 3) + "...";
        }

        // Symbole selon l'état de la quête
        String status;
        if (quest.isCompleted()) {
            status = "§a✓";
        } else {
            status = "§7●";
        }

        // Format: "● Description (progress/required)"
        return status + " §7" + description + " §f" + progressText;
    }

    /**
     * Formate une ligne de progression pour le scoreboard
     */
    private String formatProgressLine(Quest quest) {
        int progress = quest.getProgress();
        int required = quest.getRequired();
        double percentage = quest.getProgressPercentage();

        String progressBar = createProgressBar(percentage);

        return "§8  " + progressBar + " §7" + progress + "/" + required;
    }

    /**
     * Crée une barre de progression visuelle
     */
    private String createProgressBar(double percentage) {
        int filledBars = (int) Math.round(percentage / 10); // 10 barres au total
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            if (i < filledBars) {
                bar.append("§a█");
            } else {
                bar.append("§7█");
            }
        }

        return bar.toString();
    }

    /**
     * Démarre la tâche de mise à jour automatique
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID playerUUID : enabledPlayers) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        updateScoreboard(player);
                    }
                }
            }
        };

        // Met à jour toutes les 5 secondes
        updateTask.runTaskTimer(plugin, 20L * 5, 20L * 5);
    }

    /**
     * Arrête la tâche de mise à jour
     */
    public void stopUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
    }

    /**
     * Vérifie si un joueur a le scoreboard activé
     */
    public boolean hasScoreboardEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    /**
     * Met à jour le scoreboard d'un joueur spécifique si activé
     */
    public void updatePlayerScoreboard(Player player) {
        if (hasScoreboardEnabled(player)) {
            updateScoreboard(player);
        }
    }

    /**
     * Supprime tous les scoreboards (appelé lors de l'arrêt du plugin)
     */
    public void removeAllScoreboards() {
        stopUpdateTask();

        for (UUID playerUUID : new HashSet<>(enabledPlayers)) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                disableScoreboard(player);
            }
        }

        enabledPlayers.clear();
        playerScoreboards.clear();
    }

    /**
     * Gère la déconnexion d'un joueur
     */
    public void handlePlayerLeave(Player player) {
        UUID playerUUID = player.getUniqueId();
        enabledPlayers.remove(playerUUID);
        playerScoreboards.remove(playerUUID);
    }
}