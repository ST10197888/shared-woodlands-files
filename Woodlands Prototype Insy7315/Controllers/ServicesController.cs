using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;
using System.Text.Json;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class ServicesController : Controller
    {
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<ServicesController> _logger;
        private readonly JsonSerializerOptions _jsonOptions = new() { PropertyNameCaseInsensitive = true };

        public ServicesController(
            IHttpClientFactory httpClientFactory,
            ILogger<ServicesController> logger)
        {
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }

        public async Task<IActionResult> Index()
        {
            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");
                var response = await client.GetAsync("api/services");

                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    var services = JsonSerializer.Deserialize<List<Service>>(json, _jsonOptions)
                                   ?? new List<Service>();

                    // Filter out inactive services (public page)
                    services = services.Where(s => s.IsActive).ToList();

                    return View(services);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading services from Node API");
                TempData["Error"] = "Unable to load services.";
            }

            return View(new List<Service>());
        }

        public async Task<IActionResult> Details(int id)
        {
            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");
                var response = await client.GetAsync("api/services");

                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    var services = JsonSerializer.Deserialize<List<Service>>(json, _jsonOptions)
                                   ?? new List<Service>();

                    var service = services.FirstOrDefault(s => s.Id == id);
                    if (service == null) return NotFound();

                    return View(service);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading service {ServiceId}", id);
            }

            return NotFound();
        }
    }
}