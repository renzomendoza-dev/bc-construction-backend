package com.bcconstructionservices.inventory.service;

import com.bcconstructionservices.inventory.exception.ResourceNotFoundException;
import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository userRepository;

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        UUID keycloakId = UUID.fromString(jwt.getSubject());
        return userRepository.findByKeycloakId(keycloakId)
                .map(AppUser::getId)
                .orElseThrow(() -> new ResourceNotFoundException("No local AppUser found for authenticated Keycloak user"));
    }
}