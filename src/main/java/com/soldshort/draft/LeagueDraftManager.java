package com.soldshort.draft;

import com.soldshort.data.DataManager;
import com.soldshort.market.ManualMarketSimulator;
import com.soldshort.models.DraftPick;
import com.soldshort.models.League;
import com.soldshort.models.User;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates league creation, joining, and round-by-round draft flow.
 *
 * Responsibilities:
 *   - Generate collision-free 6-character alphanumeric join codes
 *   - Validate that new joiners don't exceed max-player limits
 *   - Build a Draft object from the current roster of active players
 *   - Validate individual picks (correct turn, ticker not already taken)
 *   - Advance the league state machine between rounds
 */
public class LeagueDraftManager {

    private static final String CHARSET      = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int    CODE_LENGTH  = 6;
    private static final int    MAX_ATTEMPTS = 10;

    private final DataManager         dataManager;
    private final ManualMarketSimulator market;

    // Active draft for the current round (one per league per round)
    private Draft currentDraft;

    // ── Constructor ───────────────────────────────────────────────────────────

    public LeagueDraftManager(ManualMarketSimulator market) {
        this.dataManager = DataManager.getInstance();
        this.market      = market;
    }

    // ── League Creation & Joining ─────────────────────────────────────────────

    /**
     * Creates a new league, assigning the host and generating a unique join code.
     *
     * @param host       the user who will host/administer the league
     * @param leagueName the display name for the league
     * @param maxPlayers maximum number of participants (2–16)
     * @return the newly created League, or null if creation failed
     */
    public League createLeague(User host, String leagueName, int maxPlayers) {
        return createLeague(host, leagueName, maxPlayers, 3);
    }

    /**
     * Creates a new league with a specific starting-lives count.
     */
    public League createLeague(User host, String leagueName, int maxPlayers, int startingLives) {
        String joinCode = generateUniqueJoinCode();
        if (joinCode == null) {
            System.err.println("LeagueDraftManager: could not generate a unique join code.");
            return null;
        }
        return dataManager.createLeague(leagueName, host.getId(), maxPlayers, joinCode, startingLives);
    }

    /**
     * Adds a player to an existing league by join code.
     *
     * @param joinCode the 6-character code displayed in the lobby
     * @param player   the user attempting to join
     * @return the League joined, or null if code invalid / league full / already started
     */
    public League joinLeague(String joinCode, User player) {
        League league = dataManager.getLeagueByCode(joinCode.toUpperCase().trim());
        if (league == null) {
            System.err.println("joinLeague: invalid join code '" + joinCode + "'");
            return null;
        }
        if (league.getStatus() != League.Status.FORMING) {
            System.err.println("joinLeague: league '" + league.getName() + "' has already started.");
            return null;
        }
        int currentCount = dataManager.getLeagueMembers(league.getId()).size();
        if (currentCount >= league.getMaxPlayers()) {
            System.err.println("joinLeague: league '" + league.getName() + "' is full.");
            return null;
        }
        dataManager.joinLeague(league.getId(), player.getId());
        return dataManager.getLeagueById(league.getId());
    }

    // ── Draft Flow ────────────────────────────────────────────────────────────

    /**
     * Starts the draft for round 1.
     * Transitions the league from FORMING → DRAFTING and creates the Draft object.
     *
     * @param league the league to start drafting
     * @return the new Draft ready for picks, or null if fewer than 2 players
     */
    public Draft startDraft(League league) {
        List<User> members = dataManager.getLeagueMembers(league.getId());
        if (members.size() < 2) {
            System.err.println("startDraft: need at least 2 players.");
            return null;
        }
        // Advance league state
        int nextRound = league.getCurrentRound() + 1;
        dataManager.updateLeagueRound(league.getId(), nextRound);
        dataManager.updateLeagueStatus(league.getId(), League.Status.DRAFTING);

        // Snapshot current prices as "previous" for this round
        market.advanceRound();

        List<Integer> playerIds = members.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        currentDraft = new Draft(league.getId(), nextRound, playerIds);
        currentDraft.start();
        return currentDraft;
    }

    /**
     * Opens the next round after evaluation is complete.
     * Transitions the league from ACTIVE → DRAFTING.
     *
     * @param league the league advancing to a new round
     * @return a new Draft for the surviving players
     */
    public Draft startNextRound(League league) {
        List<User> activePlayers = dataManager.getActiveMembers(league.getId());
        if (activePlayers.size() < 2) {
            // Only one player left — mark the league finished
            dataManager.updateLeagueStatus(league.getId(), League.Status.FINISHED);
            System.out.println("League '" + league.getName() + "' is finished! Winner: "
                    + (activePlayers.isEmpty() ? "N/A" : activePlayers.get(0).getUsername()));
            return null;
        }
        int nextRound = league.getCurrentRound() + 1;
        dataManager.updateLeagueRound(league.getId(), nextRound);
        dataManager.updateLeagueStatus(league.getId(), League.Status.DRAFTING);

        // Snapshot prices for the new round
        market.advanceRound();

        List<Integer> playerIds = activePlayers.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        currentDraft = new Draft(league.getId(), nextRound, playerIds);
        currentDraft.start();
        return currentDraft;
    }

    /**
     * Submits a stock pick on behalf of a player.
     *
     * All state is validated against the database so this method works correctly
     * across multiple app instances sharing the same SQLite file.
     *
     * Three-stage validation:
     *   1. The player has not already picked this round (DB check).
     *   2. The ticker is recognised by the market simulator.
     *   3. The ticker has not already been picked by another player this round (DB check).
     *
     * @param leagueId   the league the pick belongs to
     * @param round      the current round number
     * @param userId     the player making the pick
     * @param ticker     the chosen stock symbol
     * @return the saved DraftPick, or null if validation failed
     */
    public DraftPick submitPick(int leagueId, int round, int userId, String ticker) {
        // Stage 0: player must be an active (non-eliminated) member of the league
        boolean isActive = dataManager.getActiveMembers(leagueId)
                .stream().anyMatch(u -> u.getId() == userId);
        if (!isActive) {
            System.err.println("submitPick: player " + userId + " is eliminated or not in league "
                    + leagueId + " — pick rejected.");
            return null;
        }

        // Stage 1: player hasn't already picked this round
        if (dataManager.getPickForUser(leagueId, userId, round) != null) {
            System.err.println("submitPick: player " + userId + " already picked this round.");
            return null;
        }
        // Stage 2: ticker exists
        if (!market.getAvailableTickers().contains(ticker)) {
            System.err.println("submitPick: unknown ticker '" + ticker + "'.");
            return null;
        }
        // Stage 3: ticker not already taken this round
        List<String> taken = dataManager.getDraftedTickers(leagueId, round);
        if (taken.contains(ticker)) {
            System.err.println("submitPick: ticker '" + ticker + "' already picked this round.");
            return null;
        }

        return dataManager.saveDraftPick(leagueId, userId, ticker, round);
    }

    /**
     * Backward-compatible overload that accepts a Draft object (kept for callers
     * that already have a Draft reference).
     */
    public DraftPick submitPick(Draft draft, int userId, String ticker) {
        if (draft == null) return null;
        return submitPick(draft.getLeagueId(), draft.getRoundNumber(), userId, ticker);
    }

    /**
     * Returns the list of tickers still available for picking in a given round.
     *
     * Two exclusion rules are applied:
     *   1. Tickers already picked by another player THIS round (same-round duplicate prevention).
     *   2. Tickers picked in ANY PREVIOUS round of this league (cross-round permanent retirement).
     *
     * Rule 2 keeps the draft pool fresh and prevents repeated matchups across rounds.
     */
    public List<String> getAvailableTickers(int leagueId, int round) {
        List<String> all       = market.getAvailableTickers();
        // All-rounds exclusion covers same-round picks too (DISTINCT query includes current round)
        List<String> usedEver  = dataManager.getDraftedTickersAllRounds(leagueId);
        return all.stream().filter(t -> !usedEver.contains(t)).collect(Collectors.toList());
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Draft getCurrentDraft() { return currentDraft; }

    // ── Private Helpers ───────────────────────────────────────────────────────

    /**
     * Generates a random 6-character alphanumeric join code and checks it
     * against the database until a unique code is found (max 10 attempts).
     */
    private String generateUniqueJoinCode() {
        SecureRandom rng = new SecureRandom();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CHARSET.charAt(rng.nextInt(CHARSET.length())));
            }
            String code = sb.toString();
            if (!dataManager.joinCodeExists(code)) {
                return code;
            }
        }
        return null;
    }
}
