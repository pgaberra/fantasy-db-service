package com.fantasy.db.user;

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
            throw new DataIntegrityViolationException("A user already exists for email: " + email);
        }
        // A concurrent request creating the same email surfaces as a
        // DataIntegrityViolationException from the unique constraint (the source of
        // truth), which the advice maps to 409 — same as the check above.
        return userRepository.save(User.create(email, passwordHash));
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

    /**
     * Resolves the account for a verified Facebook identity: returns the user already
     * linked to this Facebook subject, otherwise links it to an existing account with the
     * same (verified) email, otherwise creates a new password-less Facebook user.
     */
    @Transactional
    public User findOrCreateFacebookUser(String email, String facebookSub) {
        Optional<User> byFacebook = userRepository.findByFacebookSub(facebookSub);
        if (byFacebook.isPresent()) {
            return byFacebook.get();
        }
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.linkFacebook(facebookSub);
            return existing;
        }
        try {
            return userRepository.save(User.createWithFacebook(email, facebookSub));
        } catch (DataIntegrityViolationException e) {
            return userRepository.findByFacebookSub(facebookSub)
                    .or(() -> userRepository.findByEmailIgnoreCase(email))
                    .orElseThrow(() -> e);
        }
    }
}
