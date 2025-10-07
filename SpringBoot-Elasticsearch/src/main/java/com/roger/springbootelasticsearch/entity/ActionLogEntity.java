package com.roger.springbootelasticsearch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author RogerLo
 * @date 2025/9/19
 */
@Entity
@Table(name = "ACTION_LOG", schema = "dbo", catalog = "ACTION_LOG_DB")
@Data
public class ActionLogEntity {

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "LOG_JSON", columnDefinition = "NVARCHAR(MAX)")
    private String logJson;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

}
