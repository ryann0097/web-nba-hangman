package io.hangman.nba.service;

import io.hangman.nba.model.entity.NbaPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NbaDatasetLoaderTest {

    private NbaDatasetLoader loader;

    @BeforeAll
    void setUp() {
        loader = new NbaDatasetLoader();
        loader.loadCsv();
    }

    @Test
    void loadCsv_populatesAllPlayers() {
        assertFalse(loader.getAllPlayers().isEmpty(), "allPlayers should not be empty after loading CSV");
    }

    @Test
    void loadCsv_separatesByDifficulty() {
        assertFalse(loader.getEasyPlayers().isEmpty(), "easyPlayers should not be empty");
        assertFalse(loader.getMidPlayers().isEmpty(), "midPlayers should not be empty");
        assertFalse(loader.getHardPlayers().isEmpty(), "hardPlayers should not be empty");

        int total = loader.getEasyPlayers().size() + loader.getMidPlayers().size() + loader.getHardPlayers().size();
        assertEquals(loader.getAllPlayers().size(), total, "Sum of difficulty lists should equal allPlayers");
    }

    @Test
    void filterPlayers_championCategory() {
        List<NbaPlayer> champs = loader.filterPlayers(1, 1); // easy + champion
        for (NbaPlayer p : champs) {
            assertTrue(p.isChamp(), "All filtered players should be champions");
        }
    }

    @Test
    void filterPlayers_guardCategory() {
        List<NbaPlayer> guards = loader.filterPlayers(1, 2); // easy + guard
        for (NbaPlayer p : guards) {
            assertEquals(1, p.getPosition(), "All filtered players should be guards (position=1)");
        }
    }

    @Test
    void filterPlayers_forwardCategory() {
        List<NbaPlayer> forwards = loader.filterPlayers(1, 3);
        for (NbaPlayer p : forwards) {
            assertEquals(2, p.getPosition(), "All filtered players should be forwards (position=2)");
        }
    }

    @Test
    void filterPlayers_centerCategory() {
        List<NbaPlayer> centers = loader.filterPlayers(1, 4);
        for (NbaPlayer p : centers) {
            assertEquals(3, p.getPosition(), "All filtered players should be centers (position=3)");
        }
    }

    @Test
    void filterPlayers_invalidCategory_returnsEmpty() {
        List<NbaPlayer> result = loader.filterPlayers(1, 99);
        assertTrue(result.isEmpty(), "Invalid category should return empty list");
    }

    @Test
    void filterPlayers_mvpCategory() {
        List<NbaPlayer> mvps = loader.filterPlayers(1, 7);
        for (NbaPlayer p : mvps) {
            assertTrue(p.isMvp(), "All filtered players should be MVPs");
        }
    }
}
