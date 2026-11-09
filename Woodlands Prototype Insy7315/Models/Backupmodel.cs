using System.Text.Json;
using System.Text.Json.Serialization;

namespace Woodlands_Prototype_Insy7315.Models
{
    //One snapshot on disk: a backup, a restore point or a rollback.
    public class BackupItem
    {
        public string Name { get; set; } = "";                 // 2026-11-03_12-00-00-backup
        public string Kind { get; set; } = "backup";           // backup | restore | rollback
        public string FileName => Name + ".json";
        public DateTime CreatedAt { get; set; }                // South Africa time (taken from the name)
        public string? CreatedBy { get; set; }
        public string? Note { get; set; }
        public long SizeBytes { get; set; }                    // json + images
        public int ImageCount { get; set; }
        public Dictionary<string, int> TableCounts { get; set; } = new();
        public int TotalRows => TableCounts.Values.Sum();
    }

    public class BackupsIndexViewModel
    {
        public List<BackupItem> Backups { get; set; } = new();
        public List<BackupItem> RestorePoints { get; set; } = new();
        public List<BackupItem> Rollbacks { get; set; } = new();
        public DateTime? LastBackupAt { get; set; }
        public string StoragePath { get; set; } = "";
    }

    public class TableReviewRow
    {
        public string Table { get; set; } = "";
        public int InSnapshot { get; set; }
        public int InDatabase { get; set; }
        public int ToAdd { get; set; }
        public int ToUpdate { get; set; }
        public int ToDelete { get; set; }
    }

    public class BackupReviewViewModel
    {
        public BackupItem Source { get; set; } = new();
        public string Mode { get; set; } = "restore";          // restore | rollback
        public List<TableReviewRow> Tables { get; set; } = new();
        public string SafetyFolder { get; set; } = "";
        public string SafetyFileName { get; set; } = "";
    }

    // Uses the same shape as the files the Node API's scheduled backups write.


    public class SnapshotFile
    {
        [JsonPropertyName("meta")] public SnapshotMeta Meta { get; set; } = new();
        [JsonPropertyName("data")] public Dictionary<string, JsonElement> Data { get; set; } = new();
    }

    public class SnapshotMeta
    {
        [JsonPropertyName("version")] public int Version { get; set; } = 1;
        [JsonPropertyName("kind")] public string Kind { get; set; } = "backup";
        [JsonPropertyName("created_at")] public DateTime CreatedAt { get; set; }      // UTC
        [JsonPropertyName("created_by")] public string? CreatedBy { get; set; }
        [JsonPropertyName("note")] public string? Note { get; set; }
        [JsonPropertyName("trigger")] public string Trigger { get; set; } = "manual";
        [JsonPropertyName("source")] public string? Source { get; set; }              // safety copies: "backup/<name>" that was applied
        [JsonPropertyName("counts")] public Dictionary<string, int> Counts { get; set; } = new();
        [JsonPropertyName("images")] public List<SnapshotImage> Images { get; set; } = new();
        [JsonPropertyName("missing_images")] public List<string> MissingImages { get; set; } = new();
    }

    //An image file saved with the snapshot: where it lives on the site and where it was saved
    public class SnapshotImage
    {
        [JsonPropertyName("path")] public string Path { get; set; } = "";     // /images/products/Kitchen Unit 12.jpeg
        [JsonPropertyName("file")] public string File { get; set; } = "";     // images/001.jpeg (inside the snapshot folder)
    }

    // Node API responses
   
    public class NodeExport
    {
        [JsonPropertyName("counts")] public Dictionary<string, int>? Counts { get; set; }
        [JsonPropertyName("errors")] public Dictionary<string, string>? Errors { get; set; }
        [JsonPropertyName("data")] public Dictionary<string, JsonElement>? Data { get; set; }
    }

    public class NodeImportResult
    {
        [JsonPropertyName("ok")] public bool Ok { get; set; }
        [JsonPropertyName("tables")] public Dictionary<string, NodeImportTable> Tables { get; set; } = new();
    }

    public class NodeImportTable
    {
        [JsonPropertyName("in_snapshot")] public int InSnapshot { get; set; }
        [JsonPropertyName("in_database")] public int InDatabase { get; set; }
        [JsonPropertyName("to_add")] public int ToAdd { get; set; }
        [JsonPropertyName("to_update")] public int ToUpdate { get; set; }
        [JsonPropertyName("to_delete")] public int ToDelete { get; set; }
        [JsonPropertyName("error")] public string? Error { get; set; }
    }
}