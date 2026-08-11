package com.walter.betanocompanion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class SessionStore {
    private static final SessionStore INSTANCE = new SessionStore();
    public static SessionStore get() { return INSTANCE; }

    private int spins;
    private double wagered;
    private double won;
    private int noWinStreak;
    private int totalNoWinSpins;
    private int totalHits;
    private final Deque<Boolean> recent = new ArrayDeque<>();
    private final Deque<Integer> completedNoWinGaps = new ArrayDeque<>();

    public synchronized void addSpin(double bet, double prize) {
        if (bet < 0) bet = 0;
        if (prize < 0) prize = 0;
        spins++;
        wagered += bet;
        won += prize;

        boolean hit = prize > 0.0001;
        if (hit) {
            totalHits++;
            completedNoWinGaps.addLast(noWinStreak);
            while (completedNoWinGaps.size() > 40) completedNoWinGaps.removeFirst();
            noWinStreak = 0;
        } else {
            noWinStreak++;
            totalNoWinSpins++;
        }

        recent.addLast(hit);
        while (recent.size() > 30) recent.removeFirst();
    }

    public synchronized void reset() {
        spins = 0;
        wagered = 0;
        won = 0;
        noWinStreak = 0;
        totalNoWinSpins = 0;
        totalHits = 0;
        recent.clear();
        completedNoWinGaps.clear();
    }

    public synchronized Snapshot snapshot() {
        int recentHits = 0;
        for (Boolean b : recent) if (b) recentHits++;
        double hitRate = recent.isEmpty() ? 0 : (double) recentHits / recent.size();
        double overallHitRate = spins == 0 ? 0 : (double) totalHits / spins;
        double rtp = wagered <= 0 ? 0 : (won / wagered) * 100.0;

        int score = 50;
        score += (int) Math.round((hitRate - 0.30) * 35.0);
        score -= Math.min(28, noWinStreak * 3);
        if (rtp > 100) score += 8;
        if (rtp < 60 && spins >= 8) score -= 8;
        score = Math.max(0, Math.min(100, score));

        String trend;
        if (noWinStreak >= 8) trend = "🔴 Mala racha";
        else if (score >= 62) trend = "🟢 Frecuencia reciente alta";
        else if (score <= 38) trend = "🔴 Racha negativa";
        else trend = "🟡 Comportamiento normal";

        double avgSpinsPerHit = totalHits == 0 ? 0 : (double) spins / totalHits;
        Window window = historicalWindow();

        return new Snapshot(
                spins, wagered, won, rtp,
                noWinStreak, totalNoWinSpins, totalHits,
                overallHitRate * 100.0, avgSpinsPerHit,
                window.min, window.max,
                score, trend);
    }

    private Window historicalWindow() {
        if (completedNoWinGaps.size() < 3) {
            if (totalHits >= 2 && spins > 0) {
                double interval = (double) spins / totalHits;
                int center = Math.max(1, (int) Math.round(interval));
                int remaining = Math.max(1, center - noWinStreak);
                return new Window(Math.max(1, remaining - 2), remaining + 2);
            }
            return new Window(0, 0);
        }

        List<Integer> gaps = new ArrayList<>(completedNoWinGaps);
        Collections.sort(gaps);
        int q25 = gaps.get((int) Math.floor((gaps.size() - 1) * 0.25));
        int q75 = gaps.get((int) Math.floor((gaps.size() - 1) * 0.75));

        // Convertimos los huecos históricos en cuántas tiradas faltarían para alcanzar
        // esa zona desde la racha actual. Es descriptivo: no cambia la probabilidad real.
        int minRemaining = Math.max(1, (q25 + 1) - noWinStreak);
        int maxRemaining = Math.max(minRemaining, (q75 + 1) - noWinStreak);
        return new Window(minRemaining, maxRemaining);
    }

    private record Window(int min, int max) {}

    public record Snapshot(
            int spins,
            double wagered,
            double won,
            double rtp,
            int noWinStreak,
            int totalNoWinSpins,
            int totalHits,
            double hitRate,
            double avgSpinsPerHit,
            int windowMin,
            int windowMax,
            int score,
            String trend) {}
}
