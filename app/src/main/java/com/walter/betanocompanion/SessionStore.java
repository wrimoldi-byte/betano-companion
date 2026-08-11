package com.walter.betanocompanion;

import java.util.ArrayDeque;
import java.util.Deque;

public final class SessionStore {
    private static final SessionStore INSTANCE = new SessionStore();
    public static SessionStore get() { return INSTANCE; }

    private int spins;
    private double wagered;
    private double won;
    private int noWinStreak;
    private final Deque<Boolean> recent = new ArrayDeque<>();

    public synchronized void addSpin(double bet, double prize) {
        if (bet < 0) bet = 0;
        if (prize < 0) prize = 0;
        spins++;
        wagered += bet;
        won += prize;
        boolean hit = prize > 0.0001;
        noWinStreak = hit ? 0 : noWinStreak + 1;
        recent.addLast(hit);
        while (recent.size() > 20) recent.removeFirst();
    }

    public synchronized void reset() {
        spins = 0; wagered = 0; won = 0; noWinStreak = 0; recent.clear();
    }

    public synchronized Snapshot snapshot() {
        int hits = 0;
        for (Boolean b : recent) if (b) hits++;
        double hitRate = recent.isEmpty() ? 0 : (double) hits / recent.size();
        double rtp = wagered <= 0 ? 0 : (won / wagered) * 100.0;
        int score = 50;
        score += (int) Math.round((hitRate - 0.30) * 45.0);
        score -= Math.min(25, noWinStreak * 2);
        if (rtp > 100) score += 8;
        if (rtp < 60 && spins >= 5) score -= 8;
        score = Math.max(0, Math.min(100, score));
        String trend = score >= 62 ? "🟢 Posible racha favorable" : score <= 38 ? "🔴 Racha negativa" : "🟡 Comportamiento normal";
        return new Snapshot(spins, wagered, won, rtp, noWinStreak, score, trend);
    }

    public record Snapshot(int spins, double wagered, double won, double rtp, int noWinStreak, int score, String trend) {}
}
