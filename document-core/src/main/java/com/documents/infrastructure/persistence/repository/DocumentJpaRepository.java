package com.documents.infrastructure.persistence.repository;

import com.documents.infrastructure.persistence.entity.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentJpaRepository extends JpaRepository<DocumentEntity, String> {

    Optional<DocumentEntity> findByIdAndTenantId(String id, String tenantId);

    Optional<DocumentEntity> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    @Query("""
            SELECT d FROM DocumentEntity d
            WHERE d.product = :product
              AND d.tenantId = :tenantId
              AND d.ownerType = :ownerType
              AND d.ownerId = :ownerId
              AND d.deletedAt IS NULL
              AND (:purpose IS NULL OR d.purpose = :purpose)
              AND (:includeVersions = TRUE OR NOT EXISTS (
                  SELECT 1 FROM DocumentEntity newer
                  WHERE newer.supersedesDocumentId = d.id
                    AND newer.deletedAt IS NULL
                    AND newer.product = d.product
                    AND newer.tenantId = d.tenantId
                    AND newer.ownerType = d.ownerType
                    AND newer.ownerId = d.ownerId
                    AND newer.purpose = d.purpose
                    AND newer.status = 'READY'
              ))
            """)
    Page<DocumentEntity> findActive(
            @Param("product") String product,
            @Param("tenantId") String tenantId,
            @Param("ownerType") String ownerType,
            @Param("ownerId") String ownerId,
            @Param("purpose") String purpose,
            @Param("includeVersions") boolean includeVersions,
            Pageable pageable);

    boolean existsBySupersedesDocumentIdAndDeletedAtIsNull(String supersedesDocumentId);

    @Query("""
            SELECT d FROM DocumentEntity d
            WHERE d.tenantId = :tenantId
              AND d.id IN :ids
              AND d.deletedAt IS NULL
            """)
    List<DocumentEntity> findByIdsAndTenantId(
            @Param("ids") Collection<String> ids,
            @Param("tenantId") String tenantId);

    @Query("""
            SELECT d FROM DocumentEntity d
            WHERE d.tenantId = :tenantId
              AND d.deletedAt IS NOT NULL
              AND d.deletedAt < :deletedBefore
            ORDER BY d.deletedAt ASC
            """)
    List<DocumentEntity> findDeletedBefore(
            @Param("tenantId") String tenantId,
            @Param("deletedBefore") Instant deletedBefore,
            Pageable pageable);
}
