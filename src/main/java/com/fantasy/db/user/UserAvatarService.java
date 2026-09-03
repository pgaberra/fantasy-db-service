package com.fantasy.db.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserAvatarService {

    private final UserRepository userRepository;
    private final UserAvatarRepository userAvatarRepository;

    public UserAvatarService(UserRepository userRepository, UserAvatarRepository userAvatarRepository) {
        this.userRepository = userRepository;
        this.userAvatarRepository = userAvatarRepository;
    }

    @Transactional(readOnly = true)
    public UserAvatar find(UUID userId) {
        return userAvatarRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No avatar for user: " + userId));
    }

    @Transactional
    public UserAvatar set(UUID userId, String contentType, byte[] data) {
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("No user found with id: " + userId);
        }
        UserAvatar avatar = userAvatarRepository.findById(userId)
                .map(existing -> {
                    existing.replace(contentType, data);
                    return existing;
                })
                .orElseGet(() -> UserAvatar.of(userId, contentType, data));
        return userAvatarRepository.save(avatar);
    }

    @Transactional
    public void delete(UUID userId) {
        userAvatarRepository.delete(find(userId));
    }
}
