package com.fantasy.db.user;

import com.fantasy.db.exception.EmailAlreadyExistsException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    @Transactional
    public User create(String email, String passwordHash) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        try {
            return userRepository.save(User.create(email, passwordHash));
        } catch (DataIntegrityViolationException e) {
            // Handles the race where two requests create the same email concurrently;
            // the unique constraint is the source of truth.
            throw new EmailAlreadyExistsException(email);
        }
    }
}
