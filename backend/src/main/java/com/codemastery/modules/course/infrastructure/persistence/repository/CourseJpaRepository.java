package com.codemastery.modules.course.infrastructure.persistence.repository;

import com.codemastery.modules.course.infrastructure.persistence.entity.CourseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseJpaRepository extends JpaRepository<CourseEntity, UUID> {


    @Query(value = "SELECT * FROM public.courses WHERE publication_status = 'published' "
            + "ORDER BY title COLLATE \"C\" ASC, id ASC",
            countQuery = "SELECT count(*) FROM public.courses WHERE publication_status = 'published'",
            nativeQuery = true)
    Page<CourseEntity> findPublishedCatalogue(Pageable pageable);

    Page<CourseEntity> findByPublicationStatus(String publicationStatus, Pageable pageable);

    Optional<CourseEntity> findByIdAndPublicationStatus(@Param("id") UUID id,
                                                        @Param("publicationStatus") String publicationStatus);
}
