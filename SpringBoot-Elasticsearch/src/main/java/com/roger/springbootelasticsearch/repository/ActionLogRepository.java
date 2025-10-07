package com.roger.springbootelasticsearch.repository;

import com.roger.springbootelasticsearch.entity.ActionLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author RogerLo
 * @date 2025/9/19
 */
@Repository
public interface ActionLogRepository extends JpaRepository<ActionLogEntity, Long> {

    @Query("SELECT l FROM ActionLogEntity l WHERE l.createdAt > :since ORDER BY l.createdAt ASC")
    List<ActionLogEntity> findByCreatedAtAfter(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT l FROM ActionLogEntity l WHERE l.updatedAt > :since ORDER BY l.updatedAt ASC")
    List<ActionLogEntity> findByUpdatedAtAfter(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT MAX(l.createdAt) FROM ActionLogEntity l")
    LocalDateTime findMaxCreatedAt();

    @Query("SELECT MAX(l.updatedAt) FROM ActionLogEntity l")
    LocalDateTime findMaxUpdatedAt();

}
