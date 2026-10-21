package com.eventPro.eventPro.service;

import com.eventPro.eventPro.dto.user.RegisterClientRequest;
import com.eventPro.eventPro.dto.user.RegisterOrganisateurRequest;
import com.eventPro.eventPro.dto.user.UserResponse;
import com.eventPro.eventPro.exception.EmailAlreadyUsedException;
import com.eventPro.eventPro.exception.ResourceNotFoundException;
import com.eventPro.eventPro.mapper.user.UserMapperrr;
import com.eventPro.eventPro.model.User;
import com.eventPro.eventPro.repository.UserRepository;
import com.eventPro.eventPro.util.PasswordUtils;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapperrr userMapper;
    private final PasswordUtils passwordUtils;

    @Transactional
    public UserResponse registerClient(RegisterClientRequest request){
        checkEmailAvailable(request.email());
        String salt = passwordUtils.generateSalt();
        String hashPassword = passwordUtils.hashPassword(request.password(),salt);
        User saved = userRepository.save(userMapper.toClient(request,hashPassword,salt));
        return userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse registerOrganisateur(RegisterOrganisateurRequest request){
        checkEmailAvailable(request.email());
        String salt = passwordUtils.generateSalt();
        String hashPassword = passwordUtils.hashPassword(request.password(),salt);
        User saved = userRepository.save(userMapper.toOrganisateur(request,hashPassword,salt));
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id){
        User user = userRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("user introuvable "+id));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll(){
         List<User> users = userRepository.findAll();
                //.orElseThrow(()->new ResourceNotFoundException("user introuvable "+id));
        return userMapper.mapToAll(users);
    }

    private void checkEmailAvailable(String email){
        if (userRepository.existsByEmail(email)){
            throw new EmailAlreadyUsedException(email);
        }
    }

}
