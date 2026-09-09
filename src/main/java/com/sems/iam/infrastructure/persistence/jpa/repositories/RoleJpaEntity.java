package com.sems.iam.infrastructure.persistence.jpa.repositories;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "iam_roles")
@Getter
@Setter
@NoArgsConstructor
public class RoleJpaEntity {
    @Id
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "name", nullable = false, unique = true, length = 20)
    private String name;
}
