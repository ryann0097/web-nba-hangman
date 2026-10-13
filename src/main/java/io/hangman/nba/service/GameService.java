package io.hangman.nba.service;

import io.hangman.nba.dto.GameResponse;
import io.hangman.nba.dto.NewGameRequest;
import io.hangman.nba.model.entity.GameSession;
import io.hangman.nba.model.entity.GameSession.GameStatus;
import io.hangman.nba.model.entity.NbaPlayer;
import io.hangman.nba.model.entity.User;
import io.hangman.nba.repository.GameSessionRepository;
import io.hangman.nba.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GameService {

    private final NbaDatasetLoader datasetLoader;
    private final GameSessionRepository gameSessionRepository;
    private final UserRepository userRepository;

    private static final String[] HANGMAN_STAGES = {
            // Stage 0: No parts yet
            "    +---+\n" +
            "    |   |\n" +
            "        |\n" +
            "        |\n" +
            "        |\n" +
            "        |\n" +
            " =========\n",

            // Stage 1: Head
            "    +---+\n" +
            "    |   |\n" +
            "    O   |\n" +
            "        |\n" +
            "        |\n" +
            "        |\n" +
            " =========\n",

            // Stage 2: Head and body
            "    +---+\n" +
            "    |   |\n" +
            "    O   |\n" +
            "    |   |\n" +
            "        |\n" +
            "        |\n" +
            " =========\n",

            // Stage 3: Head, body, and one arm
            "    +---+\n" +
            "    |   |\n" +
            "    O   |\n" +
            "   /|   |\n" +
            "        |\n" +
            "        |\n" +
            " =========\n",

            // Stage 4: Head, body, and both arms
            "    +---+\n" +
            "    |   |\n" +
            "    O   |\n" +
            "   /|\\  |\n" +
            "        |\n" +
            "        |\n" +
            " =========\n",

            // Stage 5: Head, body, both arms, and one leg
            "    +---+\n" +
            "    |   |\n" +
            "    O   |\n" +
            "   /|\\  |\n" +
            "   /    |\n" +
            "        |\n" +
            " =========\n",

            // Stage 6: Full hangman
            "    +---+\n" +
            "    |   |\n" +
            "    O   |\n" +
            "   /|\\  |\n" +
            "   / \\  |\n" +
            "        |\n" +
            " =========\n"
    };

    @Transactional
    public GameResponse createGame(User user, NewGameRequest request) {
        List<NbaPlayer> candidates = datasetLoader.filterPlayers(request.getDifficulty(), request.getCategory());
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No NBA players found for difficulty " + request.getDifficulty() + " and category " + request.getCategory());
        }

        List<GameSession> playedGames = gameSessionRepository.findByUser(user);
        Set<String> alreadyPlayedNames = new HashSet<>();
        for (GameSession g : playedGames) {
            alreadyPlayedNames.add(g.getTargetWord());
        }

        List<NbaPlayer> unplayed = new ArrayList<>();
        for (NbaPlayer p : candidates) {
            if (!alreadyPlayedNames.contains(p.getName())) {
                unplayed.add(p);
            }
        }

        NbaPlayer selectedPlayer;
        if (!unplayed.isEmpty()) {
            Collections.shuffle(unplayed);
            selectedPlayer = unplayed.get(0);
        } else {
            // Se já jogou todas dessa categoria, escolhe qualquer uma da categoria
            Collections.shuffle(candidates);
            selectedPlayer = candidates.get(0);
        }

        GameSession session = GameSession.builder()
                .user(user)
                .targetWord(selectedPlayer.getName())
                .position(selectedPlayer.getPosition())
                .positionName(selectedPlayer.getPositionName())
                .champ(selectedPlayer.isChamp())
                .roy(selectedPlayer.isRoy())
                .dpoy(selectedPlayer.isDpoy())
                .mvp(selectedPlayer.isMvp())
                .at75(selectedPlayer.isAt75())
                .difficulty(selectedPlayer.getDifficulty())
                .wrongGuesses(0)
                .maxTries(6)
                .status(GameStatus.IN_PROGRESS)
                .usedLetters(new HashSet<>())
                .wrongLetters(new ArrayList<>())
                .build();

        gameSessionRepository.save(session);
        return toGameResponse(session, "New game started! Guess a letter or the full name.");
    }

    @Transactional(readOnly = true)
    public GameResponse getGame(UUID gameId, User user) {
        GameSession session = getSessionOrThrow(gameId, user);
        return toGameResponse(session, "Current game status.");
    }

    @Transactional
    public GameResponse processGuess(UUID gameId, User user, String rawGuess) {
        GameSession session = getSessionOrThrow(gameId, user);

        if (session.getStatus() != GameStatus.IN_PROGRESS) {
            return toGameResponse(session, "Game is already finished (" + session.getStatus() + ").");
        }

        String guess = rawGuess.trim().toUpperCase();
        if (guess.isEmpty()) {
            return toGameResponse(session, "Invalid empty guess.");
        }

        String feedbackMessage;
        String targetWord = session.getTargetWord().toUpperCase();

        if (guess.length() == 1) {
            char guessedChar = guess.charAt(0);

            // Validação 1: Já utilizada
            if (session.getUsedLetters().contains(guessedChar)) {
                feedbackMessage = "Letter '" + guessedChar + "' already used!";
            } else {
                session.getUsedLetters().add(guessedChar);

                // Validação 2: Letra correta
                if (targetWord.indexOf(guessedChar) >= 0) {
                    feedbackMessage = "Right guess! Letter '" + guessedChar + "' is in the word.";
                    if (isWordFullyGuessed(targetWord, session.getUsedLetters())) {
                        handleWin(session, user);
                        feedbackMessage = "Congratulations! You guessed the player!";
                    }
                } else {
                    // Validação 3: Letra errada
                    session.getWrongLetters().add(guessedChar);
                    session.setWrongGuesses(session.getWrongGuesses() + 1);
                    feedbackMessage = "Wrong guess! Letter '" + guessedChar + "' is NOT in the word.";

                    if (session.getWrongGuesses() >= session.getMaxTries()) {
                        handleLoss(session, user);
                        feedbackMessage = "Game Over! You ran out of tries.";
                    }
                }
            }
        } else {
            // Chute de palavra completa (full guess)
            if (guess.equals(targetWord)) {
                // Revela todas as letras e marca vitória
                for (char c : targetWord.toCharArray()) {
                    if (Character.isLetter(c)) {
                        session.getUsedLetters().add(c);
                    }
                }
                handleWin(session, user);
                feedbackMessage = "Incredible! Full guess correct!";
            } else {
                // Chute completo errado = derrota imediata (reproduzindo C++ FULL_GUESS_WRONG)
                session.setWrongGuesses(session.getMaxTries());
                handleLoss(session, user);
                feedbackMessage = "Full guess wrong! The player was not '" + guess + "'.";
            }
        }

        gameSessionRepository.save(session);
        return toGameResponse(session, feedbackMessage);
    }

    private void handleWin(GameSession session, User user) {
        session.setStatus(GameStatus.WIN);
        user.setGamesPlayed(user.getGamesPlayed() + 1);
        user.setGamesWon(user.getGamesWon() + 1);
        user.setScore(user.getScore() + calculateEarnedScore(session));
        userRepository.save(user);
    }

    // Pontuação: dificuldade (1=10pts, 2=20pts, 3=30pts) + bônus de tentativas restantes
    int calculateEarnedScore(GameSession session) {
        return (session.getDifficulty() * 10) + ((session.getMaxTries() - session.getWrongGuesses()) * 5);
    }

    private void handleLoss(GameSession session, User user) {
        session.setStatus(GameStatus.LOSS);
        user.setGamesPlayed(user.getGamesPlayed() + 1);
        userRepository.save(user);
    }

    private boolean isWordFullyGuessed(String targetWord, Set<Character> usedLetters) {
        for (char c : targetWord.toCharArray()) {
            if (Character.isLetter(c) && !usedLetters.contains(c)) {
                return false;
            }
        }
        return true;
    }

    private GameSession getSessionOrThrow(UUID gameId, User user) {
        GameSession session = gameSessionRepository.findById(gameId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found with ID: " + gameId));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You are not authorized to access this game.");
        }
        return session;
    }

    private GameResponse toGameResponse(GameSession session, String message) {
        List<String> badges = new ArrayList<>();
        badges.add(session.getPositionName());
        if (session.isChamp()) badges.add("NBA Champion");
        if (session.isRoy()) badges.add("ROY");
        if (session.isDpoy()) badges.add("DPOY");
        if (session.isMvp()) badges.add("MVP");
        if (session.isAt75()) badges.add("75th TEAM");

        StringBuilder masked = new StringBuilder();
        for (char c : session.getTargetWord().toCharArray()) {
            if (Character.isLetter(c)) {
                if (session.getUsedLetters().contains(Character.toUpperCase(c))) {
                    masked.append(c).append(' ');
                } else {
                    masked.append("_ ");
                }
            } else if (c == ' ') {
                masked.append("  ");
            } else {
                masked.append(c).append(' ');
            }
        }

        int stageIndex = Math.min(session.getWrongGuesses(), HANGMAN_STAGES.length - 1);
        String asciiArt = HANGMAN_STAGES[stageIndex];

        String revealedWord = null;
        if (session.getStatus() == GameStatus.WIN || session.getStatus() == GameStatus.LOSS) {
            revealedWord = session.getTargetWord();
        }

        return GameResponse.builder()
                .gameId(session.getId())
                .badges(badges)
                .maskedWord(masked.toString().trim())
                .wrongLetters(session.getWrongLetters())
                .usedLetters(session.getUsedLetters())
                .wrongGuesses(session.getWrongGuesses())
                .chancesLeft(Math.max(0, session.getMaxTries() - session.getWrongGuesses()))
                .maxTries(session.getMaxTries())
                .status(session.getStatus())
                .feedbackMessage(message)
                .hangmanStageAscii(asciiArt)
                .revealedWord(revealedWord)
                .userScore(session.getUser() != null ? session.getUser().getScore() : null)
                .build();
    }
}
