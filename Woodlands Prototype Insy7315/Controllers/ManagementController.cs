using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Text;
using System.Text.Json;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    [Authorize(Roles = "Admin,Manager (Soweto),Manager (Roodepoort),Manager (Randfontein)")]
    public class ManagementController : Controller
    {
        private readonly IHttpClientFactory _http;
        private readonly ILogger<ManagementController> _logger;
        private readonly JsonSerializerOptions _json = new() { PropertyNameCaseInsensitive = true };

        public ManagementController(IHttpClientFactory http, ILogger<ManagementController> logger)
        {
            _http = http;
            _logger = logger;
        }

        // ====== PRODUCTS ======

        public async Task<IActionResult> Products(string? category)
        {
            var products = new List<Product>();
            var allCategories = new List<string>();

            try
            {
                var client = _http.CreateClient("NodeApi");
                var res = await client.GetAsync("api/products");
                if (res.IsSuccessStatusCode)
                {
                    var json = await res.Content.ReadAsStringAsync();
                    products = JsonSerializer.Deserialize<List<Product>>(json, _json) ?? new();

                    allCategories = products
                        .Select(p => p.Category)
                        .Where(c => !string.IsNullOrWhiteSpace(c))
                        .Distinct()
                        .OrderBy(c => c)
                        .ToList();
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading management products");
            }

            if (!string.IsNullOrWhiteSpace(category))
                products = products.Where(p => p.Category == category).ToList();

            ViewBag.ActiveCategory = category;
            ViewBag.Categories = allCategories;

            return View(products);
        }

        [HttpGet]
        public IActionResult CreateProduct() => View("ProductForm", new ProductFormViewModel());

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> CreateProduct(ProductFormViewModel model)
        {
            if (!ModelState.IsValid) return View("ProductForm", model);

            try
            {
                // Generate a unique slug id from the title
                var baseId = CreateSlug(model.Title);
                if (string.IsNullOrWhiteSpace(baseId))
                    baseId = "product";

                var id = baseId;
                var counter = 2;
                while (await ProductExists(id))
                {
                    id = $"{baseId}-{counter++}";
                }

                var payload = new
                {
                    id = id,                                        
                    category = model.Category,
                    title = model.Title,
                    tagline = model.Tagline ?? "",
                    description = model.Description ?? "",
                    image = model.Image ?? "",
                    gallery = JsonSerializer.Serialize(Lines(model.Gallery)),
                    features = JsonSerializer.Serialize(Lines(model.Features)),
                    finishes = JsonSerializer.Serialize(Lines(model.Finishes)),
                    lead_time = model.LeadTime ?? "",
                    tag = model.Tag,
                    price = model.Price,
                    is_from_price = model.IsFromPrice
                };

                var client = _http.CreateClient("NodeApi");
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
                var response = await client.PostAsync("api/products", content);
                var body = await response.Content.ReadAsStringAsync();

                _logger.LogInformation("CreateProduct response: {Status} | Body: {Body}",
                    (int)response.StatusCode, body);

                if (!response.IsSuccessStatusCode)
                {
                    _logger.LogError("Node API create product error: {Error}", body);
                    ModelState.AddModelError("", "Unable to create the product.");
                    return View("ProductForm", model);
                }

                TempData["Success"] = "Product created.";
                return RedirectToAction(nameof(Products));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error creating product");
                ModelState.AddModelError("", "Unable to create the product.");
                return View("ProductForm", model);
            }
        }

        [HttpGet]
        public async Task<IActionResult> EditProduct(string id)
        {
            if (string.IsNullOrWhiteSpace(id)) return NotFound();

            try
            {
                var client = _http.CreateClient("NodeApi");
                var res = await client.GetAsync($"api/products/{id}");
                if (!res.IsSuccessStatusCode) return NotFound();

                var json = await res.Content.ReadAsStringAsync();
                var product = JsonSerializer.Deserialize<Product>(json, _json);
                if (product == null) return NotFound();

                return View("ProductForm", ToForm(product));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading product {Id}", id);
                return NotFound();
            }
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> EditProduct(string id, ProductFormViewModel model)
        {
            if (!ModelState.IsValid)
            {
                model.Id = id;
                return View("ProductForm", model);
            }

            try
            {
                var payload = new
                {
                    category = model.Category,
                    title = model.Title,
                    tagline = model.Tagline ?? "",
                    description = model.Description ?? "",
                    image = model.Image ?? "",
                    lead_time = model.LeadTime ?? "",
                    tag = model.Tag,
                    price = model.Price,
                    is_from_price = model.IsFromPrice,
                    gallery = JsonSerializer.Serialize(Lines(model.Gallery)),
                    features = JsonSerializer.Serialize(Lines(model.Features)),
                    finishes = JsonSerializer.Serialize(Lines(model.Finishes))
                };

                var client = _http.CreateClient("NodeApi");
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
                var response = await client.PutAsync($"api/products/{id}", content);

                if (!response.IsSuccessStatusCode)
                {
                    var errJson = await response.Content.ReadAsStringAsync();
                    _logger.LogError("Node API update error: {Error}", errJson);
                    ModelState.AddModelError("", "Unable to update the product.");
                    model.Id = id;
                    return View("ProductForm", model);
                }

                TempData["Success"] = "Product updated.";
                return RedirectToAction(nameof(Products));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating product {Id}", id);
                ModelState.AddModelError("", "Unable to update the product.");
                model.Id = id;
                return View("ProductForm", model);
            }
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> RequestProductDeletion(string id, string? reason)
        {
            try
            {
                var client = _http.CreateClient("NodeApi");
                var payload = new { reason = (reason ?? "").Trim() };
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
                var response = await client.PostAsync($"api/products/{Uri.EscapeDataString(id)}/deletion-requests", content);

                if (response.IsSuccessStatusCode)
                {
                    TempData["Success"] = "Deletion request sent. An administrator must approve it before the product is removed.";
                    return RedirectToAction("Index", "ProductDeletionRequests");
                }

                TempData["Error"] = response.StatusCode == System.Net.HttpStatusCode.Forbidden
                    ? "You do not have permission to request deletion of this product."
                    : "Unable to send the deletion request.";
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error requesting deletion of product {Id}", id);
                TempData["Error"] = "Unable to send the deletion request.";
            }

            return RedirectToAction(nameof(Products));
        }

        // SERVICES

        public async Task<IActionResult> Services(string? filter = "all")
        {
            var services = new List<Service>();
            try
            {
                var client = _http.CreateClient("NodeApi");
                var res = await client.GetAsync("api/services");
                if (res.IsSuccessStatusCode)
                {
                    var json = await res.Content.ReadAsStringAsync();
                    services = JsonSerializer.Deserialize<List<Service>>(json, _json) ?? new();
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading services");
            }

            // Apply filter (view also shows counts for all three, so the full list is always passed)
            var filtered = filter switch
            {
                "inactive" => services.Where(s => !s.IsActive).ToList(),
                "all" => services,
                _ => services.Where(s => s.IsActive).ToList()
            };

            ViewData["Filter"] = filter ?? "active";
            return View(filtered);
        }


        [HttpGet]
        public IActionResult CreateService() => View("ServiceForm", new Service { IsActive = true });

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> CreateService(Service model)
        {
            if (!ModelState.IsValid) return View("ServiceForm", model);

            try
            {
                var payload = new
                {
                    name = model.Name,
                    description = model.Description ?? "",
                    image = model.Image ?? "",
                    is_active = model.IsActive
                };

                var client = _http.CreateClient("NodeApi");
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
                var response = await client.PostAsync("api/services", content);
                var body = await response.Content.ReadAsStringAsync();

                _logger.LogInformation("CreateService response: {Status} | Body: {Body}", (int)response.StatusCode, body);

                if (!response.IsSuccessStatusCode)
                {
                    _logger.LogError("Node API create service error: {Error}", body);
                    ModelState.AddModelError("", "Unable to create the service.");
                    return View("ServiceForm", model);
                }

                TempData["Success"] = "Service created.";
                return RedirectToAction(nameof(Services));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error creating service");
                ModelState.AddModelError("", "Unable to create the service.");
                return View("ServiceForm", model);
            }
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> EditService(int id, Service model)
        {
            if (!ModelState.IsValid) { model.Id = id; return View("ServiceForm", model); }

            try
            {
                var payload = new
                {
                    id = id,
                    name = model.Name,
                    description = model.Description ?? "",
                    image = model.Image ?? "",
                    is_active = model.IsActive
                };

                var client = _http.CreateClient("NodeApi");
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
                var response = await client.PutAsync($"api/services/{id}", content);

                if (!response.IsSuccessStatusCode)
                {
                    var errJson = await response.Content.ReadAsStringAsync();
                    _logger.LogError("Node API service update error: {Error}", errJson);
                    ModelState.AddModelError("", "Unable to update the service.");
                    model.Id = id;
                    return View("ServiceForm", model);
                }

                TempData["Success"] = "Service updated.";
                return RedirectToAction(nameof(Services));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating service {Id}", id);
                ModelState.AddModelError("", "Unable to update the service.");
                model.Id = id;
                return View("ServiceForm", model);
            }
        }

        [HttpGet]
        public async Task<IActionResult> EditService(int id)
        {
            try
            {
                var client = _http.CreateClient("NodeApi");
                var res = await client.GetAsync("api/services");
                if (!res.IsSuccessStatusCode) return NotFound();

                var json = await res.Content.ReadAsStringAsync();
                var services = JsonSerializer.Deserialize<List<Service>>(json, _json) ?? new();
                var service = services.FirstOrDefault(s => s.Id == id);
                if (service == null) return NotFound();

                return View("ServiceForm", service);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading service {Id}", id);
                return NotFound();
            }
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> DeleteService(int id)
        {
            try
            {
                var client = _http.CreateClient("NodeApi");
                var response = await client.DeleteAsync($"api/services/{id}");
                if (response.IsSuccessStatusCode)
                    TempData["Success"] = "Service deleted.";
                else
                    TempData["Error"] = "Unable to delete the service.";
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error deleting service {Id}", id);
                TempData["Error"] = "Unable to delete the service.";
            }

            return RedirectToAction(nameof(Services));
        }


        // in ManagementController.cs

        public async Task<IActionResult> ServiceRequests(string? status = "all")
        {
            var requests = new List<QuoteRequest>();
            try
            {
                var client = _http.CreateClient("NodeApi");
                var res = await client.GetAsync("api/quote-requests");
                if (res.IsSuccessStatusCode)
                {
                    var json = await res.Content.ReadAsStringAsync();
                    requests = JsonSerializer.Deserialize<List<QuoteRequest>>(json, _json) ?? new();
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading service requests");
            }

            // Managers only see their branch
            if (!User.IsInRole("Admin"))
            {
                var branch = User.FindFirst("Branch")?.Value;
                requests = requests
                    .Where(r => string.Equals(r.Branch, branch, StringComparison.OrdinalIgnoreCase))
                    .ToList();
            }

            requests = requests.OrderByDescending(r => r.CreatedAt).ToList();

            var filtered = status switch
            {
                "pending" => requests.Where(r => r.Status == "Pending").ToList(),
                "in-progress" => requests.Where(r => r.Status == "In Progress").ToList(),
                "completed" => requests.Where(r => r.Status == "Completed").ToList(),
                "cancelled" => requests.Where(r => r.Status == "Cancelled").ToList(),
                _ => requests
            };

            ViewData["Filter"] = status ?? "all";
            return View(filtered);
        }

        // ====== HELPERS ======

        private static ProductFormViewModel ToForm(Product product)
        {
            return new ProductFormViewModel
            {
                Id = product.Id,
                Category = product.Category,
                Title = product.Title,
                IsFromPrice = product.IsFromPrice,
                Tagline = product.Tagline,
                Description = product.Description,
                Image = product.Image,
                Gallery = string.Join(Environment.NewLine, product.Gallery),
                Features = string.Join(Environment.NewLine, product.Features),
                Finishes = string.Join(Environment.NewLine, product.Finishes),
                LeadTime = product.LeadTime,
                Tag = product.Tag,
                Price = product.Price
            };
        }
        private static string CreateSlug(string title)
        {
            var chars = (title ?? "")
                .ToLowerInvariant()
                .Select(c => char.IsLetterOrDigit(c) ? c : '-')
                .ToArray();

            return string.Join(
                "-",
                new string(chars)
                    .Split('-', StringSplitOptions.RemoveEmptyEntries));
        }

        private async Task<bool> ProductExists(string id)
        {
            try
            {
                var client = _http.CreateClient("NodeApi");
                var res = await client.GetAsync($"api/products/{id}");
                return res.IsSuccessStatusCode;
            }
            catch
            {
                return false;
            }
        }
        private static List<string> Lines(string value) =>
            (value ?? "").Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries).ToList();
    }
}