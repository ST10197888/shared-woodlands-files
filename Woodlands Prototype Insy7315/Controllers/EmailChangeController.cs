using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    [Authorize]
    public class EmailChangeController : Controller
    {
        private const string DecisionAccept = "accept";
        private const string DecisionReject = "reject";

        private readonly IHttpClientFactory _http;
        private readonly ILogger<EmailChangeController> _logger;

        public EmailChangeController(IHttpClientFactory http, ILogger<EmailChangeController> logger)
        {
            _http = http;
            _logger = logger;
        }

        [HttpGet]
        public async Task<IActionResult> Index()
        {
            EmailChangeRequestViewModel? request = null;

            try
            {
                var client = _http.CreateClient("NodeApi");
                var response = await client.GetAsync("api/email-change-requests/me");

                if (response.IsSuccessStatusCode)
                {
                    request = Parse(await response.Content.ReadAsStringAsync());
                }
                else if (response.StatusCode != System.Net.HttpStatusCode.NotFound)
                {
                    TempData["Error"] = "Unable to load your email change request.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading email change request");
                TempData["Error"] = "Unable to load your email change request.";
            }

            if (request != null && string.IsNullOrWhiteSpace(request.CurrentEmail))
            {
                request.CurrentEmail = User.FindFirst(System.Security.Claims.ClaimTypes.Email)?.Value ?? "";
            }

            return View(request);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Decide(string? id, bool accept)
        {
            if (string.IsNullOrWhiteSpace(id))
            {
                TempData["Error"] = "The request could not be found.";
                return RedirectToAction(nameof(Index));
            }

            try
            {
                var client = _http.CreateClient("NodeApi");

                var payload = new
                {
                    decision = accept ? DecisionAccept : DecisionReject,
                    accepted = accept
                };

                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");

                var response = await client.PostAsync(
                    $"api/email-change-requests/{Uri.EscapeDataString(id)}/decision",
                    content);

                if (!response.IsSuccessStatusCode)
                {
                    TempData["Error"] = "Your decision could not be saved.";
                    return RedirectToAction(nameof(Index));
                }

                if (accept)
                {
                    await HttpContext.SignOutAsync("WoodlandsCookie");
                    TempData["RegistrationMessage"] = "Your email address was updated. Please sign in with the new address.";
                    return RedirectToAction("Login", "Account");
                }

                TempData["Success"] = "Request rejected. Your email address was not changed.";
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error saving email change decision {Id}", id);
                TempData["Error"] = "Your decision could not be saved.";
            }

            return RedirectToAction(nameof(Index));
        }

        private static EmailChangeRequestViewModel? Parse(string json)
        {
            var trimmed = json.Trim();

            if (trimmed.Length == 0 || trimmed == "null") return null;

            try
            {
                using var doc = JsonDocument.Parse(trimmed);
                var root = doc.RootElement;
                JsonElement? item = null;

                if (root.ValueKind == JsonValueKind.Array)
                {
                    var items = root.EnumerateArray().ToList();
                    var pending = items.FirstOrDefault(i =>
                        string.Equals(Text(i, "status"), "pending", StringComparison.OrdinalIgnoreCase));

                    item = pending.ValueKind == JsonValueKind.Object
                        ? pending
                        : items.Count > 0 ? items[0] : (JsonElement?)null;
                }
                else if (root.ValueKind == JsonValueKind.Object)
                {
                    if (root.TryGetProperty("request", out var nested) && nested.ValueKind == JsonValueKind.Object)
                        item = nested;
                    else if (root.TryGetProperty("data", out var data) && data.ValueKind == JsonValueKind.Object)
                        item = data;
                    else if (root.TryGetProperty("id", out _))
                        item = root;
                }

                if (item == null) return null;

                var element = item.Value;

                DateTime? created = null;
                if (DateTime.TryParse(Text(element, "created_at"), out var parsed)) created = parsed;

                var status = Text(element, "status");

                return new EmailChangeRequestViewModel
                {
                    Id = Text(element, "id"),
                    CurrentEmail = Text(element, "old_email", "current_email"),
                    NewEmail = Text(element, "new_email", "requested_email", "email"),
                    Status = status.Length > 0 ? status : "Pending",
                    CreatedAt = created
                };
            }
            catch (JsonException)
            {
                return null;
            }
        }

        private static string Text(JsonElement element, params string[] names)
        {
            foreach (var name in names)
            {
                if (element.TryGetProperty(name, out var value) &&
                    value.ValueKind != JsonValueKind.Null &&
                    value.ValueKind != JsonValueKind.Object &&
                    value.ValueKind != JsonValueKind.Array)
                {
                    var text = value.ToString();
                    if (!string.IsNullOrWhiteSpace(text)) return text;
                }
            }

            return "";
        }
    }
}
