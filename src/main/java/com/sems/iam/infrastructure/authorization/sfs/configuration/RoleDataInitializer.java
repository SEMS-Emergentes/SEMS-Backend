package com.sems.iam.infrastructure.authorization.sfs.configuration;

import com.sems.iam.infrastructure.persistence.jpa.repositories.RoleJpaEntity;
import com.sems.iam.infrastructure.persistence.jpa.repositories.RoleRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RoleDataInitializer {
    private final RoleRepository roleRepository;

    @Bean
    public CommandLineRunner seedRoles() {
        return args -> List.of("ADMIN", "STAFF").forEach(roleName -> roleRepository.findByName(roleName).orElseGet(() -> {
            RoleJpaEntity role = new RoleJpaEntity();
            role.setRoleId(UUID.randomUUID());
            role.setName(roleName);
            return roleRepository.save(role);
        }));
    }
}
