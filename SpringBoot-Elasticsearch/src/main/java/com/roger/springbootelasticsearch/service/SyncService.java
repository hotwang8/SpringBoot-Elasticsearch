package com.roger.springbootelasticsearch.service;

import com.roger.springbootelasticsearch.entity.ActionLogEntity;
import com.roger.springbootelasticsearch.repository.ActionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author RogerLo
 * @date 2025/9/19
 */
@Service
public class SyncService {

    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);

    @Autowired
    private ActionLogRepository actionLogRepository;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Value("${sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${sync.full-sync-on-startup:true}")
    private boolean fullSyncOnStartup;

    @Value("${sync.batch-size:1000}")
    private int batchSize;

    private LocalDateTime lastSyncTime;
    private volatile boolean syncing = false;

    /**
     * 應用程式啟動後執行完整同步
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (syncEnabled && fullSyncOnStartup) {
            logger.info("應用程式已啟動，開始執行完整同步...");
            new Thread(this::performFullSync).start();
        }
    }

    /**
     * 定時執行增量同步 (每30秒執行一次，可在 application.yml 中調整)
     */
    @Scheduled(fixedDelayString = "${sync.incremental-interval-seconds:30}000")
    public void scheduledIncrementalSync() {
        if (syncEnabled && !syncing) {
            this.performIncrementalSync();
        }
    }

    /**
     * 執行完整同步
     */
    @Transactional(readOnly = true)
    public void performFullSync() {
        if (syncing) {
            logger.warn("同步作業進行中，跳過完整同步");
            return;
        }

        syncing = true;
        logger.info("開始執行完整同步...");

        try {
            int page = 0;
            int totalSynced = 0;

            while (true) {
                List<ActionLogEntity> actionLogEntities = actionLogRepository.findAll(PageRequest.of(page, batchSize)).getContent();

                if (actionLogEntities.isEmpty()) {
                    break;
                }

                Map<Long, String> batch = new HashMap<>();
                LocalDateTime maxTimestamp = lastSyncTime;

                for (ActionLogEntity actLog : actionLogEntities) {
                    if (actLog.getLogJson() != null && !actLog.getLogJson().trim().isEmpty()) {
                        batch.put(actLog.getId(), actLog.getLogJson());

                        if (maxTimestamp == null || actLog.getCreatedAt().isAfter(maxTimestamp)) {
                            maxTimestamp = actLog.getCreatedAt();
                        }
                    }
                }

                if (!batch.isEmpty()) {
                    elasticsearchService.bulkIndexDocuments(batch);
                    totalSynced += batch.size();
                }

                lastSyncTime = maxTimestamp;
                page++;
            }

            logger.info("完整同步完成，共同步 {} 筆資料", totalSynced);

        } catch (Exception e) {
            logger.error("完整同步失敗", e);
        } finally {
            syncing = false;
        }
    }

    /**
     * 執行增量同步
     */
    @Transactional(readOnly = true)
    public void performIncrementalSync() {
        if (syncing) {
            return;
        }

        if (lastSyncTime == null) {
            // 如果沒有上次同步時間，取得資料庫中最新的時間戳記
            lastSyncTime = actionLogRepository.findMaxCreatedAt();
            if (lastSyncTime == null) {
                lastSyncTime = LocalDateTime.now().minusHours(1); // 預設同步一小時內的資料
            }
        }

        syncing = true;
        logger.debug("開始執行增量同步，上次同步時間: {}", lastSyncTime);

        try {
            int page = 0;
            int totalSynced = 0;
            LocalDateTime maxTimestamp = lastSyncTime;

            while (true) {
                List<ActionLogEntity> logs = actionLogRepository.findByCreatedAtAfter(
                        lastSyncTime,
                        PageRequest.of(page, batchSize)
                );

                if (logs.isEmpty()) {
                    break;
                }

                Map<Long, String> batch = new HashMap<>();

                for (ActionLogEntity log : logs) {
                    if (log.getLogJson() != null && !log.getLogJson().trim().isEmpty()) {
                        batch.put(log.getId(), log.getLogJson());

                        if (log.getCreatedAt().isAfter(maxTimestamp)) {
                            maxTimestamp = log.getCreatedAt();
                        }
                    }
                }

                if (!batch.isEmpty()) {
                    elasticsearchService.bulkIndexDocuments(batch);
                    totalSynced += batch.size();
                }

                page++;
            }

            if (totalSynced > 0) {
                logger.info("增量同步完成，同步 {} 筆新資料", totalSynced);
                lastSyncTime = maxTimestamp;
            } else {
                logger.debug("增量同步完成，無新資料");
            }

        } catch (Exception e) {
            logger.error("增量同步失敗", e);
        } finally {
            syncing = false;
        }
    }

    /**
     * 手動同步特定記錄
     */
    public void syncSpecificRecord(Long id) throws IOException {
        ActionLogEntity log = actionLogRepository.findById(id).orElse(null);
        if (log != null && log.getLogJson() != null && !log.getLogJson().trim().isEmpty()) {
            elasticsearchService.indexJsonDocument(log.getId(), log.getLogJson());
            logger.info("已手動同步記錄 ID: {}", id);
        } else {
            logger.warn("記錄不存在或資料無效，ID: {}", id);
        }
    }

    /**
     * 取得同步狀態
     */
    public Map<String, Object> getSyncStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("syncEnabled", syncEnabled);
        status.put("syncing", syncing);
        status.put("lastSyncTime", lastSyncTime);
        status.put("batchSize", batchSize);
        return status;
    }
}