package org.simpledailyquests.models;

import java.util.List;
import java.util.UUID;

public class Quest {

    public enum QuestType {
        KILL, CRAFT, MINE, FISH
    }

    public enum QuestRarity {
        COMMUNE, RARE, MYTHIQUE, LEGENDAIRE
    }

    private String questId;
    private QuestType type;
    private QuestRarity rarity;
    private String target;
    private int required;
    private int progress;
    private long assignedTime;
    private List<String> rewards;
    private String description;

    public Quest(String questId, QuestType type, QuestRarity rarity, String target, int required, List<String> rewards) {
        this.questId = questId;
        this.type = type;
        this.rarity = rarity;
        this.target = target;
        this.required = required;
        this.progress = 0;
        this.assignedTime = System.currentTimeMillis();
        this.rewards = rewards;
        this.description = generateDescription();
    }

    /**
     * Génère automatiquement la description de la quête
     */
    private String generateDescription() {
        switch (type) {
            case KILL:
                return "Tuer " + required + " " + formatTarget(target);
            case CRAFT:
                return "Crafter " + required + " " + formatTarget(target);
            case MINE:
                return "Miner " + required + " " + formatTarget(target);
            case FISH:
                return "Pêcher " + required + " " + formatTarget(target);
            default:
                return "Quête inconnue";
        }
    }

    /**
     * Formate le nom du target pour l'affichage
     */
    private String formatTarget(String target) {
        return target.toLowerCase().replace("_", " ");
    }

    /**
     * Ajoute du progrès à la quête
     */
    public void addProgress(int amount) {
        this.progress += amount;
        if (this.progress > this.required) {
            this.progress = this.required;
        }
    }

    /**
     * Vérifie si la quête est terminée
     */
    public boolean isCompleted() {
        return progress >= required;
    }

    /**
     * Calcule le pourcentage de progression
     */
    public double getProgressPercentage() {
        return (double) progress / required * 100;
    }

    /**
     * Vérifie si la quête a expiré selon sa rareté
     */
    public boolean hasExpired() {
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - assignedTime;
        long hoursElapsed = timeDiff / (1000 * 60 * 60);

        switch (rarity) {
            case COMMUNE:
                return hoursElapsed >= 12;
            case RARE:
                return hoursElapsed >= 24;
            case MYTHIQUE:
                return hoursElapsed >= 72;
            case LEGENDAIRE:
                return hoursElapsed >= 168;
            default:
                return false;
        }
    }

    // Getters et setters
    public String getQuestId() { return questId; }
    public QuestType getType() { return type; }
    public QuestRarity getRarity() { return rarity; }
    public String getTarget() { return target; }
    public int getRequired() { return required; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public long getAssignedTime() { return assignedTime; }
    public void setAssignedTime(long assignedTime) { this.assignedTime = assignedTime; }
    public List<String> getRewards() { return rewards; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return "Quest{" +
                "questId='" + questId + '\'' +
                ", type=" + type +
                ", rarity=" + rarity +
                ", target='" + target + '\'' +
                ", progress=" + progress + "/" + required +
                ", completed=" + isCompleted() +
                '}';
    }
}