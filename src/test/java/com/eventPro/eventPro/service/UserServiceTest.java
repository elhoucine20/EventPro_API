package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.admin.RegisterAdminRequest;
import com.eventPro.eventPro.dto.agent.RegisterAgentRequest;
import com.eventPro.eventPro.dto.user.RegisterClientRequest;
import com.eventPro.eventPro.dto.user.RegisterOrganisateurRequest;
import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.exception.EmailAlreadyUsedException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.user.UserMapper;
import com.eventPro.eventPro.model.Administrateur;
import com.eventPro.eventPro.model.AgentControle;
import com.eventPro.eventPro.model.Client;
import com.eventPro.eventPro.model.Organisateur;
import com.eventPro.eventPro.model.User;
import com.eventPro.eventPro.repository.UserRepository;
import com.eventPro.eventPro.util.PasswordUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String EMAIL = "test@mail.com";
    private static final String PASSWORD = "motdepasse123";
    private static final String SALT = "salt-xyz";
    private static final String HASH = "hash-xyz";

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordUtils passwordUtils;

    @InjectMocks private UserService userService;

    // ---------- helper : prepare hash/salt (partage par les 4 register nominaux) ----------
    private void stubHash() {
        when(passwordUtils.generateSalt()).thenReturn(SALT);
        when(passwordUtils.hashPassword(PASSWORD, SALT)).thenReturn(HASH);
    }

    private void verifyRienNEstFaitSiEmailPris() {
        verifyNoInteractions(passwordUtils, userMapper);
        verify(userRepository, never()).save(any());
    }

    // =====================================================================
    @Nested
    @DisplayName("registerClient")
    class RegisterClient {

        @Test
        void nominal_hashePuisSauvegarde() {
            RegisterClientRequest request = mock(RegisterClientRequest.class);
            when(request.email()).thenReturn(EMAIL);
            when(request.password()).thenReturn(PASSWORD);
            Client client = mock(Client.class);
            UserResponse response = mock(UserResponse.class);

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            stubHash();
            when(userMapper.toClient(request, HASH, SALT)).thenReturn(client);
            when(userRepository.save(client)).thenReturn(client);
            when(userMapper.toResponse(client)).thenReturn(response);

            UserResponse result = userService.registerClient(request);

            assertThat(result).isSameAs(response);
            verify(passwordUtils).hashPassword(PASSWORD, SALT);
            verify(userRepository).save(client);
        }

        @Test
        void emailDejaUtilise_leveException_etNeSauvegardeRien() {
            RegisterClientRequest request = mock(RegisterClientRequest.class);
            when(request.email()).thenReturn(EMAIL);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> userService.registerClient(request))
                    .isInstanceOf(EmailAlreadyUsedException.class);

            verifyRienNEstFaitSiEmailPris();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("registerOrganisateur")
    class RegisterOrganisateur {

        @Test
        void nominal_hashePuisSauvegarde() {
            RegisterOrganisateurRequest request = mock(RegisterOrganisateurRequest.class);
            when(request.email()).thenReturn(EMAIL);
            when(request.password()).thenReturn(PASSWORD);
            Organisateur orga = mock(Organisateur.class);
            UserResponse response = mock(UserResponse.class);

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            stubHash();
            when(userMapper.toOrganisateur(request, HASH, SALT)).thenReturn(orga);
            when(userRepository.save(orga)).thenReturn(orga);
            when(userMapper.toResponse(orga)).thenReturn(response);

            UserResponse result = userService.registerOrganisateur(request);

            assertThat(result).isSameAs(response);
            verify(passwordUtils).hashPassword(PASSWORD, SALT);
            verify(userRepository).save(orga);
        }

        @Test
        void emailDejaUtilise_leveException_etNeSauvegardeRien() {
            RegisterOrganisateurRequest request = mock(RegisterOrganisateurRequest.class);
            when(request.email()).thenReturn(EMAIL);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> userService.registerOrganisateur(request))
                    .isInstanceOf(EmailAlreadyUsedException.class);

            verifyRienNEstFaitSiEmailPris();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("registerAgent")
    class RegisterAgent {

        @Test
        void nominal_hashePuisSauvegarde() {
            RegisterAgentRequest request = mock(RegisterAgentRequest.class);
            when(request.email()).thenReturn(EMAIL);
            when(request.password()).thenReturn(PASSWORD);
            AgentControle agent = mock(AgentControle.class);
            UserResponse response = mock(UserResponse.class);

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            stubHash();
            when(userMapper.toAgent(request, HASH, SALT)).thenReturn(agent);
            when(userRepository.save(agent)).thenReturn(agent);
            when(userMapper.toResponse(agent)).thenReturn(response);

            UserResponse result = userService.registerAgent(request);

            assertThat(result).isSameAs(response);
            verify(passwordUtils).hashPassword(PASSWORD, SALT);
            verify(userRepository).save(agent);
        }

        @Test
        void emailDejaUtilise_leveException_etNeSauvegardeRien() {
            RegisterAgentRequest request = mock(RegisterAgentRequest.class);
            when(request.email()).thenReturn(EMAIL);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> userService.registerAgent(request))
                    .isInstanceOf(EmailAlreadyUsedException.class);

            verifyRienNEstFaitSiEmailPris();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("registerAdmin")
    class RegisterAdmin {

        @Test
        void nominal_hashePuisSauvegarde() {
            RegisterAdminRequest request = mock(RegisterAdminRequest.class);
            when(request.email()).thenReturn(EMAIL);
            when(request.password()).thenReturn(PASSWORD);
            Administrateur admin = mock(Administrateur.class);
            UserResponse response = mock(UserResponse.class);

            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            stubHash();
            when(userMapper.toAdmin(request, HASH, SALT)).thenReturn(admin);
            when(userRepository.save(admin)).thenReturn(admin);
            when(userMapper.toResponse(admin)).thenReturn(response);

            UserResponse result = userService.registerAdmin(request);

            assertThat(result).isSameAs(response);
            verify(passwordUtils).hashPassword(PASSWORD, SALT);
            verify(userRepository).save(admin);
        }

        @Test
        void emailDejaUtilise_leveException_etNeSauvegardeRien() {
            RegisterAdminRequest request = mock(RegisterAdminRequest.class);
            when(request.email()).thenReturn(EMAIL);
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> userService.registerAdmin(request))
                    .isInstanceOf(EmailAlreadyUsedException.class);

            verifyRienNEstFaitSiEmailPris();
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void utilisateurTrouve_retourneLaReponse() {
            UUID id = UUID.randomUUID();
            User user = mock(User.class);
            UserResponse response = mock(UserResponse.class);
            when(userRepository.findById(id)).thenReturn(Optional.of(user));
            when(userMapper.toResponse(user)).thenReturn(response);

            assertThat(userService.getById(id)).isSameAs(response);
        }

        @Test
        void utilisateurIntrouvable_leveResourceNotFound() {
            UUID id = UUID.randomUUID();
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getById(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(id.toString());

            verifyNoInteractions(userMapper);
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        void retourneLesUtilisateursMappes() {
            List<User> users = List.of(mock(User.class), mock(User.class));
            List<UserResponse> responses = List.of(mock(UserResponse.class), mock(UserResponse.class));
            when(userRepository.findAll()).thenReturn(users);
            when(userMapper.mapToAll(users)).thenReturn(responses);

            assertThat(userService.getAll()).isEqualTo(responses);
        }

        @Test
        void aucunUtilisateur_retourneListeVide() {
            when(userRepository.findAll()).thenReturn(List.of());
            when(userMapper.mapToAll(List.of())).thenReturn(List.of());

            assertThat(userService.getAll()).isEmpty();
        }
    }
}