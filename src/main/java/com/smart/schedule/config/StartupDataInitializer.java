package com.smart.schedule.config;

import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.repository.RoleRepository;
import com.smart.schedule.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the fixed role dictionary and the initial administrator account.
 * Both operations are idempotent, so restarts do not duplicate data.
 */
@Component
public class StartupDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminFullName;

    public StartupDataInitializer(
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.username}") String adminUsername,
            @Value("${app.bootstrap.admin.password}") String adminPassword,
            @Value("${app.bootstrap.admin.full-name}") String adminFullName) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminFullName = adminFullName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (RoleName roleName : RoleName.values()) {
            roleRepository.findByName(roleName)
                    .orElseGet(() -> roleRepository.save(new Role(roleName, descriptionOf(roleName))));
        }

        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role was not created"));

        User admin = new User(adminUsername, passwordEncoder.encode(adminPassword), adminFullName);
        admin.addRole(adminRole);
        userRepository.save(admin);

        log.warn("Created bootstrap administrator '{}'. Change its password before production use.",
                adminUsername);
    }

    private String descriptionOf(RoleName roleName) {
        return switch (roleName) {
            case ADMIN -> "Администратор клиники";
            case DOCTOR -> "Врач";
            case RECEPTIONIST -> "Сотрудник регистратуры";
        };
    }
}
