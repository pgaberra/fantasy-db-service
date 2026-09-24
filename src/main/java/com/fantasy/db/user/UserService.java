package com.fantasy.db.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

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
    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No user found with id: " + userId));
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
     * Sets the account's public name. Taking a name someone else already holds is a conflict,
     * regardless of case: two accounts called "Alex" and "alex" would be the same name to anyone
     * reading a shared page. Re-setting your own name to a different case is allowed.
     */
    @Transactional
    public User setUsername(UUID userId, String username) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No user found with id: " + userId));
        boolean ownName = username.equalsIgnoreCase(user.getUsername());
        if (!ownName && userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DataIntegrityViolationException("Username already taken: " + username);
        }
        user.updateUsername(username);
        // A concurrent claim of the same name surfaces from the unique index — the source of
        // truth — as the same exception the check above throws.
        return userRepository.save(user);
    }

    /**
     * Ends every session the account holds: refresh tokens carry the token version they were
     * issued under, and the BFF refuses one that no longer matches.
     */
    @Transactional
    public void revokeSessions(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("No user found with id: " + userId));
        user.bumpTokenVersion();
    }

    /**
     * Resolves the account for a verified Google identity: returns the user already
     * linked to this Google subject, otherwise links it to an existing account with the
     * same (verified) email, otherwise creates a new password-less Google user. Only the
     * last reports {@code created}: an account another request created concurrently was
     * not created by this one.
     */
    @Transactional
    public ResolvedUser findOrCreateGoogleUser(String email, String googleSub) {
        Optional<User> byGoogle = userRepository.findByGoogleSub(googleSub);
        if (byGoogle.isPresent()) {
            return ResolvedUser.existing(byGoogle.get());
        }
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.linkGoogle(googleSub);
            return ResolvedUser.existing(existing);
        }
        try {
            return ResolvedUser.created(userRepository.save(User.createWithGoogle(email, googleSub)));
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created the same email/subject; the unique indexes are
            // the source of truth, so re-read whichever now exists.
            return userRepository.findByGoogleSub(googleSub)
                    .or(() -> userRepository.findByEmailIgnoreCase(email))
                    .map(ResolvedUser::existing)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Resolves the account for a verified Facebook identity: returns the user already
     * linked to this Facebook subject, otherwise links it to an existing account with the
     * same (verified) email, otherwise creates a new password-less Facebook user. Only the
     * last reports {@code created}.
     */
    @Transactional
    public ResolvedUser findOrCreateFacebookUser(String email, String facebookSub) {
        Optional<User> byFacebook = userRepository.findByFacebookSub(facebookSub);
        if (byFacebook.isPresent()) {
            return ResolvedUser.existing(byFacebook.get());
        }
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            existing.linkFacebook(facebookSub);
            return ResolvedUser.existing(existing);
        }
        try {
            return ResolvedUser.created(userRepository.save(User.createWithFacebook(email, facebookSub)));
        } catch (DataIntegrityViolationException e) {
            return userRepository.findByFacebookSub(facebookSub)
                    .or(() -> userRepository.findByEmailIgnoreCase(email))
                    .map(ResolvedUser::existing)
                    .orElseThrow(() -> e);
        }
    }
}
