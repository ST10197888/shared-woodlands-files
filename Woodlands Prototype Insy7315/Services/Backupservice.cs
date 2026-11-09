using System.Text;
using System.Text.Json;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Services
{
 
    // Makes backups, previews restores and applies restores or rollbacks.
    // The database is only ever touched by the Node API (api/admin/export and api/admin/import); this class keeps the snapshot files and the images on the website's disk.

    public class BackupService
    {
        // One backup / restore at a time on this server
        private static readonly SemaphoreSlim Gate = new(1, 1);
        private static readonly JsonSerializerOptions Json = new() { PropertyNameCaseInsensitive = true };

        private readonly IHttpClientFactory _http;
        private readonly BackupStore _store;
        private readonly ILogger<BackupService> _log;

        public BackupService(IHttpClientFactory http, BackupStore store, ILogger<BackupService> log)
        {
            _http = http;
            _store = store;
            _log = log;
        }

        // Back up now


        public async Task<(BackupItem? Item, string? Error)> BackupNowAsync(
            string? by, string? note, CancellationToken ct = default)
        {
            if (!await Gate.WaitAsync(0, ct))
                return (null, "Another backup or restore is already running. Try again in a moment.");

            try
            {
                var (live, error) = await ExportLiveAsync(ct);
                if (live == null) return (null, error);

                var name = _store.NewName(BackupStore.Backup);
                await _store.SaveAsync(BackupStore.Backup, name, NewMeta(BackupStore.Backup, by, note, "manual", null), live, ct);

                return (_store.Get(BackupStore.Backup, name), null);
            }
            catch (Exception ex)
            {
                _log.LogError(ex, "Backup could not be saved");
                return (null, "The backup could not be saved to disk.");
            }
            finally
            {
                Gate.Release();
            }
        }

        // Review - nothing is changed


        public async Task<(BackupReviewViewModel? Model, string? Error)> ReviewAsync(
            string? kind, string? name, string? mode, CancellationToken ct = default)
        {
            if (!IsMode(mode)) return (null, "Unknown action.");

            var item = _store.Get(kind, name);
            if (item == null) return (null, "That snapshot was not found.");

            var snapshot = await _store.LoadAsync(kind!, name!, ct);
            if (snapshot == null) return (null, "That snapshot could not be read.");

            var (result, error) = await CallImportAsync(snapshot.Data, mode!, null, dryRun: true, ct);
            if (result == null) return (null, error);

            var model = new BackupReviewViewModel
            {
                Source = item,
                Mode = mode!,
                SafetyFolder = BackupStore.FolderFor(mode!),
                SafetyFileName = $"<date>_<time>-{mode}.json"
            };

            foreach (var (table, row) in result.Tables)
            {
                if (!string.IsNullOrEmpty(row.Error))
                    return (null, $"Could not compare the \"{table}\" table: {row.Error}");

                model.Tables.Add(new TableReviewRow
                {
                    Table = table,
                    InSnapshot = row.InSnapshot,
                    InDatabase = row.InDatabase,
                    ToAdd = row.ToAdd,
                    ToUpdate = row.ToUpdate,
                    ToDelete = row.ToDelete
                });
            }

            return (model, null);
        }

        // Restore or rollback


        public async Task<(string? Message, string? Error)> ApplyAsync(
            string? kind, string? name, string? mode, string? scope, string? table,
            string? confirmText, string? by)
        {
            // Deliberately no request CancellationToken: closing the browser tab must not stop a restore half way.
            var ct = CancellationToken.None;

            if (!IsMode(mode)) return (null, "Unknown action.");

            var word = mode == BackupStore.Rollback ? "ROLLBACK" : "RESTORE";
            if (!string.Equals(confirmText?.Trim(), word, StringComparison.Ordinal))
                return (null, $"Nothing was changed. Type {word} to confirm.");

            if (scope != "all" && scope != "table") return (null, "Unknown scope.");

            var snapshot = await _store.LoadAsync(kind ?? "", name ?? "", ct);
            if (snapshot == null) return (null, "That snapshot was not found.");

            string? onlyTable = null;
            if (scope == "table")
            {
                if (string.IsNullOrWhiteSpace(table) || !snapshot.Data.ContainsKey(table))
                    return (null, "Pick a table that is in this snapshot.");
                onlyTable = table;
            }

            if (!await Gate.WaitAsync(0, ct))
                return (null, "Another backup or restore is already running. Try again in a moment.");

            try
            {
                // 1. Safety copy of the live data (so this can be undone)
                var (live, exportError) = await ExportLiveAsync(ct);
                if (live == null)
                    return (null, "Nothing was changed. The safety copy could not be made: " + exportError);

                var safetyName = _store.NewName(mode!);
                var safetyFolder = BackupStore.FolderFor(mode!);
                try
                {
                    await _store.SaveAsync(mode!, safetyName,
                        NewMeta(mode!, by, $"Safety copy before {mode} of {name}", mode!, $"{kind}/{name}"), live, ct);
                }
                catch (Exception ex)
                {
                    _log.LogError(ex, "Safety copy could not be saved");
                    return (null, "Nothing was changed. The safety copy could not be saved to disk.");
                }

                // 2. Write the data back through the API
                var (result, importError) = await CallImportAsync(snapshot.Data, mode!, onlyTable, dryRun: false, ct);
                if (result == null)
                    return (null, $"The {mode} failed: {importError} A safety copy was saved as {safetyName}.json in {safetyFolder}.");

                var failedTables = result.Tables
                    .Where(t => !string.IsNullOrEmpty(t.Value.Error))
                    .Select(t => $"{t.Key} ({t.Value.Error})")
                    .ToList();

                // 3. Put the images back
                ISet<string>? onlyImages = onlyTable == null
                    ? null
                    : BackupStore.CollectImagePaths(new Dictionary<string, JsonElement> { [onlyTable] = snapshot.Data[onlyTable] });
                var images = _store.RestoreImages(kind!, name!, snapshot.Meta, onlyImages);

                var added = result.Tables.Values.Sum(t => t.ToAdd);
                var updated = result.Tables.Values.Sum(t => t.ToUpdate);
                var deleted = result.Tables.Values.Sum(t => t.ToDelete);

                var summary = new StringBuilder();
                summary.Append(mode == BackupStore.Rollback ? "Rolled back" : "Restored");
                summary.Append($" from {name}{(onlyTable != null ? $" (table {onlyTable} only)" : "")}: ");
                summary.Append($"{added:N0} added, {updated:N0} overwritten");
                if (mode == BackupStore.Rollback) summary.Append($", {deleted:N0} deleted");
                summary.Append($". Images: {images.Restored} restored, {images.Unchanged} already identical");
                if (images.Failed > 0) summary.Append($", {images.Failed} could not be restored");
                summary.Append($". Safety copy: {safetyName}.json in {safetyFolder}.");

                if (failedTables.Count > 0)
                    return (null, $"The {mode} only partly worked. These tables failed: {string.Join(", ", failedTables)}. " +
                                  $"Restore the safety copy {safetyName}.json from {safetyFolder} to go back. {summary}");

                return (summary.ToString(), null);
            }
            finally
            {
                Gate.Release();
            }
        }


        // Node API calls

        // The website's HttpClient already sends the signed-in admin's token (ApiAuthHandler).
        private HttpClient Client()
        {
            var client = _http.CreateClient("NodeApi");
            client.Timeout = TimeSpan.FromMinutes(5); // a full snapshot can be several MB
            return client;
        }

        private async Task<(Dictionary<string, JsonElement>? Data, string? Error)> ExportLiveAsync(CancellationToken ct)
        {
            try
            {
                var response = await Client().GetAsync("api/admin/export", ct);
                var body = await response.Content.ReadAsStringAsync(ct);
                if (!response.IsSuccessStatusCode) return (null, ApiError(response, body));

                var export = JsonSerializer.Deserialize<NodeExport>(body, Json);
                if (export?.Data == null || export.Data.Count == 0) return (null, "The API returned no data.");
                if (export.Errors is { Count: > 0 })
                    return (null, "Some tables could not be read: " + string.Join(", ", export.Errors.Keys) + ".");

                return (export.Data, null);
            }
            catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or JsonException)
            {
                _log.LogError(ex, "Export from the API failed");
                return (null, "Could not reach the Woodlands API. Please try again.");
            }
        }

        private async Task<(NodeImportResult? Result, string? Error)> CallImportAsync(
            Dictionary<string, JsonElement> data, string mode, string? table, bool dryRun, CancellationToken ct)
        {
            try
            {
                var payload = new Dictionary<string, object?>
                {
                    ["data"] = data,
                    ["mode"] = mode,
                    ["table"] = table,
                    ["dryRun"] = dryRun
                };
                using var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");

                var response = await Client().PostAsync("api/admin/import", content, ct);
                var body = await response.Content.ReadAsStringAsync(ct);
                if (!response.IsSuccessStatusCode) return (null, ApiError(response, body));

                var result = JsonSerializer.Deserialize<NodeImportResult>(body, Json);
                return result == null ? (null, "The API sent an empty answer.") : (result, null);
            }
            catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or JsonException)
            {
                _log.LogError(ex, "Import call to the API failed");
                return (null, "Could not reach the Woodlands API.");
            }
        }

        private static string ApiError(HttpResponseMessage response, string body)
        {
            try
            {
                using var doc = JsonDocument.Parse(body);
                if (doc.RootElement.TryGetProperty("error", out var error) && error.ValueKind == JsonValueKind.String)
                    return error.GetString() ?? "The API refused the request.";
            }
            catch (Exception) { /* not json */ }

            return $"The API answered {(int)response.StatusCode}.";
        }

        // Helpers
        
        private static bool IsMode(string? mode) => mode is BackupStore.Restore or BackupStore.Rollback;

        private static SnapshotMeta NewMeta(string kind, string? by, string? note, string trigger, string? source)
        {
            var trimmed = note?.Trim();
            if (trimmed is { Length: > 120 }) trimmed = trimmed[..120];

            return new SnapshotMeta
            {
                Kind = kind,
                CreatedAt = DateTime.UtcNow,
                CreatedBy = by,
                Note = string.IsNullOrEmpty(trimmed) ? null : trimmed,
                Trigger = trigger,
                Source = source
            };
        }
    }
}