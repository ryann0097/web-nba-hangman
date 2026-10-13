package io.hangman.nba.service;

import io.hangman.nba.config.JwtService;
import io.hangman.nba.dto.AuthRequest;
import io.hangman.nba.dto.AuthResponse;
import io.hangman.nba.model.entity.User;
import io.hangman.nba.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_success() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken("newuser")).thenReturn("jwt-token");

        AuthResponse response = authService.register(new AuthRequest("newuser", "pass123"));

        assertEquals("jwt-token", response.getToken());
        assertEquals("newuser", response.getUsername());
        assertEquals(0, response.getScore());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateUsername_throws() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> authService.register(new AuthRequest("taken", "pass")));
    }

    @Test
    void login_success() {
        User user = User.builder()
                .id(1L).username("player").password("encoded").score(100).build();
        when(userRepository.findByUsername("player")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "encoded")).thenReturn(true);
        when(jwtService.generateToken("player")).thenReturn("jwt-token");

        AuthResponse response = authService.login(new AuthRequest("player", "pass"));

        assertEquals("jwt-token", response.getToken());
        assertEquals(100, response.getScore());
    }

    @Test
    void login_userNotFound_throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new AuthRequest("ghost", "pass")));
    }

    @Test
    void login_wrongPassword_throws() {
        User user = User.builder()
                .id(1L).username("player").password("encoded").build();
        when(userRepository.findByUsername("player")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(new AuthRequest("player", "wrong")));
    }
}
