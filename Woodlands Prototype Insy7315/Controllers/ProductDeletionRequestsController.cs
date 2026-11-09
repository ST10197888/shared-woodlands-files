using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    [Authorize(Roles = "Admin,Manager (Soweto),Manager (Roodepoort),Manager (Randfontein)")]
    public class ProductDeletionRequestsController : Controller
    {
        private readonly IHttpClientFactory _http;
        private readonly ILogger<ProductDeletionRequestsController> _logger;

        public ProductDeletionRequestsController(
            IHttpClientFactory http,
            ILogger<ProductDeletionRequestsController> logger)
        {
            _http = http;
            _logger = logger;
        }

        [HttpGet]
        public async Task<IActionResult> Index(string? filter = "pending")
        {
            var showAll = string.Equals(filter, "all", StringComparison.OrdinalIgnoreCase);
            var requests = new List<ProductDeletionRequestViewModel>();

            try
            {
                var client = _http.CreateClient("NodeApi");
                var response = await client.GetAsync("api/product-deletion-requests");

                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    requests = Parse(json);
                }
                else
                {
                    TempData["Error"] = response.StatusCode == System.Net.HttpStatusCode.Forbidden
                        ? "You do not have permission to view product deletion requests."
                        : "Unable to load product deletion requests.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading product deletion requests");
                TempData["Error"] = "Unable to load product deletion requests.";
            }

            if (!showAll)
            {
                requests = requests.Where(r => r.IsPending).ToList();
            }

            ViewData["Filter"] = showAll ? "all" : "pending";

            return View(requests);
        }

        [Authorize(Roles = "Admin")]
        [HttpPost]
        [ValidateAntiForgeryToken]
        public Task<IActionResult> Approve(string? id, string? note) => Decide(id, note, "approve");

        [Authorize(Roles = "Admin")]
        [HttpPost]
        [ValidateAntiForgeryToken]
        public Task<IActionResult> Reject(string? id, string? note) => Decide(id, note, "reject");

        private async Task<IActionResult> Decide(string? id, string? note, string action)
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
                    resolved_by = User.FindFirst("FullName")?.Value ?? User.Identity?.Name ?? "",
                    admin_note = (note ?? "").Trim()
                };

                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");

                var response = await client.PostAsync(
                    $"api/product-deletion-requests/{Uri.EscapeDataString(id)}/{action}",
                    content);

                if (response.IsSuccessStatusCode)
                {
                    TempData["Success"] = action == "approve"
                        ? "Request approved and the product was deleted."
                        : "Request rejected. The product was kept.";
                }
                else
                {
                    TempData["Error"] = "The request could not be processed.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error processing product deletion request {Id}", id);
                TempData["Error"] = "The request could not be processed.";
            }

            return RedirectToAction(nameof(Index));
        }

        private static List<ProductDeletionRequestViewModel> Parse(string json)
        {
            var list = new List<ProductDeletionRequestViewModel>();

            try
            {
                using var doc = JsonDocument.Parse(json);

                if (doc.RootElement.ValueKind != JsonValueKind.Array) return list;

                foreach (var item in doc.RootElement.EnumerateArray())
                {
                    var title = Text(item, "product_title", "product_name", "title");

                    if (title.Length == 0)
                    {
                        foreach (var key in new[] { "products", "product" })
                        {
                            if (item.TryGetProperty(key, out var nested) && nested.ValueKind == JsonValueKind.Object)
                            {
                                title = Text(nested, "title", "name");
                                if (title.Length > 0) break;
                            }
                        }
                    }

                    DateTime? created = null;
                    if (DateTime.TryParse(Text(item, "created_at"), out var parsed)) created = parsed;

                    var status = Text(item, "status");

                    list.Add(new ProductDeletionRequestViewModel
                    {
                        Id = Text(item, "id"),
                        ProductTitle = title.Length > 0 ? title : "Product " + Text(item, "product_id"),
                        RequestedBy = Text(item, "requested_by_name", "requested_by"),
                        Reason = Text(item, "reason"),
                        AdminNote = Text(item, "admin_note"),
                        Status = status.Length > 0 ? status : "Pending",
                        CreatedAt = created
                    });
                }
            }
            catch (JsonException)
            {
            }

            return list.OrderByDescending(r => r.CreatedAt).ToList();
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
