using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;
using System.Text.Json;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class FAQsController : Controller
    {
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<FAQsController> _logger;
        private readonly JsonSerializerOptions _jsonOptions = new() { PropertyNameCaseInsensitive = true };

        public FAQsController(
            IHttpClientFactory httpClientFactory,
            ILogger<FAQsController> logger)
        {
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }

        public async Task<IActionResult> Index()
        {
            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");
                var response = await client.GetAsync("api/faqs");

                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    var faqs = JsonSerializer.Deserialize<List<FaqItem>>(json, _jsonOptions)
                               ?? new List<FaqItem>();
                    return View(faqs);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading FAQs from Node API");
                TempData["Error"] = "Unable to load FAQs.";
            }

            return View(new List<FaqItem>());
        }
    }
}