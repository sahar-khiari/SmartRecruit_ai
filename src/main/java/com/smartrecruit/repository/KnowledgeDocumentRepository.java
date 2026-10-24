package com.smartrecruit.repository;

import com.smartrecruit.entity.KnowledgeCategory;
import com.smartrecruit.entity.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.smartrecruit.entity.IndexingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    /**
     * Recherche administrateur : catégorie et statut facultatifs, recherche simple dans le titre.
     * Les paramètres nuls ne filtrent pas.
     */
    @Query("""
            select d from KnowledgeDocument d
            where (:category is null or d.category = :category)
              and (:active is null or d.active = :active)
              and (:search is null or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<KnowledgeDocument> searchForAdmin(@Param("category") KnowledgeCategory category,
                                           @Param("active") Boolean active,
                                           @Param("search") String search,
                                           Pageable pageable);

    /**
     * Recherche publique : uniquement les documents actifs.
     */
    @Query("""
            select d from KnowledgeDocument d
            where d.active = true
              and (:category is null or d.category = :category)
              and (:search is null or lower(d.title) like lower(concat('%', :search, '%')))
            """)
    Page<KnowledgeDocument> searchActive(@Param("category") KnowledgeCategory category,
                                         @Param("search") String search,
                                         Pageable pageable);

    /**
     * Identifiants des documents actifs pour une réindexation globale bornée.
     */
    @Query("""
            select d.id from KnowledgeDocument d
            where d.active = true
            order by d.id asc
            """)
    List<Long> findActiveIds(Pageable pageable);

    /**
     * Mise à jour technique du statut sans modifier le contenu ni incrémenter
     * la version métier utilisée dans les identifiants de chunks.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update KnowledgeDocument d
               set d.indexingStatus = :status,
                   d.indexedAt = :indexedAt,
                   d.indexingError = :error
             where d.id = :id
            """)
    int updateIndexingState(@Param("id") Long id,
                            @Param("status") IndexingStatus status,
                            @Param("indexedAt") LocalDateTime indexedAt,
                            @Param("error") String error);
}
