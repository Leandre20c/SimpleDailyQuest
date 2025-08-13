package org.simpledailyquests.listeners;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.models.Quest;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class PlayerListener implements Listener {

    private final SimpleDailyQuests plugin;

    public PlayerListener(SimpleDailyQuests plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère la connexion des joueurs - charge les données et vérifie les quêtes
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Charge les données du joueur immédiatement
        plugin.getPlayerDataManager().getPlayerData(player);

        // Vérifie et assigne les quêtes avec un délai pour éviter les problèmes de chargement
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getQuestManager().checkAndResetPlayerQuests(player);

                // Debug log si activé
                if (plugin.getConfigManager().getConfig().getBoolean("debug.enabled", false)) {
                    plugin.getLogger().info("Quêtes vérifiées pour " + player.getName());
                }
            }
        }, 20L); // 1 seconde de délai
    }

    /**
     * Gère la déconnexion des joueurs - nettoie les scoreboards
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Nettoie le scoreboard du joueur
        plugin.getScoreboardManager().handlePlayerLeave(player);

        // Sauvegarde les données du joueur
        plugin.getPlayerDataManager().savePlayerData();
    }

    /**
     * Gère le placement de blocs - marque les blocs avec des métadonnées
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Ignore les joueurs en créatif ou spectateur
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Marque le bloc comme placé par un joueur
        block.setMetadata("placed_by_player", new FixedMetadataValue(plugin, true));

        // Debug log si activé
        if (plugin.getConfigManager().getConfig().getBoolean("debug.log-block-tracking", false)) {
            plugin.getLogger().info("Bloc marqué comme placé par joueur: " + block.getType() + " à " + block.getLocation());
        }
    }

    /**
     * Gère la destruction de blocs - quêtes de mine avec vérifications
     * IMPORTANT: Utilise HIGHEST priority pour vérifier AVANT que d'autres plugins annulent l'événement
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Ignore les joueurs en créatif ou spectateur
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Vérifie si le monde est autorisé pour les quêtes
        if (!isWorldAllowed(block.getWorld().getName())) {
            if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
                plugin.getLogger().info("Bloc ignoré (monde non autorisé): " + block.getWorld().getName());
            }
            return;
        }

        // Vérifie si le bloc était placé par un joueur
        if (block.hasMetadata("placed_by_player")) {
            if (plugin.getConfigManager().getConfig().getBoolean("debug.log-block-tracking", false)) {
                plugin.getLogger().info("Bloc ignoré (placé par joueur): " + block.getType());
            }
            return;
        }

        // Traite d'abord la quête, puis vérifie si l'événement a été annulé
        Material blockType = block.getType();
        String blockTypeName = blockType.name().toLowerCase();

        // Utilise un délai pour vérifier si l'événement sera annulé par d'autres plugins
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Si l'événement a été annulé par un autre plugin, on ne compte pas le bloc
            if (event.isCancelled()) {
                if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
                    plugin.getLogger().info("Bloc ignoré (événement annulé): " + blockType);
                }
                return;
            }

            // Met à jour le progrès des quêtes de mine
            plugin.getQuestManager().processQuestProgress(player, Quest.QuestType.MINE, blockTypeName, 1);

            // Debug log si activé
            if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
                plugin.getLogger().info(player.getName() + " a miné (valide): " + blockTypeName);
            }
        }, 1L); // 1 tick de délai
    }

    /**
     * Gère les pistons qui poussent des blocs
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        List<Block> blocks = event.getBlocks();
        Map<Block, Boolean> playerPlacedBlocks = new HashMap<>();

        // Sauvegarde les métadonnées des blocs qui vont être déplacés
        for (Block block : blocks) {
            if (block.hasMetadata("placed_by_player")) {
                playerPlacedBlocks.put(block, true);
            }
        }

        // Applique les métadonnées aux nouvelles positions après le déplacement
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < blocks.size(); i++) {
                Block originalBlock = blocks.get(i);
                if (playerPlacedBlocks.containsKey(originalBlock)) {
                    // Calcule la nouvelle position du bloc
                    Block newBlock = originalBlock.getRelative(event.getDirection());
                    newBlock.setMetadata("placed_by_player", new FixedMetadataValue(plugin, true));

                    if (plugin.getConfigManager().getConfig().getBoolean("debug.log-block-tracking", false)) {
                        plugin.getLogger().info("Métadonnées transférées (piston extend): " + newBlock.getLocation());
                    }
                }
            }
        }, 1L);
    }

    /**
     * Gère les pistons qui tirent des blocs
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        List<Block> blocks = event.getBlocks();
        Map<Block, Boolean> playerPlacedBlocks = new HashMap<>();

        // Sauvegarde les métadonnées des blocs qui vont être déplacés
        for (Block block : blocks) {
            if (block.hasMetadata("placed_by_player")) {
                playerPlacedBlocks.put(block, true);
            }
        }

        // Applique les métadonnées aux nouvelles positions après le déplacement
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < blocks.size(); i++) {
                Block originalBlock = blocks.get(i);
                if (playerPlacedBlocks.containsKey(originalBlock)) {
                    // Pour la rétraction, les blocs se déplacent vers le piston
                    Block newBlock = originalBlock.getRelative(event.getDirection());
                    newBlock.setMetadata("placed_by_player", new FixedMetadataValue(plugin, true));

                    if (plugin.getConfigManager().getConfig().getBoolean("debug.log-block-tracking", false)) {
                        plugin.getLogger().info("Métadonnées transférées (piston retract): " + newBlock.getLocation());
                    }
                }
            }
        }, 1L);
    }

    /**
     * Vérifie si le monde est autorisé pour les quêtes
     */
    private boolean isWorldAllowed(String worldName) {
        List<String> allowedWorlds = plugin.getConfigManager().getConfig().getStringList("quest-settings.allowed-worlds");

        // Si la liste est vide, tous les mondes sont autorisés
        if (allowedWorlds.isEmpty()) {
            return true;
        }

        // Vérifie si le monde est dans la liste (insensible à la casse)
        return allowedWorlds.stream().anyMatch(world -> world.equalsIgnoreCase(worldName));
    }

    /**
     * Gère la mort des entités - quêtes de kill
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDeath(EntityDeathEvent event) {
        // Vérifie que l'entité a été tuée par un joueur
        if (!(event.getEntity().getKiller() instanceof Player)) {
            return;
        }

        Player player = event.getEntity().getKiller();

        // Ignore les joueurs en créatif ou spectateur
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Vérifie si le monde est autorisé pour les quêtes
        if (!isWorldAllowed(event.getEntity().getWorld().getName())) {
            return;
        }

        String entityType = event.getEntity().getType().name().toLowerCase();

        // Utilise un délai pour vérifier si l'événement sera annulé
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.isCancelled()) {
                if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
                    plugin.getLogger().info("Kill ignoré (événement annulé): " + entityType);
                }
                return;
            }

            // Met à jour le progrès des quêtes de kill
            plugin.getQuestManager().processQuestProgress(player, Quest.QuestType.KILL, entityType, 1);

            // Debug log si activé
            if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
                plugin.getLogger().info(player.getName() + " a tué (valide): " + entityType);
            }
        }, 1L);
    }

    /**
     * Gère le craft d'items - quêtes de craft
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        // Vérifie que c'est un joueur qui craft
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Ignore les joueurs en spectateur
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Vérifie si le monde est autorisé pour les quêtes
        if (!isWorldAllowed(player.getWorld().getName())) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (result == null) {
            return;
        }

        String itemType = result.getType().name().toLowerCase();
        int amount = result.getAmount();

        // Gère le shift-click pour crafter plusieurs items d'un coup
        if (event.isShiftClick()) {
            amount = calculateShiftClickAmount(event, result);
        }

        // Met à jour le progrès des quêtes de craft
        plugin.getQuestManager().processQuestProgress(player, Quest.QuestType.CRAFT, itemType, amount);

        // Debug log si activé
        if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
            plugin.getLogger().info(player.getName() + " a crafté: " + amount + "x " + itemType);
        }
    }

    /**
     * Gère la pêche - quêtes de fish
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        // Ignore les joueurs en spectateur
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Vérifie que le joueur a effectivement attrapé quelque chose
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        // Vérifie si le monde est autorisé pour les quêtes
        if (!isWorldAllowed(player.getWorld().getName())) {
            return;
        }

        // Récupère l'item attrapé
        String fishType = "fish"; // Type par défaut
        int amount = 1; // Quantité par défaut

        if (event.getCaught() instanceof Item) {
            Item caughtItem = (Item) event.getCaught();
            ItemStack itemStack = caughtItem.getItemStack();

            if (itemStack != null) {
                fishType = itemStack.getType().name().toLowerCase();
                amount = itemStack.getAmount();
            }
        }

        // Utilise un délai pour vérifier si l'événement sera annulé
        final String finalFishType = fishType;
        final int finalAmount = amount;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.isCancelled()) {
                if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
                    plugin.getLogger().info("Pêche ignorée (événement annulé): " + finalFishType);
                }
                return;
            }

            // Met à jour le progrès des quêtes de pêche
            plugin.getQuestManager().processQuestProgress(player, Quest.QuestType.FISH, finalFishType, finalAmount);

            // Debug log si activé
            if (plugin.getConfigManager().getConfig().getBoolean("debug.log-quest-progress", false)) {
                plugin.getLogger().info(player.getName() + " a pêché (valide): " + finalAmount + "x " + finalFishType);
            }
        }, 1L);
    }

    /**
     * Calcule le nombre d'items craftés lors d'un shift-click
     */
    private int calculateShiftClickAmount(CraftItemEvent event, ItemStack result) {
        try {
            ItemStack[] matrix = event.getInventory().getMatrix();
            int minStackSize = Integer.MAX_VALUE;
            int ingredientCount = 0;

            // Trouve la stack la plus petite parmi les ingrédients
            for (ItemStack ingredient : matrix) {
                if (ingredient != null && ingredient.getType() != Material.AIR) {
                    minStackSize = Math.min(minStackSize, ingredient.getAmount());
                    ingredientCount++;
                }
            }

            // Si aucun ingrédient trouvé ou erreur, retourne la quantité de base
            if (minStackSize == Integer.MAX_VALUE || ingredientCount == 0) {
                return result.getAmount();
            }

            // Calcule combien de fois on peut crafter avec les ingrédients disponibles
            int maxCrafts = minStackSize;
            int totalAmount = maxCrafts * result.getAmount();

            // Limite à 64 items pour éviter les valeurs aberrantes
            return Math.min(totalAmount, 64);

        } catch (Exception e) {
            // En cas d'erreur, retourne la quantité de base
            plugin.getLogger().warning("Erreur lors du calcul du shift-click craft: " + e.getMessage());
            return result.getAmount();
        }
    }
}