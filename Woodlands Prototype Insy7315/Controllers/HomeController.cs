using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Data;
using Woodlands_Prototype_Insy7315.Models;
using System.Text.Json;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class HomeController : Controller
    {
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<HomeController> _logger;
        private readonly JsonSerializerOptions _jsonOptions = new() { PropertyNameCaseInsensitive = true };

        public HomeController(
            IHttpClientFactory httpClientFactory,
            ILogger<HomeController> logger)
        {
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }

        public async Task<IActionResult> Index()
        {
            var vm = new HomeViewModel
            {
                HeroSlides = WoodLinkData.HeroSlides,
                Categories = new List<ProductCategory>(),
                FeaturedProducts = new List<Product>(),
                TestimonialsSnippet = new List<Testimonial>()
            };

            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");

                // Fetch products
                var products = new List<Product>();
                var productsResponse = await client.GetAsync("api/products");
                if (productsResponse.IsSuccessStatusCode)
                {
                    var json = await productsResponse.Content.ReadAsStringAsync();
                    products = JsonSerializer.Deserialize<List<Product>>(json, _jsonOptions) ?? new();
                }

                // Build categories with LIVE counts from the products we just loaded
                vm.Categories = WoodLinkData.Categories
                    .Select(c => new ProductCategory
                    {
                        Id = c.Id,
                        Label = c.Label,
                        Image = c.Image,
                        Slug = c.Slug,
                        Description = c.Description,
                        Count = products.Count(p => p.Category == c.Id)   // ← live count!
                    })
                    .ToList();

                // Featured products (Popular / New tags)
                vm.FeaturedProducts = products
                    .Where(p => p.Tag == "Popular" || p.Tag == "New")
                    .Take(4)
                    .ToList();

                // Fetch testimonials
                var testimonialsResponse = await client.GetAsync("api/testimonials");
                if (testimonialsResponse.IsSuccessStatusCode)
                {
                    var json = await testimonialsResponse.Content.ReadAsStringAsync();
                    var testimonials = JsonSerializer.Deserialize<List<Testimonial>>(json, _jsonOptions) ?? new();
                    vm.TestimonialsSnippet = testimonials.Take(3).ToList();
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading homepage data from Node API");
                TempData["Error"] = "Some homepage content could not be loaded.";
            }

            return View(vm);
        }

        public IActionResult About()
        {
            return View();
        }

        public IActionResult Privacy() => View();

        public IActionResult Terms() => View();

        public IActionResult Refunds() => View();

        public IActionResult Cookies() => View();
    }
}