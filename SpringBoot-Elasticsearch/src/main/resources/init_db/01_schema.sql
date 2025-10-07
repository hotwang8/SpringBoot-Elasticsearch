USE ACTION_LOG_DB;

-- 檢查名為 dbo.ACTION_LOG 的資料表是否存在，如果存在就刪除，避免建立時發生衝突
IF OBJECT_ID('dbo.ACTION_LOG', 'U') IS NOT NULL
DROP TABLE dbo.ACTION_LOG;

-- 建立 dbo.ACTION_LOG 資料表
CREATE TABLE dbo.ACTION_LOG (
    ID BIGINT IDENTITY(1, 1) PRIMARY KEY, -- ID 欄位，設為 BIGINT 型別，自動增量 (1,1)，並設為主鍵
    LOG_JSON NVARCHAR(MAX) NULL, -- LOG_JSON 欄位，用於儲存大量的 JSON 字串
    CREATED_AT DATETIME2 NULL, -- 建立時間戳記
    UPDATED_AT DATETIME2 NULL -- 更新時間戳記
);

-- 建立索引
CREATE INDEX IX_log_table_created_at ON dbo.ACTION_LOG (CREATED_AT);
CREATE INDEX IX_log_table_updated_at ON dbo.ACTION_LOG (UPDATED_AT);