package com.bettercontent.betteroregeology.salience;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DepositSurveyEpisodesTest {
    @Test
    void acceptsOnlyPortableBoundedEpisodeIds() {
        assertTrue(DepositSurveyEpisodes.validEpisodeId("player:deposit:ironstone:42"));
        assertFalse(DepositSurveyEpisodes.validEpisodeId(""));
        assertFalse(DepositSurveyEpisodes.validEpisodeId("contains space"));
        assertFalse(DepositSurveyEpisodes.validEpisodeId("contains\nnewline"));
        assertFalse(DepositSurveyEpisodes.validEpisodeId("contains\u007fdelete"));
        assertFalse(DepositSurveyEpisodes.validEpisodeId("x".repeat(129)));
    }

    @Test
    void startsOnlyWhenTheFamilyHasNoLiveSurvey() {
        assertTrue(DepositSurveyEpisodes.shouldStart(""));
        assertFalse(DepositSurveyEpisodes.shouldStart("player:deposit:ironstone:42"));
    }
}
