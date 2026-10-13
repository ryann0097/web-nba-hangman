package io.hangman.nba.service;

import io.hangman.nba.dto.GameResponse;
import io.hangman.nba.dto.NewGameRequest;
import io.hangman.nba.model.entity.GameSession;
import io.hangman.nba.model.entity.GameSession.GameStatus;
import io.hangman.nba.model.entity.NbaPlayer;
import io.hangman.nba.model.entity.User;
import io.hangman.nba.repository.GameSessionRepository;
import io.hangman.nba.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private NbaDatasetLoader datasetLoader;
    @Mock
    private GameSessionRepository gameSessionRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GameService gameService;

    private User user;
    private NbaPlayer player;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .password("encoded")
                .score(0)
                .gamesWon(0)
                .gamesPlayed(0)
                .build();

        player = NbaPlayer.builder()
                .name("KOBE BRYANT")
                .position(1)
                .positionName("Guard")
                .champ(true)
                .roy(false)
                .dpoy(false)
                .mvp(true)
                .at75(true)
                .difficulty(1)
                .build();
    }

    @Test
    void createGame_success() {
        when(datasetLoader.filterPlayers(1, 1)).thenReturn(List.of(player));
        when(gameSessionRepository.findByUser(user)).thenReturn(Collections.emptyList());
        when(gameSessionRepository.save(any(GameSession.class))).thenAnswer(i -> {
            GameSession s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        GameResponse response = gameService.createGame(user, new NewGameRequest(1, 1));

        assertNotNull(response);
        assertEquals(GameStatus.IN_PROGRESS, response.getStatus());
        assertEquals(6, response.getMaxTries());
        verify(gameSessionRepository).save(any(GameSession.class));
    }

    @Test
    void createGame_noPlayersAvailable_throws() {
        when(datasetLoader.filterPlayers(1, 1)).thenReturn(Collections.emptyList());

        assertThrows(IllegalArgumentException.class,
                () -> gameService.createGame(user, new NewGameRequest(1, 1)));
    }

    @Test
    void processGuess_correctLetter() {
        GameSession session = buildSession("KOBE BRYANT");
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        GameResponse response = gameService.processGuess(session.getId(), user, "K");

        assertTrue(response.getFeedbackMessage().contains("Right guess"));
        assertTrue(session.getUsedLetters().contains('K'));
    }

    @Test
    void processGuess_wrongLetter() {
        GameSession session = buildSession("KOBE BRYANT");
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        GameResponse response = gameService.processGuess(session.getId(), user, "Z");

        assertTrue(response.getFeedbackMessage().contains("Wrong guess"));
        assertEquals(1, session.getWrongGuesses());
        assertTrue(session.getWrongLetters().contains('Z'));
    }

    @Test
    void processGuess_repeatedLetter() {
        GameSession session = buildSession("KOBE BRYANT");
        session.getUsedLetters().add('K');
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        GameResponse response = gameService.processGuess(session.getId(), user, "K");

        assertTrue(response.getFeedbackMessage().contains("already used"));
    }

    @Test
    void processGuess_winByLetters() {
        GameSession session = buildSession("AB");
        session.getUsedLetters().add('A');
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        GameResponse response = gameService.processGuess(session.getId(), user, "B");

        assertEquals(GameStatus.WIN, session.getStatus());
        assertTrue(response.getFeedbackMessage().contains("Congratulations"));
    }

    @Test
    void processGuess_loseByExhaustingTries() {
        GameSession session = buildSession("KOBE BRYANT");
        session.setWrongGuesses(5); // one more wrong = 6 = maxTries
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        GameResponse response = gameService.processGuess(session.getId(), user, "Z");

        assertEquals(GameStatus.LOSS, session.getStatus());
        assertTrue(response.getFeedbackMessage().contains("Game Over"));
    }

    @Test
    void processGuess_fullGuessCorrect() {
        GameSession session = buildSession("KOBE BRYANT");
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        GameResponse response = gameService.processGuess(session.getId(), user, "KOBE BRYANT");

        assertEquals(GameStatus.WIN, session.getStatus());
        assertTrue(response.getFeedbackMessage().contains("Full guess correct"));
    }

    @Test
    void processGuess_fullGuessWrong() {
        GameSession session = buildSession("KOBE BRYANT");
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        GameResponse response = gameService.processGuess(session.getId(), user, "LEBRON JAMES");

        assertEquals(GameStatus.LOSS, session.getStatus());
        assertTrue(response.getFeedbackMessage().contains("Full guess wrong"));
    }

    @Test
    void processGuess_gameAlreadyFinished() {
        GameSession session = buildSession("KOBE BRYANT");
        session.setStatus(GameStatus.WIN);
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        GameResponse response = gameService.processGuess(session.getId(), user, "A");

        assertTrue(response.getFeedbackMessage().contains("already finished"));
    }

    @Test
    void calculateEarnedScore_values() {
        GameSession session = GameSession.builder()
                .difficulty(2)
                .wrongGuesses(2)
                .maxTries(6)
                .build();

        // (2*10) + ((6-2)*5) = 20 + 20 = 40
        assertEquals(40, gameService.calculateEarnedScore(session));
    }

    @Test
    void getSessionOrThrow_wrongUser_throws() {
        User otherUser = User.builder().id(99L).username("other").password("x").build();
        GameSession session = buildSession("KOBE BRYANT");
        when(gameSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class,
                () -> gameService.processGuess(session.getId(), otherUser, "A"));
    }

    private GameSession buildSession(String targetWord) {
        UUID id = UUID.randomUUID();
        return GameSession.builder()
                .id(id)
                .user(user)
                .targetWord(targetWord)
                .position(1)
                .positionName("Guard")
                .champ(true)
                .roy(false)
                .dpoy(false)
                .mvp(true)
                .at75(true)
                .difficulty(1)
                .wrongGuesses(0)
                .maxTries(6)
                .status(GameStatus.IN_PROGRESS)
                .usedLetters(new HashSet<>())
                .wrongLetters(new ArrayList<>())
                .build();
    }
}
