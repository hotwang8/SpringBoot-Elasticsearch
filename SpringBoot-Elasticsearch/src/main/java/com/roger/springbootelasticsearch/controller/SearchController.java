package com.roger.springbootelasticsearch.controller;

import com.roger.springbootelasticsearch.service.ElasticsearchService;
import com.roger.springbootelasticsearch.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author RogerLo
 * @date 2025/9/19
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private SyncService syncService;

    /**
     * 依欄位搜尋
     */
    @GetMapping("/field")
    public ResponseEntity<List<Map<String, Object>>> searchByField(
            @RequestParam String fieldName,
            @RequestParam String value) throws IOException {

        List<Map<String, Object>> results = elasticsearchService.searchByField(fieldName, value);
        return ResponseEntity.ok(results);
    }

    /**
     * 多欄位查詢
     */
    @PostMapping("/multiple-fields")
    public ResponseEntity<List<Map<String, Object>>> searchMultipleFields(
            @RequestBody Map<String, Object> conditions) throws IOException {

        List<Map<String, Object>> results = elasticsearchService.searchMultipleFields(conditions);
        return ResponseEntity.ok(results);
    }

    /**
     * 查詢字串搜尋 (Kibana 風格)
     */
    @GetMapping("/query")
    public ResponseEntity<List<Map<String, Object>>> queryStringSearch(
            @RequestParam String q) throws IOException {

        List<Map<String, Object>> results = elasticsearchService.queryStringSearch(q);
        return ResponseEntity.ok(results);
    }

    /**
     * 時間範圍查詢
     */
    @GetMapping("/time-range")
    public ResponseEntity<List<Map<String, Object>>> timeRangeSearch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) throws IOException {

        List<Map<String, Object>> results = elasticsearchService.timeRangeSearch(from, to);
        return ResponseEntity.ok(results);
    }

    /**
     * 全文搜尋
     */
    @GetMapping("/full-text")
    public ResponseEntity<List<Map<String, Object>>> fullTextSearch(
            @RequestParam String searchTerm) throws IOException {

        List<Map<String, Object>> results = elasticsearchService.fullTextSearch(searchTerm);
        return ResponseEntity.ok(results);
    }

    /**
     * 取得索引統計
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getIndexStats() throws IOException {
        Map<String, Object> stats = elasticsearchService.getIndexStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 手動觸發完整同步
     */
    @PostMapping("/sync/full")
    public ResponseEntity<String> triggerFullSync() {
        new Thread(() -> syncService.performFullSync()).start();
        return ResponseEntity.ok("完整同步已開始執行");
    }

    /**
     * 手動觸發增量同步
     */
    @PostMapping("/sync/incremental")
    public ResponseEntity<String> triggerIncrementalSync() {
        syncService.performIncrementalSync();
        return ResponseEntity.ok("增量同步已執行");
    }

    /**
     * 手動同步特定記錄
     */
    @PostMapping("/sync/record/{id}")
    public ResponseEntity<String> syncSpecificRecord(@PathVariable Long id) throws IOException {
        syncService.syncSpecificRecord(id);
        return ResponseEntity.ok("記錄 ID " + id + " 已同步");
    }

    /**
     * 取得同步狀態
     */
    @GetMapping("/sync/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        Map<String, Object> status = syncService.getSyncStatus();
        return ResponseEntity.ok(status);
    }

}