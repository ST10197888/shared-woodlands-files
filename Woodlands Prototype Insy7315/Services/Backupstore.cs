using System.Globalization;
using System.IO.Compression;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.RegularExpressions;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Services
{

    // Reads and writes snapshots on disk e.g:
    //  wwwroot/Updates/Backups/        2026-11-03_12-00-00-backup/   2026-11-03_12-00-00-backup.json + meta.json + images/
    //   wwwroot/Updates/Restore points/ 2026-11-03_12-05-10-restore/  (safety copy taken just before a restore)
    //  wwwroot/Updates/Rollbacks/      2026-11-03_12-09-40-rollback/ (safety copy taken just before a rollback)
      
    // Each snapshot has its own folder so its images sit next to its json.

    public class BackupStore
    {
        public const string Backup = "backup";
        public const string Restore = "restore";
        public const string Rollback = "rollback";

        private static readonly Regex NameRx = new(
            @"^\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}-(backup|restore|rollback)$", RegexOptions.Compiled);
        private static readonly Regex ImageRx = new(
            @"\.(jpe?g|png|webp|gif|svg)$", RegexOptions.Compiled | RegexOptions.IgnoreCase);
        private static readonly JsonSerializerOptions WriteOptions = new()
        {
            Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping
        };
        private static readonly JsonSerializerOptions ReadOptions = new() { PropertyNameCaseInsensitive = true };

        private readonly string _webRoot;
        private readonly string _root;
        private readonly ILogger<BackupStore> _log;

        public BackupStore(IWebHostEnvironment env, ILogger<BackupStore> log)
        {
            _log = log;
            _webRoot = Path.GetFullPath(env.WebRootPath ?? Path.Combine(env.ContentRootPath, "wwwroot"));
            _root = Path.Combine(_webRoot, "Updates");

            foreach (var kind in new[] { Backup, Restore, Rollback })
            {
                Directory.CreateDirectory(Path.Combine(_root, FolderFor(kind)));
            }
        }

        public string StoragePath => "wwwroot/Updates";

        // names and folders 

        public static string FolderFor(string kind) => kind switch
        {
            Backup => "Backups",
            Restore => "Restore points",
            Rollback => "Rollbacks",
            _ => throw new ArgumentException("Unknown kind.", nameof(kind))
        };

        public static bool IsKind(string? kind) => kind is Backup or Restore or Rollback;

        // True only for names we generated ourselves, so a name can never point outside the Updates folder.
        public static bool IsValid(string? kind, string? name) =>
            IsKind(kind) && name != null && NameRx.IsMatch(name) && name.EndsWith("-" + kind, StringComparison.Ordinal);

        // South Africa has no daylight saving, so UTC+2 is always right.
        public static DateTime SouthAfricaNow() => DateTime.UtcNow.AddHours(2);

        public static DateTime ParseTime(string name) =>
            DateTime.ParseExact(name[..19], "yyyy-MM-dd_HH-mm-ss", CultureInfo.InvariantCulture);

        // currentdate_timestamp-kind, e.g. 2026-11-03_12-00-00-backup (moves on a second if it is taken).
        public string NewName(string kind)
        {
            var now = SouthAfricaNow();
            string name;
            do
            {
                name = now.ToString("yyyy-MM-dd_HH-mm-ss", CultureInfo.InvariantCulture) + "-" + kind;
                now = now.AddSeconds(1);
            }
            while (Directory.Exists(DirOf(kind, name)));
            return name;
        }

        private string DirOf(string kind, string name) => Path.Combine(_root, FolderFor(kind), name);

        public string JsonPath(string kind, string name) => Path.Combine(DirOf(kind, name), name + ".json");

        // listing 

        public List<BackupItem> List(string kind)
        {
            var items = new List<BackupItem>();
            var folder = Path.Combine(_root, FolderFor(kind));
            if (!Directory.Exists(folder)) return items;

            foreach (var dir in Directory.EnumerateDirectories(folder))
            {
                var name = Path.GetFileName(dir);
                if (IsValid(kind, name) && File.Exists(JsonPath(kind, name)))
                {
                    items.Add(ToItem(kind, name));
                }
            }

            return items.OrderByDescending(i => i.CreatedAt).ToList();
        }

        public BackupItem? Get(string? kind, string? name) =>
            IsValid(kind, name) && File.Exists(JsonPath(kind!, name!)) ? ToItem(kind!, name!) : null;

        private BackupItem ToItem(string kind, string name)
        {
            var meta = ReadMeta(kind, name);
            long size = 0;
            foreach (var file in Directory.EnumerateFiles(DirOf(kind, name), "*", SearchOption.AllDirectories))
            {
                size += new FileInfo(file).Length;
            }

            return new BackupItem
            {
                Name = name,
                Kind = kind,
                CreatedAt = ParseTime(name),
                CreatedBy = meta?.CreatedBy,
                Note = meta?.Note,
                SizeBytes = size,
                ImageCount = meta?.Images.Count ?? 0,
                TableCounts = meta?.Counts ?? new()
            };
        }

        private SnapshotMeta? ReadMeta(string kind, string name)
        {
            try
            {
                var path = Path.Combine(DirOf(kind, name), "meta.json");
                return File.Exists(path)
                    ? JsonSerializer.Deserialize<SnapshotMeta>(File.ReadAllText(path), ReadOptions)
                    : null;
            }
            catch (Exception ex)
            {
                _log.LogWarning(ex, "Could not read meta.json for {Name}", name);
                return null;
            }
        }

        public async Task<SnapshotFile?> LoadAsync(string kind, string name, CancellationToken ct = default)
        {
            if (!IsValid(kind, name) || !File.Exists(JsonPath(kind, name))) return null;
            await using var stream = File.OpenRead(JsonPath(kind, name));
            return await JsonSerializer.DeserializeAsync<SnapshotFile>(stream, ReadOptions, ct);
        }

        //  saving 


        // Writes the snapshot folder: the json, a small meta.json for fast listing, and a copy of every image file the data points at. Web links (https://...) stay in the json only.

        public async Task SaveAsync(string kind, string name, SnapshotMeta meta,
            Dictionary<string, JsonElement> data, CancellationToken ct = default)
        {
            if (!IsValid(kind, name)) throw new ArgumentException("Invalid snapshot name.");

            var dir = DirOf(kind, name);
            if (Directory.Exists(dir)) throw new IOException("That snapshot already exists.");
            Directory.CreateDirectory(dir);

            try
            {
                var imageDir = Path.Combine(dir, "images");
                var number = 0;
                meta.Images = new();
                meta.MissingImages = new();

                foreach (var webPath in CollectImagePaths(data).OrderBy(p => p, StringComparer.Ordinal))
                {
                    var source = ResolveUnderWebRoot(webPath, mustExist: true);
                    if (source == null)
                    {
                        meta.MissingImages.Add(webPath);
                        continue;
                    }

                    Directory.CreateDirectory(imageDir);
                    number++;
                    var saved = $"{number:000}{Path.GetExtension(source).ToLowerInvariant()}";
                    File.Copy(source, Path.Combine(imageDir, saved), overwrite: false);
                    meta.Images.Add(new SnapshotImage { Path = webPath, File = "images/" + saved });
                }

                meta.Counts = data.ToDictionary(
                    kv => kv.Key,
                    kv => kv.Value.ValueKind == JsonValueKind.Array ? kv.Value.GetArrayLength() : 0);

                var file = new SnapshotFile { Meta = meta, Data = data };
                await using (var stream = File.Create(JsonPath(kind, name)))
                {
                    await JsonSerializer.SerializeAsync(stream, file, WriteOptions, ct);
                }

                await File.WriteAllTextAsync(
                    Path.Combine(dir, "meta.json"),
                    JsonSerializer.Serialize(meta, WriteOptions),
                    ct);
            }
            catch
            {
                try { Directory.Delete(dir, recursive: true); } catch { /* leave nothing half-written if we can help it */ }
                throw;
            }
        }

        //  downloading 

        // A zip of the whole snapshot folder (json + images). Deleted from disk when the download ends.
        public FileStream CreateZip(string kind, string name)
        {
            var temp = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N") + ".zip");
            ZipFile.CreateFromDirectory(DirOf(kind, name), temp, CompressionLevel.Fastest, includeBaseDirectory: true);
            return new FileStream(temp, FileMode.Open, FileAccess.Read, FileShare.Read, 4096, FileOptions.DeleteOnClose);
        }

        // images

        // Every local image path ("/images/...jpg") found anywhere in the data, including inside JSON-array text columns.
        public static HashSet<string> CollectImagePaths(Dictionary<string, JsonElement> data)
        {
            var found = new HashSet<string>(StringComparer.Ordinal);
            foreach (var table in data.Values) Walk(table, found, 0);
            return found;
        }

        private static void Walk(JsonElement element, HashSet<string> found, int depth)
        {
            if (depth > 6) return;

            switch (element.ValueKind)
            {
                case JsonValueKind.Array:
                    foreach (var item in element.EnumerateArray()) Walk(item, found, depth);
                    break;

                case JsonValueKind.Object:
                    foreach (var property in element.EnumerateObject()) Walk(property.Value, found, depth);
                    break;

                case JsonValueKind.String:
                {
                    var text = element.GetString();
                    if (string.IsNullOrEmpty(text)) break;

                    if (IsLocalImage(text))
                    {
                        found.Add(text);
                    }
                    else if (text[0] == '[')
                    {
                        // products.gallery / features / finishes are stored as JSON text
                        try
                        {
                            using var inner = JsonDocument.Parse(text);
                            Walk(inner.RootElement, found, depth + 1);
                        }
                        catch (JsonException) { /* ordinary text that happens to start with [ */ }
                    }
                    break;
                }
            }
        }

        private static bool IsLocalImage(string value)
        {
            if (value.Length < 4 || value.Length > 300 || value[0] != '/' || value[1] == '/') return false;
            var cut = value.IndexOfAny(new[] { '?', '#' });
            var path = cut >= 0 ? value[..cut] : value;
            return ImageRx.IsMatch(path) && !path.Split('/').Any(s => s == "..");
        }

        // Copies the images saved with a snapshot back onto the site. Returns (restored, already identical, failed).
        public (int Restored, int Unchanged, int Failed) RestoreImages(
            string kind, string name, SnapshotMeta meta, ISet<string>? only = null)
        {
            int restored = 0, unchanged = 0, failed = 0;
            var dir = Path.GetFullPath(DirOf(kind, name));

            foreach (var image in meta.Images)
            {
                if (only != null && !only.Contains(image.Path)) continue;

                try
                {
                    if (!IsLocalImage(image.Path)) { failed++; continue; }

                    var saved = Path.GetFullPath(Path.Combine(dir, image.File.Replace('/', Path.DirectorySeparatorChar)));
                    if (!saved.StartsWith(dir + Path.DirectorySeparatorChar, StringComparison.Ordinal) || !File.Exists(saved))
                    {
                        failed++;
                        continue;
                    }

                    var destination = ResolveUnderWebRoot(image.Path, mustExist: false);
                    if (destination == null) { failed++; continue; }

                    if (File.Exists(destination) && SameBytes(saved, destination)) { unchanged++; continue; }

                    Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
                    File.Copy(saved, destination, overwrite: true);
                    restored++;
                }
                catch (Exception ex)
                {
                    _log.LogWarning(ex, "Could not restore image {Path}", image.Path);
                    failed++;
                }
            }

            return (restored, unchanged, failed);
        }

        private static bool SameBytes(string a, string b) =>
            new FileInfo(a).Length == new FileInfo(b).Length &&
            File.ReadAllBytes(a).AsSpan().SequenceEqual(File.ReadAllBytes(b));


        // Turns "/images/products/x.jpeg" into a full path under wwwroot.

        private string? ResolveUnderWebRoot(string webPath, bool mustExist)
        {
            try
            {
                var cut = webPath.IndexOfAny(new[] { '?', '#' });
                var path = cut >= 0 ? webPath[..cut] : webPath;

                var candidates = new List<string> { path };
                var unescaped = Uri.UnescapeDataString(path);
                if (unescaped != path) candidates.Add(unescaped);

                foreach (var candidate in candidates)
                {
                    var segments = candidate.Split('/', StringSplitOptions.RemoveEmptyEntries);
                    if (segments.Length == 0 || segments.Any(s => s == "." || s == "..")) continue;

                    var current = _webRoot;
                    var found = true;
                    for (var i = 0; i < segments.Length; i++)
                    {
                        var next = MatchEntry(current, segments[i], isFile: i == segments.Length - 1);
                        if (next != null) { current = next; continue; }

                        if (mustExist) { found = false; break; }
                        current = Path.Combine(new[] { current }.Concat(segments.Skip(i)).ToArray());
                        break;
                    }
                    if (!found) continue;

                    var full = Path.GetFullPath(current);
                    if (!full.StartsWith(_webRoot + Path.DirectorySeparatorChar, StringComparison.Ordinal)) continue;
                    if (full.StartsWith(_root + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase)) continue;
                    return full;
                }
            }
            catch (Exception ex)
            {
                _log.LogWarning(ex, "Could not resolve image path {Path}", webPath);
            }

            return null;
        }

        private static string? MatchEntry(string parent, string segment, bool isFile)
        {
            var exact = Path.Combine(parent, segment);
            if (isFile ? File.Exists(exact) : Directory.Exists(exact)) return exact;
            if (!Directory.Exists(parent)) return null;

            foreach (var entry in Directory.EnumerateFileSystemEntries(parent))
            {
                if (string.Equals(Path.GetFileName(entry), segment, StringComparison.OrdinalIgnoreCase) &&
                    (isFile ? File.Exists(entry) : Directory.Exists(entry)))
                {
                    return entry;
                }
            }

            return null;
        }
    }
}