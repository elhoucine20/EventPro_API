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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // Mockito cree les @Mock avant chaque test
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordUtils passwordUtils;

    @InjectMocks
    private UserService userService; // le vrai service, avec les faux repository/mapper dedans

    // ---------- petits helpers (comme OneUser() avant) ----------
    private UserResponse OneResponse() {
        return mock(UserResponse.class);
    }

    // =====================================================================
    // registerClient
    // =====================================================================
    @Test
    void registerClient_valide_hashLePasswordEtSauvegarde() {
        // ARRANGE
        RegisterClientRequest request =
                new RegisterClientRequest("lahcen", "lahcen@gmail.com", "12345678", "0612345678");
        Client client = mock(Client.class);
        UserResponse response = OneResponse();

        // l'email n'existe pas (Mockito retourne false par defaut, je le mets pour etre clair)
        when(userRepository.existsByEmail("lahcen@gmail.com")).thenReturn(false);
        when(passwordUtils.generateSalt()).thenReturn("salt123");
        when(passwordUtils.hashPassword("12345678", "salt123")).thenReturn("hash123");
        when(userMapper.toClient(request, "hash123", "salt123")).thenReturn(client);
        when(userRepository.save(client)).thenReturn(client);
        when(userMapper.toResponse(client)).thenReturn(response);

        // ACT
        UserResponse result = userService.registerClient(request);

        // ASSERT
        assertSame(response, result);                       // le service retourne la reponse du mapper
        verify(passwordUtils).hashPassword("12345678", "salt123"); // le password est hashe avec le salt
        verify(userRepository).save(client);                // le client est sauvegarde
    }

    @Test
    void registerClient_emailDejaUtilise_exceptionEtRienNEstSauvegarde() {
        // ARRANGE
        RegisterClientRequest request =
                new RegisterClientRequest("lahcen", "lahcen@gmail.com", "12345678", "0612345678");
        when(userRepository.existsByEmail("lahcen@gmail.com")).thenReturn(true);

        // ACT + ASSERT
        assertThrows(EmailAlreadyUsedException.class, () -> userService.registerClient(request));

        verify(userRepository, never()).save(any());          // jamais sauvegarde
        verify(passwordUtils, never()).generateSalt();        // on n'a meme pas commence a hasher
    }

    // =====================================================================
    // registerOrganisateur
    // =====================================================================
    @Test
    void registerOrganisateur_valide_hashLePasswordEtSauvegarde() {
        // ARRANGE  (ordre suppose : name, email, password, nomOrganisation)
        RegisterOrganisateurRequest request =
                new RegisterOrganisateurRequest("lahcen", "orga@gmail.com", "12345678", "PayEvents");
        Organisateur orga = mock(Organisateur.class);
        UserResponse response = OneResponse();

        when(passwordUtils.generateSalt()).thenReturn("salt123");
        when(passwordUtils.hashPassword("12345678", "salt123")).thenReturn("hash123");
        when(userMapper.toOrganisateur(request, "hash123", "salt123")).thenReturn(orga);
        when(userRepository.save(orga)).thenReturn(orga);
        when(userMapper.toResponse(orga)).thenReturn(response);

        // ACT
        UserResponse result = userService.registerOrganisateur(request);

        // ASSERT
        assertSame(response, result);
        verify(userRepository).save(orga);
    }

    @Test
    void registerOrganisateur_emailDejaUtilise_exceptionEtRienNEstSauvegarde() {
        // ARRANGE
        RegisterOrganisateurRequest request =
                new RegisterOrganisateurRequest("lahcen", "orga@gmail.com", "12345678", "PayEvents");
        when(userRepository.existsByEmail("orga@gmail.com")).thenReturn(true);

        // ACT + ASSERT
        assertThrows(EmailAlreadyUsedException.class, () -> userService.registerOrganisateur(request));

        verify(userRepository, never()).save(any());
    }

    // =====================================================================
    // registerAgent
    // =====================================================================
    @Test
    void registerAgent_valide_hashLePasswordEtSauvegarde() {
        // ARRANGE
        RegisterAgentRequest request = new RegisterAgentRequest("agent", "agent@gmail.com", "12345678");
        AgentControle agent = mock(AgentControle.class);
        UserResponse response = OneResponse();

        when(passwordUtils.generateSalt()).thenReturn("salt123");
        when(passwordUtils.hashPassword("12345678", "salt123")).thenReturn("hash123");
        when(userMapper.toAgent(request, "hash123", "salt123")).thenReturn(agent);
        when(userRepository.save(agent)).thenReturn(agent);
        when(userMapper.toResponse(agent)).thenReturn(response);

        // ACT
        UserResponse result = userService.registerAgent(request);

        // ASSERT
        assertSame(response, result);
        verify(userRepository).save(agent);
    }

    @Test
    void registerAgent_emailDejaUtilise_exceptionEtRienNEstSauvegarde() {
        // ARRANGE
        RegisterAgentRequest request = new RegisterAgentRequest("agent", "agent@gmail.com", "12345678");
        when(userRepository.existsByEmail("agent@gmail.com")).thenReturn(true);

        // ACT + ASSERT
        assertThrows(EmailAlreadyUsedException.class, () -> userService.registerAgent(request));

        verify(userRepository, never()).save(any());
    }

    // =====================================================================
    // registerAdmin
    // =====================================================================
    @Test
    void registerAdmin_valide_hashLePasswordEtSauvegarde() {
        // ARRANGE
        RegisterAdminRequest request = new RegisterAdminRequest("admin", "admin@gmail.com", "12345678");
        Administrateur admin = mock(Administrateur.class);
        UserResponse response = OneResponse();

        when(passwordUtils.generateSalt()).thenReturn("salt123");
        when(passwordUtils.hashPassword("12345678", "salt123")).thenReturn("hash123");
        when(userMapper.toAdmin(request, "hash123", "salt123")).thenReturn(admin);
        when(userRepository.save(admin)).thenReturn(admin);
        when(userMapper.toResponse(admin)).thenReturn(response);

        // ACT
        UserResponse result = userService.registerAdmin(request);

        // ASSERT
        assertSame(response, result);
        verify(userRepository).save(admin);
    }

    @Test
    void registerAdmin_emailDejaUtilise_exceptionEtRienNEstSauvegarde() {
        // ARRANGE
        RegisterAdminRequest request = new RegisterAdminRequest("admin", "admin@gmail.com", "12345678");
        when(userRepository.existsByEmail("admin@gmail.com")).thenReturn(true);

        // ACT + ASSERT
        assertThrows(EmailAlreadyUsedException.class, () -> userService.registerAdmin(request));

        verify(userRepository, never()).save(any());
    }

    // =====================================================================
    // getById
    // =====================================================================
    @Test
    void getById_existant_retourneLaReponse() {
        // ARRANGE
        UUID id = UUID.randomUUID();
        User user = mock(User.class);
        UserResponse response = OneResponse();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        // ACT + ASSERT
        assertSame(response, userService.getById(id));  // assertSame => le meme objet
    }

    @Test
    void getById_inexistant_ResourceNotFound() {
        // ARRANGE
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThrows(ResourceNotFoundException.class, () -> userService.getById(id));
    }

    // =====================================================================
    // getAll
    // =====================================================================
    @Test
    void getAll_retourneLesUtilisateursMappes() {
        // ARRANGE
        List<User> users = List.of(mock(User.class), mock(User.class));
        List<UserResponse> responses = List.of(OneResponse(), OneResponse());
        when(userRepository.findAll()).thenReturn(users);
        when(userMapper.mapToAll(users)).thenReturn(responses);

        // ACT
        List<UserResponse> result = userService.getAll();

        // ASSERT
        assertEquals(2, result.size());
        assertSame(responses, result);
    }

    @Test
    void getAll_aucunUtilisateur_listeVide() {
        // ARRANGE
        when(userRepository.findAll()).thenReturn(List.of());
        when(userMapper.mapToAll(List.of())).thenReturn(List.of());

        // ACT + ASSERT
        assertTrue(userService.getAll().isEmpty());
    }
}