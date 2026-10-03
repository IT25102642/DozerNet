package com.dozernet.module4_operator.repository;

import com.dozernet.common.user.User;
import com.dozernet.module4_operator.entity.OperatorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OperatorProfileRepository extends JpaRepository<OperatorProfile, Long> {

    Optional<OperatorProfile> findByUser(User user);

    @Query("select p from OperatorProfile p join fetch p.user where p.verified = false")
    List<OperatorProfile> findByVerifiedFalse();

    @Query("select p from OperatorProfile p join fetch p.user where p.verified = true")
    List<OperatorProfile> findByVerifiedTrue();

    @Query("select p from OperatorProfile p join fetch p.user")
    List<OperatorProfile> findAllWithUser();

    boolean existsByLicenceNumber(String licenceNumber);
}
