package com.dozernet.module3_fleet.repository;

import com.dozernet.common.user.User;
import com.dozernet.module3_fleet.entity.Machine;
import com.dozernet.module3_fleet.entity.MachineStatus;
import com.dozernet.module3_fleet.entity.MachineType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Repository (DAO) pattern for machines, including the public catalogue search.
 */
public interface MachineRepository extends JpaRepository<Machine, Long> {

    @Query("""
            select m from Machine m
            left join fetch m.owner
            order by m.model asc
            """)
    List<Machine> findAllWithOwner();

    @Query("""
            select m from Machine m
            left join fetch m.owner
            where m.owner = :owner
            order by m.model asc
            """)
    List<Machine> findByOwnerWithOwner(@Param("owner") User owner);

    @Query("""
            select m from Machine m
            left join fetch m.owner
            where m.verified = false
            order by m.createdAt desc
            """)
    List<Machine> findUnverifiedWithOwner();

    @Query("""
            select m from Machine m
            left join fetch m.owner
            where m.id = :id
            """)
    java.util.Optional<Machine> findByIdWithOwner(@Param("id") Long id);

    List<Machine> findByOwner(User owner);

    List<Machine> findByVerifiedFalse();

    List<Machine> findByStatus(MachineStatus status);

    boolean existsByRegistrationNumber(String registrationNumber);

    long countByStatus(MachineStatus status);

    /**
     * Public catalogue search: only verified + available machines, filtered by
     * optional type and a location/model keyword.
     */
    @Query("""
            select m from Machine m
            where m.verified = true and m.status = com.dozernet.module3_fleet.entity.MachineStatus.AVAILABLE
              and (:type is null or m.type = :type)
              and (:keyword is null or lower(m.location) like lower(concat('%', :keyword, '%'))
                                    or lower(m.model)    like lower(concat('%', :keyword, '%')))
            order by m.dailyRate asc
            """)
    List<Machine> search(@Param("type") MachineType type, @Param("keyword") String keyword);

    /**
     * Public catalogue search filtered to any of the given machine types
     * (used by landing-page job categories such as Digging / Loading).
     */
    @Query("""
            select m from Machine m
            where m.verified = true and m.status = com.dozernet.module3_fleet.entity.MachineStatus.AVAILABLE
              and m.type in :types
              and (:keyword is null or lower(m.location) like lower(concat('%', :keyword, '%'))
                                    or lower(m.model)    like lower(concat('%', :keyword, '%')))
            order by m.dailyRate asc
            """)
    List<Machine> searchByTypes(@Param("types") Collection<MachineType> types,
                                @Param("keyword") String keyword);
}