using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Text.Json;
using Woodlands_Prototype_Insy7315.Data;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    [Authorize]
    public class DashboardController : Controller
    {
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<DashboardController> _logger;
        private readonly JsonSerializerOptions _jsonOptions = new() { PropertyNameCaseInsensitive = true };

        public DashboardController(IHttpClientFactory httpClientFactory, ILogger<DashboardController> logger)
        {
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }

        public async Task<IActionResult> Index()
        {
            var isAdmin = User.IsInRole("Admin");
            var isManager = User.IsInRole("Manager (Soweto)") ||
                            User.IsInRole("Manager (Roodepoort)") ||
                            User.IsInRole("Manager (Randfontein)");
            var displayName = User.FindFirst("FullName")?.Value ?? User.Identity?.Name ?? "User";
            var managerBranch = User.FindFirst("Branch")?.Value;

            var allQuotes = new List<QuoteRequest>();

            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");
                var response = await client.GetAsync("api/quote-requests");
                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    allQuotes = JsonSerializer.Deserialize<List<QuoteRequest>>(json, _jsonOptions) ?? new List<QuoteRequest>();
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching quotes from Node API");
            }

            var ordered = allQuotes.OrderByDescending(q => q.CreatedAt).ToList();

            var vm = new DashboardOverviewViewModel
            {
                IsAdmin = isAdmin,
                IsManager = isManager,
                ManagerBranch = managerBranch,
                DisplayName = displayName
            };

            if (isAdmin) BuildAdminDashboard(vm, ordered);
            else if (isManager) BuildManagerDashboard(vm, ordered, managerBranch);
            else BuildCustomerDashboard(vm, ordered, UserEmail());

            return View(vm);
        }

        private static void BuildAdminDashboard(DashboardOverviewViewModel m, List<QuoteRequest> quotes)
        {
            m.TotalQuotes = quotes.Count;
            m.TotalCustomers = quotes.Select(q => q.Email).Where(e => !string.IsNullOrWhiteSpace(e)).Distinct(StringComparer.OrdinalIgnoreCase).Count();
            m.CompletedJobs = quotes.Count(q => string.Equals(q.Status, "Completed", StringComparison.OrdinalIgnoreCase));
            m.CompletionRate = m.TotalQuotes == 0 ? 0 : Math.Round((double)m.CompletedJobs / m.TotalQuotes * 100, 1);

            var completed = quotes.Where(q => string.Equals(q.Status, "Completed", StringComparison.OrdinalIgnoreCase)).ToList();
            m.MonthlyRevenue = FormatCurrency(completed.Sum(GetQuoteValue));
            m.Branches = BuildBranchSummaries(quotes);
            m.RecentQuotes = quotes.Take(5).Select(MapQuote).ToList();
        }

        private static void BuildManagerDashboard(DashboardOverviewViewModel m, List<QuoteRequest> allQuotes, string? managerBranch)
        {
            var branchQuotes = allQuotes
                .Where(q => !string.IsNullOrWhiteSpace(managerBranch) && string.Equals(q.Branch, managerBranch, StringComparison.OrdinalIgnoreCase))
                .OrderByDescending(q => q.CreatedAt).ToList();

            m.PendingCount = branchQuotes.Count(q => string.Equals(q.Status, "Pending", StringComparison.OrdinalIgnoreCase));
            m.InProgressCount = branchQuotes.Count(q => string.Equals(q.Status, "In Progress", StringComparison.OrdinalIgnoreCase));
            m.CompletedCount = branchQuotes.Count(q => string.Equals(q.Status, "Completed", StringComparison.OrdinalIgnoreCase));

            var completedRevenue = branchQuotes.Where(q => string.Equals(q.Status, "Completed", StringComparison.OrdinalIgnoreCase)).Sum(GetQuoteValue);
            m.WeekRevenue = FormatCurrency(completedRevenue);
            m.WeeklyRevenueDays = BuildWeeklyRevenue(branchQuotes);
            m.QuotesNeedingAttention = branchQuotes
                .Where(q => string.Equals(q.Status, "Pending", StringComparison.OrdinalIgnoreCase) || string.Equals(q.Status, "In Progress", StringComparison.OrdinalIgnoreCase))
                .Take(10).Select(MapQuote).ToList();
        }

        private static void BuildCustomerDashboard(DashboardOverviewViewModel m, List<QuoteRequest> allQuotes, string? email)
        {
            var customerQuotes = allQuotes
                .Where(q => !string.IsNullOrWhiteSpace(email) && string.Equals(q.Email, email, StringComparison.OrdinalIgnoreCase))
                .OrderByDescending(q => q.CreatedAt).ToList();

            m.PendingCount = customerQuotes.Count(q => string.Equals(q.Status, "Pending", StringComparison.OrdinalIgnoreCase));
            m.InProgressCount = customerQuotes.Count(q => string.Equals(q.Status, "In Progress", StringComparison.OrdinalIgnoreCase));
            m.CompletedCount = customerQuotes.Count(q => string.Equals(q.Status, "Completed", StringComparison.OrdinalIgnoreCase));
            m.RecentQuotes = customerQuotes.Take(10).Select(MapQuote).ToList();
        }

        private static List<BranchSummary> BuildBranchSummaries(List<QuoteRequest> quotes)
        {
            var branches = new[] { "Soweto", "Roodepoort", "Randfontein" };
            return branches.Select(branch =>
            {
                var bq = quotes.Where(q => string.Equals(q.Branch, branch, StringComparison.OrdinalIgnoreCase)).ToList();
                var comp = bq.Where(q => string.Equals(q.Status, "Completed", StringComparison.OrdinalIgnoreCase)).ToList();
                var revenue = comp.Sum(GetQuoteValue);
                var donePercent = bq.Count == 0 ? 0 : (int)Math.Round((double)comp.Count / bq.Count * 100);
                return new BranchSummary
                {
                    Name = branch,
                    Quotes = bq.Count,
                    Revenue = FormatShortCurrency(revenue),
                    DonePercent = donePercent
                };
            }).ToList();
        }

        private static List<int> BuildWeeklyRevenue(List<QuoteRequest> quotes)
        {
            var today = DateTime.Today;
            var startOfWeek = today.AddDays(-(int)today.DayOfWeek + 1);
            var result = new List<int>();
            for (var i = 0; i < 6; i++)
            {
                var day = startOfWeek.AddDays(i);
                var revenue = quotes
                    .Where(q => q.CreatedAt.Date == day.Date && string.Equals(q.Status, "Completed", StringComparison.OrdinalIgnoreCase))
                    .Sum(GetQuoteValue);
                result.Add((int)Math.Round(revenue));
            }
            return result;
        }

        private static MockQuote MapQuote(QuoteRequest q) => new()
        {
            Id = string.IsNullOrWhiteSpace(q.QuoteCode) ? q.Id.ToString() : q.QuoteCode,
            Customer = $"{q.FirstName} {q.LastName}".Trim(),
            Phone = q.Phone ?? "",
            Service = q.Service ?? "",
            Branch = q.Branch ?? "",
            Date = q.CreatedAt.ToString("yyyy-MM-dd"),
            Value = string.IsNullOrWhiteSpace(q.Value) ? "" : q.Value,
            Status = string.IsNullOrWhiteSpace(q.Status) ? "Pending" : q.Status
        };

        private static double GetQuoteValue(QuoteRequest q)
        {
            if (string.IsNullOrWhiteSpace(q.Value)) return 0;
            var cleaned = q.Value.Replace("R", "").Replace(",", "").Replace(" ", "").Trim();
            return double.TryParse(cleaned, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out var v) ? v : 0;
        }

        private static string FormatCurrency(double v) => $"R{v:N0}";
        private static string FormatShortCurrency(double v) => v >= 1_000_000 ? $"R{v / 1_000_000:0.#}m" : v >= 1_000 ? $"R{v / 1_000:0.#}k" : $"R{v:N0}";

        private string? UserEmail() =>
            User.FindFirst(System.Security.Claims.ClaimTypes.Email)?.Value ?? User.Identity?.Name;

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> UpdateQuoteStatus(string id, string status)
        {
            if (!User.IsInRole("Admin") && !User.IsInRole("Manager (Soweto)") &&
                !User.IsInRole("Manager (Roodepoort)") && !User.IsInRole("Manager (Randfontein)"))
            {
                return Forbid();
            }

            if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(status))
                return BadRequest();

            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");

                // Find quote by quote_code or id
                var listRes = await client.GetAsync("api/quote-requests");
                if (!listRes.IsSuccessStatusCode) return NotFound();
                var json = await listRes.Content.ReadAsStringAsync();
                var all = JsonSerializer.Deserialize<List<QuoteRequest>>(json, _jsonOptions) ?? new();

                var quote = all.FirstOrDefault(q => q.QuoteCode == id);
                if (quote == null && Guid.TryParse(id, out _)) quote = all.FirstOrDefault(q => q.Id.ToString() == id);
                if (quote == null) return NotFound();

                if (!User.IsInRole("Admin"))
                {
                    var managerBranch = User.FindFirst("Branch")?.Value;
                    if (!string.Equals(quote.Branch, managerBranch, StringComparison.OrdinalIgnoreCase))
                        return Forbid();
                }

                var body = JsonSerializer.Serialize(new { status });
                var content = new StringContent(body, System.Text.Encoding.UTF8, "application/json");
                var updateResponse = await client.PutAsync($"api/quote-requests/{quote.Id}", content);

                if (updateResponse.IsSuccessStatusCode)
                {
                    TempData["Success"] = "Quote status updated.";
                }
                else
                {
                    TempData["Error"] = updateResponse.StatusCode == System.Net.HttpStatusCode.Forbidden
                        ? "You do not have permission to update this quote."
                        : "The quote status could not be updated.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating quote status");
                TempData["Error"] = "The quote status could not be updated. Please try again.";
            }

            return RedirectToAction(nameof(Index));
        }
    }
}