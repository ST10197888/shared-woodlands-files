using Microsoft.AspNetCore.Mvc;
using System.Text.Json;

using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class TestimonialsController : Controller
    {
        //public IActionResult Index()
        //{
        //    return View(
        //        new List<Woodlands_Prototype_Insy7315.Models.Testimonial>());
        //}


        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<TestimonialsController> _logger;
        private readonly JsonSerializerOptions _jsonOptions = new() { PropertyNameCaseInsensitive = true };

        public TestimonialsController(
            IHttpClientFactory httpClientFactory,
            ILogger<TestimonialsController> logger)
        {
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }

        public async Task<IActionResult> Index()
        {
            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");
                var response = await client.GetAsync("api/testimonials");

                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    var testimonials = JsonSerializer.Deserialize<List<Testimonial>>(json, _jsonOptions)
                                       ?? new List<Testimonial>();
                    return View(testimonials);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading testimonials from Node API");
                TempData["Error"] = "Unable to load testimonials.";
            }

            return View(new List<Testimonial>());
        }

    }
}