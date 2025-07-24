package swp.project.adn_backend;


import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import swp.project.adn_backend.dto.request.authentication.IntrospectRequest;
import swp.project.adn_backend.dto.request.authentication.LoginDTO;
import swp.project.adn_backend.dto.response.AuthenticationResponse;
import swp.project.adn_backend.entity.Users;
import swp.project.adn_backend.enums.ErrorCodeUser;
import swp.project.adn_backend.exception.AppException;
import swp.project.adn_backend.repository.UserRepository;
import swp.project.adn_backend.service.JWT.AuthenticationUserService;

import java.text.ParseException;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JWTServiceTest {

    @InjectMocks
    @Spy
    private AuthenticationUserService authenticationUserService;
    @Mock
    private UserRepository userRepository;

    private Users testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testUser = new Users();
        testUser.setUserId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"));
        testUser.setEnabled(true);
        testUser.setRoles(Set.of("USER"));
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");
    }

    @Test
    void testAuthenticateUser_success() {
        LoginDTO loginDTO = new LoginDTO("testuser", "password123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        AuthenticationResponse response = authenticationUserService.authenticateUser(loginDTO);

        assertNotNull(response.getToken());
        assertEquals("true", response.getAuthenticated());
    }

    @Test
    void testAuthenticateUser_userNotFound() {
        LoginDTO loginDTO = new LoginDTO("wronguser", "password123");
        when(userRepository.findByUsername("wronguser")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authenticationUserService.authenticateUser(loginDTO));
        assertEquals(ErrorCodeUser.USER_NOT_EXISTED, ex.getErrorCode());
    }

    @Test
    void testAuthenticateUser_wrongPassword() {
        LoginDTO loginDTO = new LoginDTO("testuser", "wrongpass");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        AppException ex = assertThrows(AppException.class, () -> authenticationUserService.authenticateUser(loginDTO));
        assertEquals(ErrorCodeUser.UNAUTHENTICATED, ex.getErrorCode());
    }

    @Test
    void testAuthenticateUser_disabledUser() {
        testUser.setEnabled(false);
        LoginDTO loginDTO = new LoginDTO("testuser", "password123");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        AppException ex = assertThrows(AppException.class, () -> authenticationUserService.authenticateUser(loginDTO));
        assertEquals(ErrorCodeUser.USER_DISABLED, ex.getErrorCode());
    }

    @Test
    void testGenerateToken_validToken() throws Exception {
        String token = authenticationUserService.generateToken(testUser);
        assertNotNull(token);

        SignedJWT jwt = SignedJWT.parse(token);
        assertEquals("testuser", jwt.getJWTClaimsSet().getSubject());
    }


    @Test
    void testGenerateToken_throwJOSEException() throws Exception {
        Users user = new Users();
        user.setUsername("testuser");
        user.setUserId(1L);
        user.setPhone("123456");
        user.setAddress("abc");
        user.setFullName("Test");
        user.setAvatarUrl("url");
        user.setEmail("email@email.com");
        user.setRoles(Set.of("USER"));

        // 👇 Mock signToken để ném lỗi
        doThrow(new JOSEException("Signing failed")).when(authenticationUserService).signToken(any(JWSObject.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            authenticationUserService.generateToken(user);
        });

        assertTrue(ex.getCause() instanceof JOSEException);
        assertEquals("Signing failed", ex.getCause().getMessage());
    }
}
