package com.roger.springbootelasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author RogerLo
 * @date 2025/9/19
 */
@Service
@Slf4j
public class ElasticsearchService {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.elasticsearch.index.name}")
    private String indexName;

    @Value("${spring.elasticsearch.index.shards:1}")
    private String numberOfShards;

    @Value("${spring.elasticsearch.index.replicas:1}")
    private String numberOfReplicas;

    @PostConstruct
    public void initializeIndex() throws IOException {
        this.createIndexIfNotExists();
    }

    /**
     * 建立索引 (如果不存在)
     */
    private void createIndexIfNotExists() throws IOException {
        ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));

        if (!elasticsearchClient.indices().exists(existsRequest).value()) {
            CreateIndexRequest request = CreateIndexRequest.of(c -> c
                .index(indexName)
                .mappings(m -> m
                        .dynamic(co.elastic.clients.elasticsearch._types.mapping.DynamicMapping.True)
                        // strict_date_optional_time 是 Elasticsearch 的預設時間格式
                        .properties("timestamp", Property.of(p -> p.date(d -> d.format("strict_date_optional_time||yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"))))
                        .properties("_raw_json", Property.of(p -> p.text(t -> t.index(false).store(true))))
                        .properties("_db_id", Property.of(p -> p.keyword(k -> k.store(true))))
                )
                .settings(s -> s
                        .numberOfShards(numberOfShards)
                        .numberOfReplicas(numberOfReplicas)
                )
            );

            elasticsearchClient.indices().create(request);
            log.info("索引 '{}' 建立成功", indexName);
        }
    }

    /**
     * 索引 JSON 文件
     */
    public String indexJsonDocument(Long dbId, String jsonString) throws IOException {
        try {
            // 解析 JSON 並加入額外欄位
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            Map<String, Object> document = objectMapper.convertValue(jsonNode, Map.class);

            // 加入元資料
            document.put("timestamp", LocalDateTime.now().toString());
            document.put("_raw_json", jsonString);
            document.put("_db_id", dbId);
            document.put("_indexed_at", System.currentTimeMillis());

            IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                    .index(indexName)
                    .id(dbId.toString())
                    .document(document)
            );

            IndexResponse response = elasticsearchClient.index(request);
            log.debug("文件已索引，DB ID: {}, ES ID: {}", dbId, response.id());
            return response.id();

        } catch (Exception e) {
            log.error("索引文件失敗，DB ID: {}", dbId, e);
            throw new IOException("索引文件失敗", e);
        }
    }

    /**
     * 批次索引文件
     */
    public void bulkIndexDocuments(Map<Long, String> documents) throws IOException {
        if (documents.isEmpty()) return;

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        for (Map.Entry<Long, String> entry : documents.entrySet()) {
            Long dbId = entry.getKey();
            String jsonString = entry.getValue();

            try {
                JsonNode jsonNode = objectMapper.readTree(jsonString);
                Map<String, Object> document = objectMapper.convertValue(jsonNode, Map.class);

                document.put("timestamp", LocalDateTime.now().toString());
                document.put("_raw_json", jsonString);
                document.put("_db_id", dbId);
                document.put("_indexed_at", System.currentTimeMillis());

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(indexName)
                                .id(dbId.toString())
                                .document(document)
                        )
                );

            } catch (Exception e) {
                log.error("準備批次索引失敗，DB ID: {}", dbId, e);
            }
        }

        BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

        if (response.errors()) {
            log.warn("批次索引有部分錯誤");
            response.items().forEach(item -> {
                if (item.error() != null) {
                    log.error("索引錯誤: {}", item.error().reason());
                }
            });
        } else {
            log.info("批次索引完成，處理 {} 筆資料", documents.size());
        }
    }

    /**
     * Kibana 風格的欄位搜尋
     */
    public List<Map<String, Object>> searchByField(String fieldName, Object value) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s
                .index(indexName)
                .query(q -> q
                        .match(t -> t
                                .field(fieldName)
                                .query(value.toString())
                        )
                )
                .size(100)
                .sort(so -> so.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
        );

        return this.executeSearchAndGetResults(request);
    }

    /**
     * 多欄位查詢
     */
    public List<Map<String, Object>> searchMultipleFields(Map<String, Object> conditions) throws IOException {
        List<Query> mustQueries = new ArrayList<>();

        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            Query query = Query.of(q -> q
                    .term(t -> t
                            .field(fieldName + ".keyword")
                            .value(v -> v.stringValue(value.toString()))
                    )
            );
            mustQueries.add(query);
        }

        SearchRequest request = SearchRequest.of(s -> s
                .index(indexName)
                .query(q -> q
                        .bool(b -> b.must(mustQueries))
                )
                .size(100)
                .sort(so -> so.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
        );

        return executeSearchAndGetResults(request);
    }

    /**
     * 查詢字串搜尋 (Kibana 風格)
     */
    public List<Map<String, Object>> queryStringSearch(String queryString) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s
                .index(indexName)
                .query(q -> q
                        .queryString(qs -> qs
                                .query(queryString)
                                .defaultField("*")
                        )
                )
                .size(100)
                .sort(so -> so.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
        );

        return executeSearchAndGetResults(request);
    }

    /**
     * 時間範圍查詢
     */
    public List<Map<String, Object>> timeRangeSearch(LocalDateTime from, LocalDateTime to) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s
                .index(indexName)
                .query(q -> q
                        .range(r -> r
                                .field("timestamp")
                                .gte(co.elastic.clients.json.JsonData.of(from.toString()))
                                .lte(co.elastic.clients.json.JsonData.of(to.toString()))
                        )
                )
                .size(100)
                .sort(so -> so.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
        );

        return executeSearchAndGetResults(request);
    }

    /**
     * 全文搜尋
     */
    public List<Map<String, Object>> fullTextSearch(String searchTerm) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s
                .index(indexName)
                .query(q -> q
                        .multiMatch(m -> m
                                .query(searchTerm)
                                .fields("*")
                        )
                )
                .size(100)
                .sort(so -> so.field(f -> f.field("timestamp").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
        );

        return executeSearchAndGetResults(request);
    }

    /**
     * 執行搜尋並取得結果
     */
    private List<Map<String, Object>> executeSearchAndGetResults(SearchRequest request) throws IOException {
        SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Hit<Map> hit : response.hits().hits()) {
            Map<String, Object> result = new HashMap<>(hit.source());
            result.put("_id", hit.id());
            result.put("_score", hit.score());
            results.add(result);
        }

        log.debug("搜尋完成，找到 {} 筆資料", results.size());
        return results;
    }

    /**
     * 刪除文件
     */
    public void deleteDocument(Long dbId) throws IOException {
        DeleteRequest request = DeleteRequest.of(d -> d
                .index(indexName)
                .id(dbId.toString())
        );

        DeleteResponse response = elasticsearchClient.delete(request);
        log.debug("文件刪除，DB ID: {}, 結果: {}", dbId, response.result());
    }

    /**
     * 取得索引統計
     */
    public Map<String, Object> getIndexStats() throws IOException {
        // 使用搜尋 API 取得統計資訊
        SearchRequest request = SearchRequest.of(s -> s
                .index(indexName)
                .size(0)
        );

        SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", response.hits().total().value());
        stats.put("indexName", indexName);

        return stats;
    }
}