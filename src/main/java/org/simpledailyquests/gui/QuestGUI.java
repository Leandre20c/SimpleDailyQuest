package org.simpledailyquests.gui;

import org.simpledailyquests.SimpleDailyQuests;
import org.simpledailyquests.managers.PlayerQuestData;
import org.simpledailyquests.models.Quest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class QuestGUI implements Listener {

    private final SimpleDailyQuests plugin;

    public QuestGUI(SimpleDailyQuests plugin) {
        this.plugin = plugin;
        // Enregistrer les événements
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre le menu principal des quêtes
     */
    public void openMainMenu(Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);

        // Récupération de la configuration GUI
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("gui");
        int size = guiConfig.getInt("size", 27);
        String title = guiConfig.getString("title", "&6Mes Quêtes Journalières");
        title = title.replace("&", "§");

        // Création de l'inventaire
        Inventory inventory = Bukkit.createInventory(null, size, title);

        // Remplissage avec l'item de décoration
        fillWithFillerItems(inventory, guiConfig);

        // Ajout des items de quêtes pour chaque rareté
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            addRarityItem(inventory, player, playerData, rarity, guiConfig);
        }

        // Ouverture du menu
        player.openInventory(inventory);
    }

    /**
     * Remplit l'inventaire avec les items de décoration
     */
    private void fillWithFillerItems(Inventory inventory, ConfigurationSection guiConfig) {
        ConfigurationSection fillerConfig = guiConfig.getConfigurationSection("filler-item");
        if (fillerConfig == null) return;

        String materialName = fillerConfig.getString("material", "GRAY_STAINED_GLASS_PANE");
        String name = fillerConfig.getString("name", " ");

        try {
            Material material = Material.valueOf(materialName);
            ItemStack fillerItem = new ItemStack(material);
            ItemMeta meta = fillerItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name.replace("&", "§"));
                fillerItem.setItemMeta(meta);
            }

            // Remplit tous les slots sauf ceux réservés aux quêtes
            for (int i = 0; i < inventory.getSize(); i++) {
                if (!isQuestSlot(i, guiConfig)) {
                    inventory.setItem(i, fillerItem);
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Matériau invalide pour filler-item: " + materialName);
        }
    }

    /**
     * Vérifie si un slot est réservé pour une quête
     */
    private boolean isQuestSlot(int slot, ConfigurationSection guiConfig) {
        ConfigurationSection slotsConfig = guiConfig.getConfigurationSection("slots");
        if (slotsConfig == null) return false;

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            int raritySlot = slotsConfig.getInt(rarity.name().toLowerCase(), -1);
            if (slot == raritySlot) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ajoute l'item d'une rareté spécifique
     */
    private void addRarityItem(Inventory inventory, Player player, PlayerQuestData playerData,
                               Quest.QuestRarity rarity, ConfigurationSection guiConfig) {

        // Récupération de la position
        ConfigurationSection slotsConfig = guiConfig.getConfigurationSection("slots");
        if (slotsConfig == null) return;

        int slot = slotsConfig.getInt(rarity.name().toLowerCase(), -1);
        if (slot == -1 || slot >= inventory.getSize()) return;

        // Récupération des quêtes actives pour cette rareté
        List<Quest> activeQuests = playerData.getActiveQuests(rarity);

        if (activeQuests.isEmpty()) {
            // Aucune quête active - item grisé
            addNoQuestItem(inventory, slot, rarity, guiConfig, player);
        } else {
            // Quête active - affichage de la première quête
            Quest quest = activeQuests.get(0);
            addQuestItem(inventory, slot, quest, rarity, guiConfig, player);
        }
    }

    /**
     * Ajoute un item pour une quête active
     */
    private void addQuestItem(Inventory inventory, int slot, Quest quest, Quest.QuestRarity rarity,
                              ConfigurationSection guiConfig, Player player) {

        ConfigurationSection rarityConfig = guiConfig.getConfigurationSection("rarity-items." + rarity.name().toLowerCase());
        if (rarityConfig == null) return;

        try {
            // Création de l'item
            String materialName = rarityConfig.getString("material", "STONE");
            Material material = Material.valueOf(materialName);
            ItemStack item = new ItemStack(material);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Nom de l'item
                String name = rarityConfig.getString("name", "Quête");
                meta.setDisplayName(name.replace("&", "§"));

                // Lore de l'item
                List<String> lore = new ArrayList<>();
                List<String> configLore = rarityConfig.getStringList("lore");

                for (String line : configLore) {
                    if (line.trim().isEmpty()) {
                        lore.add("");
                        continue;
                    }

                    String processedLine = line
                            .replace("&", "§")
                            .replace("%quest%", quest.getDescription())
                            .replace("{progress}", String.valueOf(quest.getProgress()))
                            .replace("{required}", String.valueOf(quest.getRequired()))
                            .replace("{percentage}", String.format("%.1f", quest.getProgressPercentage()))
                            .replace("{time-left}", getTimeLeft(quest, player));

                    lore.add(processedLine);
                }

                // Si la quête est terminée, utilise la lore spéciale
                if (quest.isCompleted()) {
                    List<String> completedLore = guiConfig.getStringList("quest-completed-lore");
                    if (!completedLore.isEmpty()) {
                        lore.clear();
                        lore.add("§6Quête : §f" + quest.getDescription());
                        lore.add("");
                        for (String line : completedLore) {
                            lore.add(line.replace("&", "§"));
                        }

                        // Ajout d'une ligne pour indiquer la vérification d'inventaire
                        lore.add("");
                        lore.add("§7§o(2 slots libres requis)");
                    }

                    // Change le matériau pour indiquer que c'est terminé
                    item.setType(Material.LIME_CONCRETE_POWDER);
                }

                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            inventory.setItem(slot, item);

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Matériau invalide pour " + rarity.name() + ": " + rarityConfig.getString("material"));
        }
    }

    /**
     * Ajoute un item quand aucune quête n'est active
     */
    private void addNoQuestItem(Inventory inventory, int slot, Quest.QuestRarity rarity,
                                ConfigurationSection guiConfig, Player player) {

        try {
            // Item grisé (barrière)
            ItemStack item = new ItemStack(Material.BARRIER);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7Aucune quête " + rarity.name().toLowerCase());

                List<String> lore = new ArrayList<>();

                // Calcule le temps restant avant la prochaine rotation de quête
                String timeUntilNext = getTimeUntilNextRotation(rarity, player);

                if (timeUntilNext.equals("Bientôt")) {
                    lore.add("§aUne nouvelle quête arrive bientôt !");
                    lore.add("§7Reconnectez-vous ou attendez quelques minutes.");
                } else if (timeUntilNext.equals("Jamais") || timeUntilNext.equals("Inconnu")) {
                    lore.add("§7Aucune quête disponible.");
                    lore.add("§7Contactez un administrateur.");
                } else {
                    lore.add("§7Vous avez terminé votre quête.");
                    lore.add("");
                    lore.add("§7Prochaine quête dans:");
                    lore.add("§e" + timeUntilNext);
                }

                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            inventory.setItem(slot, item);

        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la création de l'item 'no quest' pour " + rarity.name());
        }
    }

    /**
     * Calcule le temps jusqu'à la prochaine rotation de quête
     */
    private String getTimeUntilNextRotation(Quest.QuestRarity rarity, Player player) {
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        long lastReset = playerData.getLastReset(rarity);

        if (lastReset == 0) {
            return "Jamais"; // Pas encore de timer démarré
        }

        long currentTime = System.currentTimeMillis();
        long rotationInterval = plugin.getConfigManager().getResetHours(rarity) * 60 * 60 * 1000L;

        long timeUntilNext = rotationInterval - (currentTime - lastReset);

        if (timeUntilNext <= 60000) { // Moins de 1 minute
            return "Bientôt";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(timeUntilNext);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeUntilNext) % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    /**
     * Calcule le temps restant avant la prochaine rotation de quête
     */
    private String getTimeLeft(Quest quest, Player player) {
        // Pour une quête active, on affiche le temps avant la prochaine rotation
        // Pas le temps d'expiration de la quête elle-même

        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        long lastReset = playerData.getLastReset(quest.getRarity());

        if (lastReset == 0) {
            return "Nouveau"; // Timer pas encore démarré
        }

        long currentTime = System.currentTimeMillis();
        long rotationInterval = plugin.getConfigManager().getResetHours(quest.getRarity()) * 60 * 60 * 1000L;

        long timeUntilRotation = rotationInterval - (currentTime - lastReset);

        if (timeUntilRotation <= 0) {
            return "Rotation imminente";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(timeUntilRotation);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeUntilRotation) % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    /**
     * Gère les clics dans l'inventaire
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // Vérifie si c'est notre menu
        String title = event.getView().getTitle();
        String expectedTitle = plugin.getConfigManager().getConfig()
                .getString("gui.title", "&6Mes Quêtes Journalières")
                .replace("&", "§");

        if (!title.equals(expectedTitle)) return;

        event.setCancelled(true); // Empêche la prise d'items

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Détermine quelle rareté a été cliquée
        Quest.QuestRarity clickedRarity = getRarityFromSlot(event.getSlot());
        if (clickedRarity == null) return;

        // Vérifie s'il y a une quête terminée à récupérer
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        List<Quest> activeQuests = playerData.getActiveQuests(clickedRarity);

        if (!activeQuests.isEmpty()) {
            Quest quest = activeQuests.get(0);
            if (quest.isCompleted()) {
                // Tente de récupérer les récompenses avec vérification d'inventaire
                boolean success = plugin.getQuestManager().claimQuestRewards(player, quest);

                if (success) {
                    // Son de récupération
                    String sound = plugin.getConfigManager().getConfig().getString("sounds.rewards-claimed", "ENTITY_PLAYER_LEVELUP");
                    if (sound != null && !sound.isEmpty()) {
                        try {
                            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                        } catch (Exception e) {
                            // Ignore les erreurs de son
                        }
                    }

                    // Met à jour le menu
                    openMainMenu(player);
                } else {
                    // Le message d'erreur est déjà envoyé par claimQuestRewards
                    // Son d'erreur
                    try {
                        player.playSound(player.getLocation(), "ENTITY_VILLAGER_NO", 1.0f, 1.0f);
                    } catch (Exception e) {
                        // Ignore les erreurs de son
                    }
                }
            } else {
                // Quête pas encore terminée, affiche le progrès
                String message = plugin.getConfigManager().getMessagesConfig()
                        .getString("quest-progress", "&7Progression: &e{progress}&7/&e{required} &7({percentage}%)")
                        .replace("{progress}", String.valueOf(quest.getProgress()))
                        .replace("{required}", String.valueOf(quest.getRequired()))
                        .replace("{percentage}", String.format("%.1f", quest.getProgressPercentage()))
                        .replace("&", "§");

                String prefix = plugin.getConfigManager().getMessagesConfig().getString("prefix", "");
                player.sendMessage(prefix + message);
            }
        }

        // Son de clic
        String clickSound = plugin.getConfigManager().getConfig().getString("sounds.menu-click");
        if (clickSound != null && !clickSound.isEmpty()) {
            try {
                player.playSound(player.getLocation(), clickSound, 0.5f, 1.0f);
            } catch (Exception e) {
                // Ignore les erreurs de son
            }
        }
    }

    /**
     * Détermine la rareté basée sur le slot cliqué
     */
    private Quest.QuestRarity getRarityFromSlot(int slot) {
        ConfigurationSection slotsConfig = plugin.getConfigManager().getConfig()
                .getConfigurationSection("gui.slots");
        if (slotsConfig == null) return null;

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            int raritySlot = slotsConfig.getInt(rarity.name().toLowerCase(), -1);
            if (slot == raritySlot) {
                return rarity;
            }
        }
        return null;
    }
}