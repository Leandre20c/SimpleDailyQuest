package org.simpledailyquests.managers;

import org.simpledailyquests.models.Quest;

import java.util.*;

public class PlayerQuestData {

    private final UUID playerUUID;
    private String playerName;
    private final Map<Quest.QuestRarity, List<Quest>> activeQuests;
    private final Map<Quest.QuestRarity, Long> lastReset;
    private final List<CompletedQuest> completedQuests;

    public PlayerQuestData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.playerName = "Unknown";
        this.activeQuests = new HashMap<>();
        this.lastReset = new HashMap<>();
        this.completedQuests = new ArrayList<>();

        // Initialisation des listes de quêtes actives pour chaque rareté
        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            activeQuests.put(rarity, new ArrayList<>());
            lastReset.put(rarity, 0L);
        }
    }

    /**
     * Ajoute une quête active
     */
    public void addActiveQuest(Quest quest) {
        List<Quest> quests = activeQuests.get(quest.getRarity());
        if (quests != null && !quests.contains(quest)) {
            quests.add(quest);
        }
    }

    /**
     * Supprime une quête active
     */
    public void removeActiveQuest(Quest quest) {
        List<Quest> quests = activeQuests.get(quest.getRarity());
        if (quests != null) {
            quests.removeIf(q -> q.getQuestId().equals(quest.getQuestId()));
        }
    }

    /**
     * Obtient toutes les quêtes actives d'une rareté donnée
     */
    public List<Quest> getActiveQuests(Quest.QuestRarity rarity) {
        return activeQuests.getOrDefault(rarity, new ArrayList<>());
    }

    /**
     * Obtient toutes les quêtes actives
     */
    public List<Quest> getAllActiveQuests() {
        List<Quest> allQuests = new ArrayList<>();
        for (List<Quest> quests : activeQuests.values()) {
            allQuests.addAll(quests);
        }
        return allQuests;
    }

    /**
     * Trouve une quête active par son ID
     */
    public Quest getActiveQuestById(String questId) {
        for (List<Quest> quests : activeQuests.values()) {
            for (Quest quest : quests) {
                if (quest.getQuestId().equals(questId)) {
                    return quest;
                }
            }
        }
        return null;
    }

    /**
     * Vérifie si le joueur a des quêtes actives d'une rareté donnée
     */
    public boolean hasActiveQuests(Quest.QuestRarity rarity) {
        return !getActiveQuests(rarity).isEmpty();
    }

    /**
     * Obtient le nombre de quêtes actives d'une rareté donnée
     */
    public int getActiveQuestCount(Quest.QuestRarity rarity) {
        return getActiveQuests(rarity).size();
    }

    /**
     * Vérifie si le joueur peut recevoir une nouvelle quête de cette rareté
     */
    public boolean canReceiveQuest(Quest.QuestRarity rarity, int maxQuests) {
        return getActiveQuestCount(rarity) < maxQuests;
    }

    /**
     * Complète une quête et l'ajoute aux quêtes terminées
     */
    public void completeQuest(Quest quest) {
        removeActiveQuest(quest);
        addCompletedQuest(quest.getQuestId(), System.currentTimeMillis(), quest.getRarity());
    }

    /**
     * Ajoute une quête terminée
     */
    public void addCompletedQuest(String questId, long completionTime, Quest.QuestRarity rarity) {
        completedQuests.add(new CompletedQuest(questId, completionTime, rarity));
    }

    /**
     * Obtient toutes les quêtes terminées
     */
    public List<CompletedQuest> getCompletedQuests() {
        return new ArrayList<>(completedQuests);
    }

    /**
     * Obtient le nombre de quêtes terminées d'une rareté donnée
     */
    public int getCompletedQuestCount(Quest.QuestRarity rarity) {
        return (int) completedQuests.stream()
                .filter(cq -> cq.getRarity() == rarity)
                .count();
    }

    /**
     * Obtient le nombre total de quêtes terminées
     */
    public int getTotalCompletedQuests() {
        return completedQuests.size();
    }

    /**
     * Supprime les quêtes expirées
     */
    public List<Quest> removeExpiredQuests() {
        List<Quest> expiredQuests = new ArrayList<>();

        for (Quest.QuestRarity rarity : Quest.QuestRarity.values()) {
            List<Quest> quests = activeQuests.get(rarity);
            Iterator<Quest> iterator = quests.iterator();

            while (iterator.hasNext()) {
                Quest quest = iterator.next();
                if (quest.hasExpired()) {
                    expiredQuests.add(quest);
                    iterator.remove();
                }
            }
        }

        return expiredQuests;
    }

    /**
     * Efface toutes les quêtes actives d'une rareté donnée
     */
    public void clearActiveQuests(Quest.QuestRarity rarity) {
        activeQuests.get(rarity).clear();
    }

    /**
     * Met à jour le progrès d'une quête
     */
    public boolean updateQuestProgress(String questId, int amount) {
        Quest quest = getActiveQuestById(questId);
        if (quest != null) {
            quest.addProgress(amount);
            return quest.isCompleted();
        }
        return false;
    }

    /**
     * Trouve une quête par type et target
     */
    public Quest findQuestByTypeAndTarget(Quest.QuestType type, String target) {
        for (Quest quest : getAllActiveQuests()) {
            if (quest.getType() == type && quest.getTarget().equalsIgnoreCase(target)) {
                return quest;
            }
        }
        return null;
    }

    // Getters et setters
    public UUID getPlayerUUID() { return playerUUID; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public long getLastReset(Quest.QuestRarity rarity) {
        return lastReset.getOrDefault(rarity, 0L);
    }
    public void setLastReset(Quest.QuestRarity rarity, long time) {
        lastReset.put(rarity, time);
    }

    /**
     * Classe interne pour représenter une quête terminée
     */
    public static class CompletedQuest {
        private final String questId;
        private final long completionTime;
        private final Quest.QuestRarity rarity;

        public CompletedQuest(String questId, long completionTime, Quest.QuestRarity rarity) {
            this.questId = questId;
            this.completionTime = completionTime;
            this.rarity = rarity;
        }

        public String getQuestId() { return questId; }
        public long getCompletionTime() { return completionTime; }
        public Quest.QuestRarity getRarity() { return rarity; }

        @Override
        public String toString() {
            return "CompletedQuest{" +
                    "questId='" + questId + '\'' +
                    ", completionTime=" + completionTime +
                    ", rarity=" + rarity +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "PlayerQuestData{" +
                "playerUUID=" + playerUUID +
                ", playerName='" + playerName + '\'' +
                ", activeQuests=" + activeQuests.size() +
                ", completedQuests=" + completedQuests.size() +
                '}';
    }
}