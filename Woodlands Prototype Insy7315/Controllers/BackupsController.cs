using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;
using Woodlands_Prototype_Insy7315.Services;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    //Manual backups, restores and rollbacks. Administrators only.
    [Authorize(Roles = "Admin")]
    public class BackupsController : Controller
    {
        private readonly BackupService _backups;
        private readonly BackupStore _store;

        public BackupsController(BackupService backups, BackupStore store)
        {
            _backups = backups;
            _store = store;
        }

        private string? Who => User.FindFirst(ClaimTypes.Email)?.Value ?? User.Identity?.Name;

        [HttpGet]
        public IActionResult Index()
        {
            var backups = _store.List(BackupStore.Backup);

            return View(new BackupsIndexViewModel
            {
                Backups = backups,
                RestorePoints = _store.List(BackupStore.Restore),
                Rollbacks = _store.List(BackupStore.Rollback),
                LastBackupAt = backups.FirstOrDefault()?.CreatedAt,
                StoragePath = _store.StoragePath
            });
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Run(string? note)
        {
            var (item, error) = await _backups.BackupNowAsync(Who, note);

            if (item == null)
            {
                TempData["Error"] = error ?? "The backup failed.";
            }
            else
            {
                TempData["Success"] =
                    $"Backup saved as {item.FileName} ({item.TotalRows:N0} rows, {item.ImageCount} {(item.ImageCount == 1 ? "image" : "images")}).";
            }

            return RedirectToAction(nameof(Index));
        }

        // The json when there are no images, otherwise a zip of the snapshot folder (json + images).
        
        // Downloads always go through here, the Updates folder itself is blocked from static serving.
        [HttpGet]
        public IActionResult Download(string kind, string name)
        {
            var item = _store.Get(kind, name);
            if (item == null) return NotFound();

            if (item.ImageCount == 0)
            {
                return PhysicalFile(_store.JsonPath(kind, name), "application/json", item.FileName);
            }

            return File(_store.CreateZip(kind, name), "application/zip", name + ".zip");
        }

        [HttpGet]
        public async Task<IActionResult> Review(string kind, string name, string mode = "restore")
        {
            var (model, error) = await _backups.ReviewAsync(kind, name, mode);

            if (model == null)
            {
                TempData["Error"] = error ?? "That snapshot could not be opened.";
                return RedirectToAction(nameof(Index));
            }

            return View(model);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Apply(
            string kind, string name, string mode, string scope, string? table, string? confirmText)
        {
            var (message, error) = await _backups.ApplyAsync(kind, name, mode, scope, table, confirmText, Who);

            if (error != null)
            {
                TempData["Error"] = error;
                return RedirectToAction(nameof(Review), new { kind, name, mode });
            }

            TempData["Success"] = message;
            return RedirectToAction(nameof(Index), new { tab = mode == BackupStore.Rollback ? "rollbacks" : "restore-points" });
        }
    }
}