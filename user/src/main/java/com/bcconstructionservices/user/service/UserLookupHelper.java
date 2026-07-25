package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserLookupHelper {

    private final UserRepository userRepository;

    @Named("resolveUserName")
    public String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(AppUser::getFullName)
                .orElse(null);
    }
}
