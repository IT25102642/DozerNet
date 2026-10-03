package com.dozernet.common.document;

import com.dozernet.common.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository (DAO) pattern via Spring Data JPA - uploaded document metadata.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOwnerOrderByCreatedAtDesc(User owner);

}
