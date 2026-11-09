using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;
using Woodlands_Prototype_Insy7315.Services;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    [Authorize(Roles = "Admin")]
    public class DeletionRequestsController : Controller
    {
        private readonly IHttpClientFactory _http;
        private readonly ILogger<DeletionRequestsController> _logger;
        private readonly JsonSerializerOptions _json = new() { PropertyNameCaseInsensitive = true };

        public DeletionRequestsController(
            IHttpClientFactory http,
            ILogger<DeletionRequestsController> logger)
        {
            _http = http;
            _logger = logger;
        }

        [HttpGet]
        public async Task<IActionResult> Index(string? filter = "pending")
        {
            var showAll = string.Equals(filter, "all", StringComparison.OrdinalIgnoreCase);
            var requests = new List<DeletionRequest>();

            try
            {
                var client = _http.CreateClient("NodeApi");
                var response = await client.GetAsync(
                    showAll ? "api/deletion-requests" : "api/deletion-requests?status=Pending");

                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    requests = JsonSerializer.Deserialize<List<DeletionRequest>>(json, _json) ?? new();
                }
                else
                {
                    TempData["Error"] = "Unable to load deletion requests.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading deletion requests");
                TempData["Error"] = "Unable to load deletion requests.";
            }

            ViewData["Filter"] = showAll ? "all" : "pending";

            return View(requests);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Approve(string? id)
        {
            if (string.IsNullOrWhiteSpace(id))
            {
                return RedirectToAction(nameof(Index));
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                using var content = new StringContent(
                    JsonSerializer.Serialize(new { resolved_by = AdminName() }),
                    Encoding.UTF8,
                    "application/json");

                var response = await client.PostAsync(
                    $"api/deletion-requests/{Uri.EscapeDataString(id)}/approve",
                    content);

                if (response.IsSuccessStatusCode)
                {
                    var archived = false;

                    try
                    {
                        var body = await response.Content.ReadAsStringAsync();
                        using var doc = JsonDocument.Parse(body);
                        archived = doc.RootElement.TryGetProperty("archived", out var value)
                                   && value.ValueKind == JsonValueKind.True;
                    }
                    catch
                    {
                        archived = false;
                    }

                    TempData["Success"] = archived
                        ? "Account deleted. A recoverable copy of the data was saved."
                        : "Account deleted.";
                }
                else
                {
                    TempData["Error"] = await AccountApiHelper.ReadErrorAsync(
                        response,
                        "Unable to approve the request.");
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error approving deletion request {RequestId}", id);
                TempData["Error"] = "Unable to approve the request.";
            }

            return RedirectToAction(nameof(Index));
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Reject(string? id, string? note)
        {
            if (string.IsNullOrWhiteSpace(id))
            {
                return RedirectToAction(nameof(Index));
            }

            var cleanNote = InputSanitizer.StripHtml(note) ?? "";

            if (cleanNote.Length > 500)
            {
                cleanNote = cleanNote.Substring(0, 500);
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                using var content = new StringContent(
                    JsonSerializer.Serialize(new { admin_note = cleanNote, resolved_by = AdminName() }),
                    Encoding.UTF8,
                    "application/json");

                var response = await client.PutAsync(
                    $"api/deletion-requests/{Uri.EscapeDataString(id)}/reject",
                    content);

                if (response.IsSuccessStatusCode)
                {
                    TempData["Success"] = "Request rejected. The account was left unchanged.";
                }
                else
                {
                    TempData["Error"] = await AccountApiHelper.ReadErrorAsync(
                        response,
                        "Unable to reject the request.");
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error rejecting deletion request {RequestId}", id);
                TempData["Error"] = "Unable to reject the request.";
            }

            return RedirectToAction(nameof(Index));
        }

        private string AdminName() =>
            User.FindFirst("FullName")?.Value ?? User.Identity?.Name ?? "Admin";
    }
}
