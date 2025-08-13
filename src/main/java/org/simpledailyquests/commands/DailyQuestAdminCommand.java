package org.simpledailyquests.commands;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.managers.PlayerQuestData;
import org.simpledailyquests.models.Quest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DailyQuestAdminCommand implements CommandExecutor, TabCompleter {

    private final SimpleDailyQuests plugin;

    public DailyQuestAdminCommand(SimpleDailyQuests plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Vérifie les permissions
        if (!sender.hasPermission("simpledailyquests.admin")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 0) {
            showAdminHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                reloadConfig(sender);
                break;

            case "reset":
                handleResetCommand(sender, args);
                break;

            case "give":
                handleGiveCommand(sender, args);
                break;

            case "info":
                handleInfoCommand(sender, args);
                break;

            case "complete":
                handleCompleteCommand(sender, args);
                break;

            case "save":
                saveData(sender);
                break;

            case "debug":
                handleDebugCommand(sender, args);
                break;

            case "stats":
                handleStatsCommand(sender);
                break;

            case "cleanup":
                handleCleanupCommand(sender, args);
                break;

            case "help":
                showAdminHelp(sender);
                break;

            default:
                sender.sendMessage("§cCommande inconnue. Utilisez §e/dqa help §cpour voir les commandes disponibles.");
                break;
        }

        return true;
    }

    /**
     * Recharge les configurations
     */
    private void reloadConfig(CommandSender sender) {
        try {
            plugin.reloadConfigs();
            sender.sendMessage("§a[SimpleDailyQuests] Configurations rechargées avec succès!");
        } catch (Exception e) {
            sender.sendMessage("§c[SimpleDailyQuests] Erreur lors du rechargement: " + e.getMessage());
            plugin.getLogger().severe("Erreur lors du rechargement: " + e.getMessage());
        }
    }

    /**
     * Gère la commande de reset
     */
    private void handleResetCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dqa reset <joueur|all> [rareté]");
            return;
        }

        if (args[1].equalsIgnoreCase("all")) {
            resetAllPlayersQuests(sender, args);
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }

        if (args.length >= 3) {
            // Reset d'une rareté spécifique
            try {
                Quest.QuestRarity rarity = Quest.QuestRarity.valueOf(args[2].toUpperCase());
                resetPlayerQuestsForRarity(sender, target, rarity);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cRareté invalide. Raretés disponibles: COMMUNE, RARE, MYTHIQUE, LEGENDAIRE");
            }
        } else {
            // Reset de toutes les quêtes
            resetAllPlayerQuests(sender, target);
        }
    }

    /**
     * Reset les quêtes de tous les joueurs connectés
     */
    private void resetAllPlayersQuests(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            // Reset d'une rareté spécifique pour tous
            try {
                Quest.QuestRarity rarity = Quest.QuestRarity.valueOf(args[2].toUpperCase());
                int count = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    resetPlayerQuestsForRarity(null, player, rarity);
                    count++;
                }
                sender.sendMessage("§a[SimpleDailyQuests] Quêtes " + rarity.name() + " réinitialisées pour " + count + " joueur(s).");
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cRareté invalide. Raretés disponibles: COMMUNE, RARE, MYTHIQUE, LEGENDAIRE");
            }
        } else {
            // Reset de toutes les quêtes pour tous
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getQuestManager().forceAssignQuests(player);
                count++;
            }
            sender.sendMessage("§a[SimpleDailyQuests] Toutes les quêtes réinitialisées pour " + count + " joueur(s).");
        }
    }

    /**
     * Reset toutes les quêtes d'un joueur
     */
    private void resetAllPlayerQuests(CommandSender sender, Player target) {
        plugin.getQuestManager().forceAssignQuests(target);
        sender.sendMessage("§a[SimpleDailyQuests] Toutes les quêtes de " + target.getName() + " ont été réinitialisées.");
        target.sendMessage("§e[SimpleDailyQuests] Vos quêtes ont été réinitialisées par un administrateur.");
    }

    /**
     * Reset les quêtes d'une rareté spécifique
     */
    private void resetPlayerQuestsForRarity(CommandSender sender, Player target, Quest.QuestRarity rarity) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(target);
        playerData.clearActiveQuests(rarity);
        playerData.setLastReset(rarity, System.currentTimeMillis());

        plugin.getQuestManager().assignNewQuests(target);

        if (sender != null) {
            sender.sendMessage("§a[SimpleDailyQuests] Quêtes " + rarity.name() + " de " + target.getName() + " réinitialisées.");
        }
        target.sendMessage("§e[SimpleDailyQuests] Vos quêtes " + rarity.name() + " ont été réinitialisées.");
    }

    /**
     * Gère la commande de give (donner des quêtes)
     */
    private void handleGiveCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dqa give <joueur|all>");
            return;
        }

        if (args[1].equalsIgnoreCase("all")) {
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getQuestManager().assignNewQuests(player);
                count++;
            }
            sender.sendMessage("§a[SimpleDailyQuests] Nouvelles quêtes assignées à " + count + " joueur(s).");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }

        plugin.getQuestManager().assignNewQuests(target);
        sender.sendMessage("§a[SimpleDailyQuests] Nouvelles quêtes assignées à " + target.getName() + ".");
        target.sendMessage("§e[SimpleDailyQuests] De nouvelles quêtes vous ont été assignées!");
    }

    /**
     * Gère la commande d'information
     */
    private void handleInfoCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dqa info <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }

        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(target);
        showPlayerInfo(sender, target, playerData);
    }

    /**
     * Affiche les informations d'un joueur
     */
    private void showPlayerInfo(CommandSender sender, Player target, PlayerQuestData playerData) {
        sender.sendMessage("§6=== Informations de " + target.getName() + " ===");
        sender.sendMessage("§eQuêtes terminées: §f" + playerData.getTotalCompletedQuests());

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            int active = playerData.getActiveQuestCount(rarity);
            int max = plugin.getConfigManager().getMaxActiveQuests(rarity);
            int completed = playerData.getCompletedQuestCount(rarity);
            long lastReset = playerData.getLastReset(rarity);

            sender.sendMessage("§e" + rarity.name() + ": §f" + active + "/" + max +
                    " actives, " + completed + " terminées");

            if (lastReset > 0) {
                long timeSinceReset = System.currentTimeMillis() - lastReset;
                long hoursSinceReset = timeSinceReset / (1000 * 60 * 60);
                sender.sendMessage("  §7Dernier reset: il y a " + hoursSinceReset + "h");
            }
        }
    }

    /**
     * Gère la commande de completion forcée
     */
    private void handleCompleteCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /dqa complete <joueur> <quest-id>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }

        String questId = args[2];
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(target);
        Quest quest = playerData.getActiveQuestById(questId);

        if (quest == null) {
            sender.sendMessage("§cQuête introuvable: " + questId);
            return;
        }

        // Force la completion
        quest.setProgress(quest.getRequired());
        plugin.getQuestManager().completeQuest(target, quest);

        sender.sendMessage("§a[SimpleDailyQuests] Quête " + questId + " terminée pour " + target.getName() + ".");
    }

    /**
     * Sauvegarde les données
     */
    private void saveData(CommandSender sender) {
        plugin.getPlayerDataManager().savePlayerData();
        sender.sendMessage("§a[SimpleDailyQuests] Données sauvegardées avec succès!");
    }

    /**
     * Gère les commandes de debug
     */
    private void handleDebugCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dqa debug <on|off>");
            return;
        }

        boolean enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
        plugin.getConfigManager().getConfig().set("debug.enabled", enable);

        sender.sendMessage("§a[SimpleDailyQuests] Debug " + (enable ? "activé" : "désactivé") + ".");
    }

    /**
     * Affiche les statistiques globales du serveur
     */
    private void handleStatsCommand(CommandSender sender) {
        sender.sendMessage("§6=== Statistiques SimpleDailyQuests ===");

        int totalPlayers = 0;
        int totalCompletedQuests = 0;
        int totalActiveQuests = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerQuestData data = plugin.getPlayerDataManager().getPlayerData(player);
            totalPlayers++;
            totalCompletedQuests += data.getTotalCompletedQuests();
            totalActiveQuests += data.getAllActiveQuests().size();
        }

        sender.sendMessage("§eJoueurs connectés: §f" + totalPlayers);
        sender.sendMessage("§eQuêtes terminées (total): §f" + totalCompletedQuests);
        sender.sendMessage("§eQuêtes actives (total): §f" + totalActiveQuests);

        if (totalPlayers > 0) {
            double avgCompleted = (double) totalCompletedQuests / totalPlayers;
            sender.sendMessage("§eMoyenne par joueur: §f" + String.format("%.1f", avgCompleted) + " quêtes terminées");
        }
    }

    /**
     * Gère le nettoyage des données
     */
    private void handleCleanupCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /dqa cleanup <expired|offline>");
            return;
        }

        String cleanupType = args[1].toLowerCase();

        switch (cleanupType) {
            case "expired":
                int expiredCount = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerQuestData data = plugin.getPlayerDataManager().getPlayerData(player);
                    List<Quest> expired = data.removeExpiredQuests();
                    expiredCount += expired.size();
                }
                sender.sendMessage("§a[SimpleDailyQuests] " + expiredCount + " quête(s) expirée(s) supprimée(s).");
                break;

            case "offline":
                // Cette fonctionnalité nécessiterait une gestion plus complexe des données hors ligne
                sender.sendMessage("§c[SimpleDailyQuests] Fonctionnalité non implémentée pour cette version.");
                break;

            default:
                sender.sendMessage("§cType de nettoyage invalide. Utilisez: expired, offline");
                break;
        }
    }

    /**
     * Affiche l'aide administrative
     */
    private void showAdminHelp(CommandSender sender) {
        sender.sendMessage("§6=== Aide Administration SimpleDailyQuests ===");
        sender.sendMessage("§e/dqa reload §7- Recharge les configurations");
        sender.sendMessage("§e/dqa reset <joueur|all> [rareté] §7- Reset les quêtes");
        sender.sendMessage("§e/dqa give <joueur|all> §7- Assigne de nouvelles quêtes");
        sender.sendMessage("§e/dqa info <joueur> §7- Informations sur un joueur");
        sender.sendMessage("§e/dqa complete <joueur> <quest-id> §7- Force la completion");
        sender.sendMessage("§e/dqa save §7- Sauvegarde les données");
        sender.sendMessage("§e/dqa debug <on|off> §7- Active/désactive le debug");
        sender.sendMessage("§e/dqa stats §7- Statistiques globales");
        sender.sendMessage("§e/dqa cleanup <expired|offline> §7- Nettoie les données");
        sender.sendMessage("§e/dqa help §7- Affiche cette aide");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("simpledailyquests.admin")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Sous-commandes principales
            List<String> subCommands = Arrays.asList("reload", "reset", "give", "info", "complete",
                    "save", "debug", "stats", "cleanup", "help");
            String input = args[0].toLowerCase();

            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            // Noms des joueurs pour la plupart des commandes
            if (Arrays.asList("reset", "give", "info", "complete").contains(subCommand)) {
                String input = args[1].toLowerCase();
                completions.add("all");
                completions.addAll(Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList()));
            } else if (subCommand.equals("debug")) {
                completions.addAll(Arrays.asList("on", "off"));
            } else if (subCommand.equals("cleanup")) {
                completions.addAll(Arrays.asList("expired", "offline"));
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if (Arrays.asList("reset", "give").contains(subCommand)) {
                // Raretés pour les commandes reset et give
                String input = args[2].toLowerCase();
                for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
                    String rarityName = rarity.name().toLowerCase();
                    if (rarityName.startsWith(input)) {
                        completions.add(rarityName);
                    }
                }
            }
        }

        return completions;
    }
}