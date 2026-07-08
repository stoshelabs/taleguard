package dev.stoshe.taleguard.api;

public enum Priority {
    HIGHEST(0),
    HIGH(1),
    NORMAL(2),
    LOW(3),
    LOWEST(4);

    private final int level;
    Priority(int level) { this.level = level; }
    public int getLevel() { return level; }
}
