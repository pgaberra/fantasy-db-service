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

    /**
     * Resolves the account for a verified Google identity: returns the user already
     * linked to this Google subject, otherwise links it to an existing account with the
     * same (verified) email, otherwise creates a new password-less Google user.
     */
    @Transactional
    public User findOrCreateGoogleUser(String email, String googleSub) {
        Optional<User> byGoogle = userRepository.findByGoogleSub(googleSub);
        if (byGoogle.isPresent()) {
            return byGoogle.get();
        }
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.linkGoogle(googleSub);
            return existing;
        }
        try {
            return userRepository.save(User.createWithGoogle(email, googleSub));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created the same email/subject; the unique indexes are
            // the source of truth, so re-read whichever now exists.
            return userRepository.findByGoogleSub(googleSub)
                    .or(() -> userRepository.findByEmailIgnoreCase(email))
                    .orElseThrow(() -> e);
        }
    }
}
