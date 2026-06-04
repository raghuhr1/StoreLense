package com.storelense.auth.domain.repository;

import com.storelense.auth.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    Set<Role> findByNameIn(Set<String> names);
}
