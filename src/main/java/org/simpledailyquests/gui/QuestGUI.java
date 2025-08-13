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
            addNoQuestItem(inventory, slot, rarity, guiConfig);
        } else {
            // Quête active - affichage de la première quête
            Quest quest = activeQuests.get(0);
            addQuestItem(inventory, slot, quest, rarity, guiConfig);
        }
    }

    /**
     * Ajoute un item pour une quête active
     */
    private void addQuestItem(Inventory inventory, int slot, Quest quest, Quest.QuestRarity rarity,
                              ConfigurationSection guiConfig) {

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
                            .replace("{time-left}", getTimeLeft(quest));

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
                    }
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
                                ConfigurationSection guiConfig) {

        ConfigurationSection rarityConfig = guiConfig.getConfigurationSection("rarity-items." + rarity.name().toLowerCase());
        if (rarityConfig == null) return;

        try {
            // Item grisé (barrière ou verre gris)
            ItemStack item = new ItemStack(Material.BARRIER);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§7Aucune quête " + rarity.name().toLowerCase());

                List<String> lore = new ArrayList<>();
                lore.add("§7Aucune quête active pour cette rareté.");
                lore.add("");
                lore.add("§7Prochaine réinitialisation:");
                lore.add("§e" + getNextResetTime(rarity));

                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            inventory.setItem(slot, item);

        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la création de l'item 'no quest' pour " + rarity.name());
        }
    }

    /**
     * Calcule le temps restant pour une quête
     */
    private String getTimeLeft(Quest quest) {
        long currentTime = System.currentTimeMillis();
        long questTime = quest.getAssignedTime();
        long elapsedTime = currentTime - questTime;

        // Calcule le temps de reset selon la rareté
        int resetHours = plugin.getConfigManager().getResetHours(quest.getRarity());
        long resetTimeMs = resetHours * 60 * 60 * 1000L;

        long timeLeft = resetTimeMs - elapsedTime;

        if (timeLeft <= 0) {
            return "Expirée";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(timeLeft);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    /**
     * Calcule le temps jusqu'à la prochaine réinitialisation
     */
    private String getNextResetTime(Quest.QuestRarity rarity) {
        int resetHours = plugin.getConfigManager().getResetHours(rarity);
        return "Dans " + resetHours + "h maximum";
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

        // Vérifie s'il y a une quête terminée à réclamer
        PlayerQuestData playerData = plugin.getPlayerDataManager().getPlayerData(player);
        List<Quest> activeQuests = playerData.getActiveQuests(clickedRarity);

        if (!activeQuests.isEmpty()) {
            Quest quest = activeQuests.get(0);
            if (quest.isCompleted()) {
                // Donne les récompenses et termine la quête
                plugin.getQuestManager().completeQuest(player, quest);

                // Son de récupération
                String sound = plugin.getConfigManager().getConfig().getString("sounds.quest-completed");
                if (sound != null && !sound.isEmpty()) {
                    try {
                        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                    } catch (Exception e) {
                        // Ignore les erreurs de son
                    }
                }

                // Met à jour le menu
                openMainMenu(player);
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