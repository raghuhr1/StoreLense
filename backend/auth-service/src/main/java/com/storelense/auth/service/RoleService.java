package com.storelense.auth.service;

import com.storelense.auth.domain.entity.Role;
import com.storelense.auth.domain.repository.RoleRepository;
import com.storelense.auth.domain.repository.UserRepository;
import com.storelense.auth.dto.CreateRoleRequest;
import com.storelense.auth.dto.RoleResponse;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final List<String> SYSTEM_ROLES = List.of(
            "ADMIN", "STORE_MANAGER", "STORE_ASSOCIATE", "REFILL_ASSOCIATE", "SECURITY_GUARD"
    );

    private final RoleRepository roleRepository;
    private final JdbcClient     jdbcClient;

    @Transactional(readOnly = true)
    public List<RoleResponse> listAll() {
        return roleRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest req) {
        if (roleRepository.findByName(req.name()).isPresent()) {
            throw new BusinessException("ROLE_EXISTS", "Role '" + req.name() + "' already exists", HttpStatus.CONFLICT);
        }
        Role role = Role.builder()
                .name(req.name())
                .description(req.description())
                .build();
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(UUID id, CreateRoleRequest req) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));

        if (SYSTEM_ROLES.contains(role.getName())) {
            throw new BusinessException("SYSTEM_ROLE", "System roles cannot be renamed", HttpStatus.FORBIDDEN);
        }

        roleRepository.findByName(req.name()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessException("ROLE_EXISTS", "Role '" + req.name() + "' already exists", HttpStatus.CONFLICT);
            }
        });

        role.setName(req.name());
        role.setDescription(req.description());
        return toResponse(roleRepository.save(role));
    }

    @Transactional
    public void delete(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));

        if (SYSTEM_ROLES.contains(role.getName())) {
            throw new BusinessException("SYSTEM_ROLE", "System roles cannot be deleted", HttpStatus.FORBIDDEN);
        }

        long count = countUsersWithRole(role.getName());
        if (count > 0) {
            throw new BusinessException("ROLE_IN_USE",
                    "Role '" + role.getName() + "' is assigned to " + count + " user(s) — reassign them first",
                    HttpStatus.CONFLICT);
        }

        roleRepository.delete(role);
    }

    private long countUsersWithRole(String roleName) {
        return jdbcClient.sql("""
                SELECT COUNT(DISTINCT ur.user_id)
                FROM auth.user_roles ur
                JOIN auth.roles r ON r.id = ur.role_id
                WHERE r.name = :name
                """)
                .param("name", roleName)
                .query(Long.class)
                .single();
    }

    private RoleResponse toResponse(Role role) {
        long userCount = countUsersWithRole(role.getName());
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), userCount, role.getCreatedAt());
    }
}
