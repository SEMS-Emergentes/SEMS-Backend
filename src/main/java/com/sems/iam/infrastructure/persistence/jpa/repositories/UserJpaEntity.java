package com.sems.iam.infrastructure.persistence.jpa.repositories;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * UserJpaEntity is the PERSISTENCE model: it maps a Java object to a row in the
 * database "users" table using JPA/Hibernate. We deliberately keep it separate
 * from the domain's UserAggregate. The domain class focuses on business rules;
 * this class focuses on how data is stored. Mixing them would tie our business
 * logic to the database, so we keep two shapes and translate between them.
 *
 * Annotations:
 *  - @Entity + @Table tell JPA this class is a table named "iam_users".
 *  - @Getter/@Setter/@NoArgsConstructor are Lombok: they generate the getters,
 *    setters and the empty constructor that JPA requires.
 */
@Entity
@Table(name = "iam_users")
@Getter
@Setter
@NoArgsConstructor
public class UserJpaEntity {
    // @Id marks the primary key column. @Column maps each field to a DB column
    // and adds constraints that the database will enforce.
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // unique = true creates a UNIQUE constraint, so the database itself prevents
    // two users with the same email even if the application check is bypassed.
    @Column(name = "email_address", nullable = false, unique = true)
    private String emailAddress;

    // We store only the hashed password (nullable = false means it is required).
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Estado de la cuenta: PENDING mientras no se verifica el correo, ACTIVE
    // despues. Es nullable para no romper las filas que existian antes de
    // introducir la verificacion; un valor ausente se trata como ACTIVE.
    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
