package com.storelense.auth.mapper;

import com.storelense.auth.domain.entity.User;
import com.storelense.auth.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", expression = "java(mapRoles(user))")
    UserResponse toResponse(User user);

    default Set<String> mapRoles(User user) {
        return user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toSet());
    }
}
