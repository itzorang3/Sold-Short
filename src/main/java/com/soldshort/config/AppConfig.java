package com.soldshort.config;

import com.soldshort.draft.LeagueDraftManager;
import com.soldshort.engine.PortfolioEvaluationEngine;
import com.soldshort.market.LiveMarketProvider;
import com.soldshort.market.ManualMarketSimulator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring bean configuration for the Sold Short server.
 *
 * All game-logic objects are singletons shared across the entire server
 * process — exactly one ManualMarketSimulator, one LeagueDraftManager,
 * and one PortfolioEvaluationEngine per running server instance.
 */
@Configuration
public class AppConfig {

    /**
     * Shared market data provider.
     *
     * Uses LiveMarketProvider so seed prices are refreshed from Yahoo Finance
     * on startup.  Falls back to hard-coded seed prices if the network is
     * unavailable.
     */
    /**
     * Shared market data provider — declared as LiveMarketProvider (not the base
     * class) so Spring's scheduler post-processor can discover the @Scheduled
     * method on the concrete type and register it automatically.
     */
    @Bean
    public LiveMarketProvider market() {
        return new LiveMarketProvider();
    }

    /**
     * Draft manager — depends on the shared market simulator for price
     * snapshotting at round boundaries.
     */
    @Bean
    public LeagueDraftManager draftManager(LiveMarketProvider market) {
        return new LeagueDraftManager(market);
    }

    /**
     * Evaluation engine — reads from DataManager (Singleton) and fires
     * Observer notifications.
     */
    @Bean
    public PortfolioEvaluationEngine evaluationEngine() {
        return new PortfolioEvaluationEngine();
    }
}
