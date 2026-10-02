package com.dozernet.common.user;

import com.dozernet.common.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository (DAO) pattern via Spring Data JPA - shared user data access.
 *
 * <p>Roles live in a collection table, so the role lookups below are explicit
 * JPQL joins rather than derived query methods.</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByIdentityCardNumber(String identityCardNumber);

    @Query("select distinct u from User u join u.roles r where r = :role")
    List<User> findByRole(@Param("role") Role role);

    @Query("select distinct u from User u join u.roles r where r = :role and u.verified = false")
    List<User> findByRoleAndVerifiedFalse(@Param("role") Role role);

    @Query("select count(distinct u) from User u join u.roles r where r = :role")
    long countByRole(@Param("role") Role role);
}
