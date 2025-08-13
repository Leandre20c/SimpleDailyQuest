package org.simpledailyquests.commands;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.gui.QuestGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DailyQuestCommand implements CommandExecutor, TabCompleter {

    private final SimpleDailyQuests plugin;

    public DailyQuestCommand(SimpleDailyQuests plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur.");
            return true;
        }

        Player player = (Player) sender;

        // Vérifie les permissions
        if (!player.hasPermission("simpledailyquests.use")) {
            player.sendMessage(getPrefix() + "§cVous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        // Si aucun argument, ouvre le menu principal
        if (args.length == 0) {
            openQuestMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "menu":
            case "gui":
                openQuestMenu(player);
                break;

            case "scoreboard":
                toggleScoreboard(player);
                break;

            case "status":
                showQuestStatus(player);
                break;

            case "help":
                showHelp(player);
                break;

            default:
                // Par défaut, ouvre le menu
                openQuestMenu(player);
                break;
        }

        return true;
    }

    /**
     * Ouvre le menu des quêtes pour le joueur
     */
    private void openQuestMenu(Player player) {
        // Vérifie et met à jour les quêtes avant d'ouvrir le menu
        plugin.getQuestManager().checkAndResetPlayerQuests(player);

        // Ouvre l'interface graphique
        QuestGUI questGUI = new QuestGUI(plugin);
        questGUI.openMainMenu(player);

        // Son d'ouverture du menu
        String sound = plugin.getConfigManager().getConfig().getString("sounds.menu-open");
        if (sound != null && !sound.isEmpty()) {
            try {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception e) {
                // Ignore les erreurs de son
            }
        }
    }

    /**
     * Toggle le scoreboard des quêtes
     */
    private void toggleScoreboard(Player player) {
        boolean isEnabled = plugin.getScoreboardManager().toggleScoreboard(player);

        if (isEnabled) {
            player.sendMessage(getPrefix() + "§aScoreboard des quêtes activé!");
        } else {
            player.sendMessage(getPrefix() + "§cScoreboard des quêtes désactivé!");
        }
    }

    /**
     * Affiche le statut des quêtes en texte
     */
    private void showQuestStatus(Player player) {
        // Vérifie et met à jour les quêtes
        plugin.getQuestManager().checkAndResetPlayerQuests(player);

        // Obtient et affiche le statut
        String status = plugin.getQuestManager().getQuestStatus(player);
        player.sendMessage(status);
    }

    /**
     * Affiche l'aide de la commande
     */
    private void showHelp(Player player) {
        player.sendMessage("§6=== Aide SimpleDailyQuests ===");
        player.sendMessage("§e/dq §7- Ouvre le menu des quêtes");
        player.sendMessage("§e/dq scoreboard §7- Active/désactive le scoreboard");
        player.sendMessage("§e/dq status §7- Affiche vos quêtes en cours");
        player.sendMessage("§e/dq help §7- Affiche cette aide");
        player.sendMessage("");
        player.sendMessage("§7Les quêtes se réinitialisent automatiquement:");
        player.sendMessage("§8• §aCommunes: toutes les 12h");
        player.sendMessage("§8• §9Rares: toutes les 24h");
        player.sendMessage("§8• §dMythiques: toutes les 72h");
        player.sendMessage("§8• §6Légendaires: toutes les 168h (7 jours)");
        player.sendMessage("");
        player.sendMessage("§7Aliases: §e/dailyquest, /quests, /q, /quest, /quete, /quetes");
    }

    /**
     * Obtient le préfixe des messages
     */
    private String getPrefix() {
        return plugin.getConfigManager().getMessagesConfig().getString("prefix", "§8[§6SimpleDailyQuests§8] ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        Player player = (Player) sender;

        // Vérifie les permissions
        if (!player.hasPermission("simpledailyquests.use")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Première argument - sous-commandes disponibles
            List<String> subCommands = Arrays.asList("menu", "scoreboard", "status", "help");
            String input = args[0].toLowerCase();

            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        }

        return completions;
    }
}