package com.soldshort.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages turn order for a single round of stock picking within a league.
 *
 * Each round all active (non-eliminated) players pick one stock in turn.
 * The draft is complete when every active player has submitted a pick.
 * Turn order is randomized once when the draft is constructed.
 */
public class Draft {

    /** Possible states of the draft. */
    public enum State {
        WAITING,     // draft object created but not yet started
        IN_PROGRESS, // at least one pick has been made; not yet complete
        COMPLETE     // every active player has submitted their pick
    }

    private final int         leagueId;
    private final int         roundNumber;
    private final List<Integer> playerOrder;    // userId turn order (randomized)
    private final List<Integer> pickedPlayers;  // users who have already picked

    private State state;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a new Draft for the given league round.
     *
     * @param leagueId    the league hosting this draft
     * @param roundNumber the round being drafted
     * @param playerIds   IDs of all active (non-eliminated) players
     */
    public Draft(int leagueId, int roundNumber, List<Integer> playerIds) {
        this.leagueId     = leagueId;
        this.roundNumber  = roundNumber;
        this.playerOrder  = new ArrayList<>(playerIds);
        this.pickedPlayers = new ArrayList<>();
        this.state        = State.WAITING;

        // Randomize turn order each round
        Collections.shuffle(this.playerOrder);
    }

    // ── Turn Management ───────────────────────────────────────────────────────

    /** Starts the draft. Call once before the first pick. */
    public void start() {
        if (state == State.WAITING) {
            state = State.IN_PROGRESS;
        }
    }

    /**
     * Returns the userId of the player whose turn it is, or -1 if draft is
     * complete or all players have picked.
     */
    public int getCurrentPlayerId() {
        for (int playerId : playerOrder) {
            if (!pickedPlayers.contains(playerId)) {
                return playerId;
            }
        }
        return -1;  // all players have picked
    }

    /**
     * Records that a player has picked and advances the turn.
     *
     * @param userId the player who just submitted a pick
     * @return true if the record was accepted; false if it was not this player's turn
     */
    public boolean recordPick(int userId) {
        if (getCurrentPlayerId() != userId) {
            return false;
        }
        pickedPlayers.add(userId);
        if (pickedPlayers.size() == playerOrder.size()) {
            state = State.COMPLETE;
        } else {
            state = State.IN_PROGRESS;
        }
        return true;
    }

    /** Returns true if the given user has already picked this round. */
    public boolean hasPlayerPicked(int userId) {
        return pickedPlayers.contains(userId);
    }

    /** Returns true when every active player has made their selection. */
    public boolean isComplete() {
        return state == State.COMPLETE;
    }

    /** Returns the number of players still waiting to pick. */
    public int remainingPicks() {
        return playerOrder.size() - pickedPlayers.size();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public int         getLeagueId()    { return leagueId; }
    public int         getRoundNumber() { return roundNumber; }
    public State       getState()       { return state; }
    public List<Integer> getPlayerOrder()  { return Collections.unmodifiableList(playerOrder); }
    public List<Integer> getPickedPlayers(){ return Collections.unmodifiableList(pickedPlayers); }

    @Override
    public String toString() {
        return "Draft{league=" + leagueId + ", round=" + roundNumber
                + ", state=" + state + ", remaining=" + remainingPicks() + "}";
    }
}
