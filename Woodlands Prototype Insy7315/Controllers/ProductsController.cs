using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Data;
using Woodlands_Prototype_Insy7315.Models;
using System.Text.Json;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class ProductsController : Controller
    {
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<ProductsController> _logger;
        private readonly JsonSerializerOptions _jsonOptions = new() { PropertyNameCaseInsensitive = true };

        public ProductsController(
            IHttpClientFactory httpClientFactory,
            ILogger<ProductsController> logger)
        {
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }

        public async Task<IActionResult> Index(string? category)
        {
            var products = new List<Product>();

            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");
                var response = await client.GetAsync("api/products");

                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    products = JsonSerializer.Deserialize<List<Product>>(json, _jsonOptions) ?? new List<Product>();
                }
                else
                {
                    _logger.LogError("Node API returned {StatusCode} for products", response.StatusCode);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading products from Node API");
                TempData["Error"] = "Unable to load products.";
            }

            // -- Everything below this line stays identical to your existing code --

            string? activeCategoryLabel = null;

            if (!string.IsNullOrWhiteSpace(category) &&
                WoodLinkData.SlugToCategory.TryGetValue(category, out var label))
            {
                activeCategoryLabel = label;
            }

            var filteredProducts = activeCategoryLabel == null
                ? products
                : products.Where(p => p.Category == activeCategoryLabel).ToList();

            var categories = WoodLinkData.Categories
                .Select(c => new ProductCategory
                {
                    Id = c.Id,
                    Label = c.Label,
                    Image = c.Image,
                    Slug = c.Slug,
                    Description = c.Description,
                    Count = products.Count(p => p.Category == c.Id)
                })
                .ToList();

            var subtitle = activeCategoryLabel == null
                ? "Browse our full range of custom-built units and board services."
                : categories.FirstOrDefault(c => c.Id == activeCategoryLabel)?.Description ?? "";

            var vm = new ProductsIndexViewModel
            {
                ActiveCategory = activeCategoryLabel,
                PageSubtitle = subtitle,
                Products = filteredProducts,
                Categories = categories,
                TotalProductCount = products.Count
            };

            return View(vm);
        }

        public async Task<IActionResult> Details(string id)
        {
            if (string.IsNullOrWhiteSpace(id))
                return RedirectToAction(nameof(Index));

            Product? product = null;
            var related = new List<Product>();

            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");

                // Get the product
                var response = await client.GetAsync($"api/products/{id}");
                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    product = JsonSerializer.Deserialize<Product>(json, _jsonOptions);
                }

                if (product == null)
                    return RedirectToAction(nameof(Index));

                // Get related products (same category)
                var allResponse = await client.GetAsync("api/products");
                if (allResponse.IsSuccessStatusCode)
                {
                    var allJson = await allResponse.Content.ReadAsStringAsync();
                    var allProducts = JsonSerializer.Deserialize<List<Product>>(allJson, _jsonOptions) ?? new List<Product>();

                    related = allProducts
                        .Where(p => p.Category == product.Category && p.Id != product.Id)
                        .Take(3)
                        .ToList();
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading product {ProductId} from Node API", id);
                return RedirectToAction(nameof(Index));
            }

            var vm = new ProductDetailViewModel
            {
                Product = product,
                CategorySlug = WoodLinkData.CategoryToSlug.GetValueOrDefault(product.Category, ""),
                Related = related
            };

            return View(vm);
        }
    }
}