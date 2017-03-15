package com.soprasteria.initiatives.auth.service;

import com.soprasteria.initiatives.auth.config.CustomUser;
import com.soprasteria.initiatives.auth.domain.Authority;
import com.soprasteria.initiatives.auth.domain.User;
import com.soprasteria.initiatives.auth.repository.UserRepository;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import javax.validation.ValidationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Business services for {@link User} entity
 *
 * @author jntakpe
 */
@Service
public class UserService implements UserDetailsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    private final AuthorityService authorityService;

    @Autowired
    public UserService(UserRepository userRepository, AuthorityService authorityService) {
        this.userRepository = userRepository;
        this.authorityService = authorityService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = findByUsername(username);
        String[] authorities = user.getAuthorities().stream().map(Authority::getName).toArray(String[]::new);
        return new CustomUser(user.getUsername(), user.getPassword(), AuthorityUtils.createAuthorityList(authorities));
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        LOGGER.debug("Searching all users");
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        LOGGER.debug("Searching user with id : '{}'", id);
        User user = userRepository.findOne(id);
        if (user == null) {
            throw new EntityNotFoundException(String.format("Unable to retrieve user id : '%s'", id));
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        LOGGER.debug("Searching user with username : '{}'", username);
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException(String.format("Unable to retrieve username '%s'", username)));
    }

    @Transactional
    public User create(User user) {
        LOGGER.info("Creating new user : {}", user);
        checkUsernameAvailable(user);
        user.getAuthorities().add(authorityService.findDefaultOrCreate());
        return userRepository.save(user);
    }

    @Transactional
    public User edit(Long id, User user) {
        LOGGER.info("Updating user : {}", user);
        user.setId(id);
        checkUsernameAvailable(user);
        return userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = findById(id);
        LOGGER.info("Deleting user : {}", user);
        user.getAuthorities().clear();
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public Set<Authority> findAuthorities(Long userId) {
        User user = findById(userId);
        LOGGER.debug("Searching authorities for user : {}", user);
        Hibernate.initialize(user.getAuthorities());
        return user.getAuthorities();
    }

    @Transactional
    public User addAuthority(Long userId, Long authorityId) {
        User user = findById(userId);
        Authority authority = authorityService.findById(authorityId);
        checkAuthorityDoesntExist(user, authority);
        LOGGER.info("Adding authority {} to user {}", authority, user);
        user.getAuthorities().add(authority);
        return user;
    }

    @Transactional
    public User removeAuthority(Long userId, Long authorityId) {
        User user = findById(userId);
        Authority authority = authorityService.findById(authorityId);
        checkAuthorityExist(user, authority);
        LOGGER.info("Removing authority {} from user {}", authority, user);
        user.getAuthorities().remove(authority);
        return user;
    }

    private void checkUsernameAvailable(User user) {
        Optional<User> opt = userRepository.findByUsernameIgnoreCase(user.getUsername());
        if (opt.isPresent() && !opt.get().getId().equals(user.getId())) {
            throw new ValidationException(String.format("Username '%s' is already used", user.getUsername()));
        }
    }

    private void checkAuthorityDoesntExist(User user, Authority authority) {
        if (user.getAuthorities().contains(authority)) {
            throw new ValidationException(String.format("User %s already has authority %s", user, authority));
        }
    }

    private void checkAuthorityExist(User user, Authority authority) {
        if (!user.getAuthorities().contains(authority)) {
            throw new ValidationException(String.format("User %s has not authority %s", user, authority));
        }
    }

}