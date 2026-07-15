package com.storelense.auth.service;

import com.storelense.auth.domain.entity.User;
import com.storelense.auth.domain.repository.RoleRepository;
import com.storelense.auth.domain.repository.UserRepository;
import com.storelense.auth.dto.CreateUserRequest;
import com.storelense.auth.dto.UpdateUserRequest;
import com.storelense.auth.dto.UserResponse;
import com.storelense.auth.mapper.UserMapper;
import com.storelense.common.dto.PageResponse;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final RoleRepository  roleRepository;
    private final UserMapper      userMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(UUID storeId, boolean includeInactive, Pageable pageable) {
        var page = (storeId != null)
                ? (includeInactive ? userRepository.findByStoreId(storeId, pageable)
                                   : userRepository.findByStoreIdAndActiveTrue(storeId, pageable))
                : (includeInactive ? userRepository.findAllBy(pageable)
                                   : userRepository.findByActiveTrue(pageable));
        return PageResponse.from(page.map(userMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        return userMapper.toResponse(findOrThrow(userId));
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, UUID createdBy) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("USERNAME_TAKEN", "Username already exists", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("EMAIL_TAKEN", "Email already exists", HttpStatus.CONFLICT);
        }

        var roles = roleRepository.findByNameIn(request.roles());
        if (roles.isEmpty()) {
            throw new BusinessException("INVALID_ROLES", "No valid roles found");
        }

        var user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .storeId(request.storeId())
                .roles(roles)
                .createdBy(createdBy)
                .build();

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = findOrThrow(userId);

        if (request.email() != null) {
            if (userRepository.existsByEmail(request.email()) && !request.email().equals(user.getEmail())) {
                throw new BusinessException("EMAIL_TAKEN", "Email already exists", HttpStatus.CONFLICT);
            }
            user.setEmail(request.email());
        }
        if (request.firstName() != null)  user.setFirstName(request.firstName());
        if (request.lastName() != null)   user.setLastName(request.lastName());
        if (request.storeId() != null)    user.setStoreId(request.storeId());
        if (request.active() != null)     user.setActive(request.active());
        if (request.roles() != null && !request.roles().isEmpty()) {
            user.setRoles(roleRepository.findByNameIn(request.roles()));
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public void deactivateUser(UUID userId) {
        User user = findOrThrow(userId);
        user.setActive(false);
        userRepository.save(user);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
