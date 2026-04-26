package com.soldshort.controllers;

import com.soldshort.data.DataManager;
import com.soldshort.draft.Draft;
import com.soldshort.draft.LeagueDraftManager;
import com.soldshort.dto.*;
import com.soldshort.engine.PortfolioEvaluationEngine;
import com.soldshort.market.ManualMarketSimulator;
import com.soldshort.models.DraftPick;
import com.soldshort.models.League;
import com.soldshort.models.User;
import com.soldshort.observer.LeaderboardEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller for league and draft operations.
 *
 * ── Leagues ──────────────────────────────────────────────────────────────────
 * GET    /api/leagues?userId=X          — leagues the user belongs to
 * POST   /api/leagues                   — create league
 * GET    /api/leagues/{id}              — get league by ID
 * DELETE /api/leagues/{id}              — host removes league
 * POST   /api/leagues/{id}/join         — join by code
 * POST   /api/leagues/{id}/leave        — leave league
 * GET    /api/leagues/{id}/members      — all members
 * GET    /api/leagues/{id}/members/active — active (non-eliminated) members
 *
 * ── Draft ────────────────────────────────────────────────────────────────────
 * POST   /api/leagues/{id}/draft/start  — host starts round 1
 * POST   /api/leagues/{id}/round/next   — host starts next round
 * POST   /api/leagues/{id}/draft/pick   — submit a stock pick
 * GET    /api/leagues/{id}/draft/picks?round=N             — picks for a round
 * GET    /api/leagues/{id}/draft/picks/user?userId=X&round=N — one player's pick
 * GET    /api/leagues/{id}/draft/picks/all                  — all picks, all rounds
 * GET    /api/leagues/{id}/draft/tickers?round=N            — available tickers
 * GET    /api/leagues/{id}/draft/all-submitted?round=N      — bool: all picks in?
 * GET    /api/leagues/{id}/draft/round-evaluated?round=N    — bool: round scored?
 *
 * ── Evaluation ───────────────────────────────────────────────────────────────
 * POST   /api/leagues/{id}/evaluate     — host enters prices + triggers scoring
 * GET    /api/leagues/{id}/leaderboard  — current standings
 */
@RestController
@RequestMapping("/api/leagues")
public class LeagueController {

    private final DataManager               dm             = DataManager.getInstance();
    private final LeagueDraftManager        draftManager;
    private final PortfolioEvaluationEngine evaluationEngine;
    private final ManualMarketSimulator     market;

    public LeagueController(LeagueDraftManager draftManager,
                            PortfolioEvaluationEngine evaluationEngine,
                            ManualMarketSimulator market) {
        this.draftManager     = draftManager;
        this.evaluationEngine = evaluationEngine;
        this.market           = market;
    }

    // ── Leagues ───────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<League>> getLeaguesForUser(@RequestParam int userId) {
        return ResponseEntity.ok(dm.getLeaguesForUser(userId));
    }

    @PostMapping
    public ResponseEntity<League> createLeague(@RequestBody CreateLeagueRequest req) {
        User host = dm.getUserById(req.getHostId());
        if (host == null) return ResponseEntity.badRequest().build();
        League league = draftManager.createLeague(host, req.getName(),
                req.getMaxPlayers(), req.getStartingLives());
        return league != null ? ResponseEntity.ok(league)
                              : ResponseEntity.internalServerError().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<League> getLeague(@PathVariable int id) {
        League league = dm.getLeagueById(id);
        return league != null ? ResponseEntity.ok(league) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeLeague(@PathVariable int id,
                                             @RequestParam int hostId) {
        League league = dm.getLeagueById(id);
        if (league == null)                 return ResponseEntity.notFound().build();
        if (!league.isHostedBy(hostId))     return ResponseEntity.status(403).build();
        dm.removeLeague(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<League> joinLeague(@PathVariable int id,
                                             @RequestBody JoinRequest req) {
        User player = dm.getUserById(req.getUserId());
        if (player == null) return ResponseEntity.badRequest().build();

        // When id == 0 the client is joining by code only (join-code flow).
        // Otherwise resolve the code from the existing league record.
        String code;
        if (id == 0) {
            if (req.getJoinCode() == null || req.getJoinCode().isBlank())
                return ResponseEntity.badRequest().build();
            code = req.getJoinCode();
        } else {
            League league = dm.getLeagueById(id);
            if (league == null) return ResponseEntity.notFound().build();
            code = req.getJoinCode() != null ? req.getJoinCode() : league.getJoinCode();
        }

        League joined = draftManager.joinLeague(code, player);
        return joined != null ? ResponseEntity.ok(joined) : ResponseEntity.badRequest().build();
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leaveLeague(@PathVariable int id,
                                            @RequestBody JoinRequest req) {
        boolean ok = dm.leaveLeague(id, req.getUserId());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<User>> getMembers(@PathVariable int id) {
        return ResponseEntity.ok(dm.getLeagueMembers(id));
    }

    @GetMapping("/{id}/members/active")
    public ResponseEntity<List<User>> getActiveMembers(@PathVariable int id) {
        return ResponseEntity.ok(dm.getActiveMembers(id));
    }

    // ── Draft ─────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/draft/start")
    public ResponseEntity<League> startDraft(@PathVariable int id,
                                             @RequestBody JoinRequest req) {
        League league = dm.getLeagueById(id);
        if (league == null)               return ResponseEntity.notFound().build();
        if (!league.isHostedBy(req.getUserId())) return ResponseEntity.status(403).build();

        Draft draft = draftManager.startDraft(league);
        if (draft == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(dm.getLeagueById(id));
    }

    @PostMapping("/{id}/round/next")
    public ResponseEntity<League> startNextRound(@PathVariable int id,
                                                 @RequestBody JoinRequest req) {
        League league = dm.getLeagueById(id);
        if (league == null)               return ResponseEntity.notFound().build();
        if (!league.isHostedBy(req.getUserId())) return ResponseEntity.status(403).build();

        Draft draft = draftManager.startNextRound(league);
        League fresh = dm.getLeagueById(id);
        if (draft == null) {
            // Tournament ended — return the FINISHED league
            return ResponseEntity.ok(fresh);
        }
        return ResponseEntity.ok(fresh);
    }

    @PostMapping("/{id}/draft/pick")
    public ResponseEntity<DraftPick> submitPick(@PathVariable int id,
                                                @RequestBody MakePickRequest req) {
        League league = dm.getLeagueById(id);
        if (league == null) return ResponseEntity.notFound().build();
        DraftPick pick = draftManager.submitPick(id, league.getCurrentRound(),
                req.getUserId(), req.getTicker());
        return pick != null ? ResponseEntity.ok(pick)
                            : ResponseEntity.badRequest().build();
    }

    @GetMapping("/{id}/draft/picks")
    public ResponseEntity<List<DraftPick>> getPicksForRound(
            @PathVariable int id, @RequestParam int round) {
        return ResponseEntity.ok(dm.getPicksForRound(id, round));
    }

    @GetMapping("/{id}/draft/picks/user")
    public ResponseEntity<DraftPick> getPickForUser(
            @PathVariable int id,
            @RequestParam int userId,
            @RequestParam int round) {
        DraftPick pick = dm.getPickForUser(id, userId, round);
        return pick != null ? ResponseEntity.ok(pick) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/draft/picks/all")
    public ResponseEntity<List<DraftPick>> getAllPicks(@PathVariable int id) {
        return ResponseEntity.ok(dm.getAllPicksForLeague(id));
    }

    /**
     * Returns available tickers for the current draft round as a list of maps:
     * [ { ticker, companyName, currentPrice, previousPrice }, ... ]
     */
    @GetMapping("/{id}/draft/tickers")
    public ResponseEntity<List<Map<String, Object>>> getAvailableTickers(
            @PathVariable int id, @RequestParam int round) {
        List<String> tickers = draftManager.getAvailableTickers(id, round);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String ticker : tickers) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ticker",        ticker);
            entry.put("companyName",   market.getCompanyName(ticker));
            entry.put("currentPrice",  market.getPrice(ticker));
            entry.put("previousPrice", market.getPreviousPrice(ticker));
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/draft/all-submitted")
    public ResponseEntity<Boolean> allPicksSubmitted(
            @PathVariable int id, @RequestParam int round) {
        return ResponseEntity.ok(dm.allPicksSubmitted(id, round));
    }

    @GetMapping("/{id}/draft/round-evaluated")
    public ResponseEntity<Boolean> roundEvaluated(
            @PathVariable int id, @RequestParam int round) {
        return ResponseEntity.ok(dm.isRoundEvaluated(id, round));
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /**
     * Host submits ticker → new price pairs.  Server applies them to the
     * shared ManualMarketSimulator and runs the PortfolioEvaluationEngine.
     * Returns the ranked leaderboard for the round.
     */
    @PostMapping("/{id}/evaluate")
    public ResponseEntity<List<LeaderboardEntry>> evaluate(
            @PathVariable int id, @RequestBody EvaluateRequest req) {
        League league = dm.getLeagueById(id);
        if (league == null)                   return ResponseEntity.notFound().build();
        if (!league.isHostedBy(req.getHostId())) return ResponseEntity.status(403).build();

        // Guard: don't double-evaluate
        if (dm.isRoundEvaluated(id, league.getCurrentRound())) {
            return ResponseEntity.ok(evaluationEngine.getLeaderboard(id));
        }

        // Apply host-supplied prices to the shared market simulator
        if (req.getPrices() != null) {
            req.getPrices().forEach(market::setPrice);
        }

        List<LeaderboardEntry> results =
                evaluationEngine.evaluateRound(league, league.getCurrentRound(), market);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(@PathVariable int id) {
        return ResponseEntity.ok(evaluationEngine.getLeaderboard(id));
    }
}
