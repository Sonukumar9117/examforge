package com.examforge.config;

import com.examforge.entity.Role;
import com.examforge.entity.User;
import com.examforge.entity.UserProfile;
import com.examforge.repository.RoleRepository;
import com.examforge.repository.UserProfileRepository;
import com.examforge.repository.UserRepository;
import com.examforge.service.AuditService;
import com.examforge.util.ValidationPatterns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Creates the first administrator from ADMIN_EMAIL / ADMIN_PASSWORD on startup, but only while no admin
 * exists. This is the only way to get the first admin; there is no public endpoint for it.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties props;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminBootstrap(AppProperties props, UserRepository users, RoleRepository roles,
                          UserProfileRepository profiles, PasswordEncoder passwordEncoder, AuditService audit) {
        this.props = props;
        this.users = users;
        this.roles = roles;
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Admin cfg = props.admin();
        if (users.countByRole(Role.ADMIN) > 0) return;
        if (cfg == null || isBlank(cfg.email()) || isBlank(cfg.password())) {
            log.warn("No admin account exists. Set ADMIN_EMAIL and ADMIN_PASSWORD to create one on startup.");
            return;
        }
        if (!cfg.password().matches(ValidationPatterns.PASSWORD_REGEX)) {
            log.error("ADMIN_PASSWORD is too weak ({}). Admin was not created.", ValidationPatterns.PASSWORD_MESSAGE);
            return;
        }
        String email = cfg.email().trim().toLowerCase(Locale.ROOT);
        Role adminRole = roles.require(Role.ADMIN);
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            String username = isBlank(cfg.username()) ? "admin" : cfg.username().trim();
            while (users.existsByUsernameIgnoreCase(username)) username = username + "1";
            user = new User(username, email, isBlank(cfg.fullName()) ? "ExamForge Admin" : cfg.fullName());
            user.setPasswordHash(passwordEncoder.encode(cfg.password()));
            user.setEmailVerified(true);
            user.getRoles().add(roles.require(Role.STUDENT));
            user.getRoles().add(adminRole);
            users.save(user);
            profiles.save(new UserProfile(user));
        } else {
            user.getRoles().add(adminRole);
        }
        audit.record(null, "ADMIN_BOOTSTRAPPED", "User", user.getId(), "from environment configuration", null);
        log.info("Created initial administrator '{}'", user.getUsername());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
