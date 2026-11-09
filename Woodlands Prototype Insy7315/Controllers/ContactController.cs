using Microsoft.AspNetCore.Mvc;
using System.Text;
using System.Text.Json;
using Woodlands_Prototype_Insy7315.Models;
using Woodlands_Prototype_Insy7315.Services;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class ContactController : Controller
    {
        private readonly IHttpClientFactory _http;
        private readonly ILogger<ContactController> _logger;
        private readonly JsonSerializerOptions _json = new() { PropertyNameCaseInsensitive = true };

        public ContactController(IHttpClientFactory http, ILogger<ContactController> logger)
        {
            _http = http;
            _logger = logger;
        }

        [HttpGet]
        public async Task<IActionResult> Index(string? service, string? product, string? productId)
        {
            var services = new List<Service>();
            Product? requestedProduct = null;

            try
            {
                var client = _http.CreateClient("NodeApi");

                var servicesRes = await client.GetAsync("api/services");
                if (servicesRes.IsSuccessStatusCode)
                {
                    var json = await servicesRes.Content.ReadAsStringAsync();
                    services = JsonSerializer.Deserialize<List<Service>>(json, _json) ?? new();
                    services = services.Where(s => s.IsActive).ToList();
                }

                if (!string.IsNullOrWhiteSpace(productId))
                {
                    var prodRes = await client.GetAsync($"api/products/{productId}");
                    if (prodRes.IsSuccessStatusCode)
                    {
                        var json = await prodRes.Content.ReadAsStringAsync();
                        requestedProduct = JsonSerializer.Deserialize<Product>(json, _json);
                    }
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading contact page data");
            }

            ViewBag.Services = services;
            ViewBag.RequestedProduct = requestedProduct;

            var resolvedService = requestedProduct?.Category ?? service;
            var resolvedProductTitle = requestedProduct?.Title ?? product;

            var model = new ContactRequest
            {
                Service = services.Any(s => s.Name == resolvedService) ? resolvedService! : "",
                Message = !string.IsNullOrWhiteSpace(resolvedProductTitle)
                    ? $"I'm interested in the \"{resolvedProductTitle}\". "
                    : "",
                ProductId = requestedProduct?.Id
            };

            return View(model);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Index(ContactRequest request)
        {
            if (User.IsInRole("Admin") ||
                User.IsInRole("Manager (Soweto)") ||
                User.IsInRole("Manager (Roodepoort)") ||
                User.IsInRole("Manager (Randfontein)"))
            {
                return Forbid();
            }

            if (!request.PrivacyAcknowledged)
            {
                ModelState.AddModelError(
                    nameof(ContactRequest.PrivacyAcknowledged),
                    "Please read the privacy notice before submitting this form.");

                return View(request);
            }

            var services = new List<Service>();
            try
            {
                var client = _http.CreateClient("NodeApi");
                var servicesRes = await client.GetAsync("api/services");
                if (servicesRes.IsSuccessStatusCode)
                {
                    var json = await servicesRes.Content.ReadAsStringAsync();
                    services = JsonSerializer.Deserialize<List<Service>>(json, _json) ?? new();
                    services = services.Where(s => s.IsActive).ToList();
                }
                ViewBag.Services = services;

                if (!string.IsNullOrWhiteSpace(request.ProductId))
                {
                    var prodRes = await client.GetAsync($"api/products/{request.ProductId}");
                    if (prodRes.IsSuccessStatusCode)
                    {
                        var json = await prodRes.Content.ReadAsStringAsync();
                        ViewBag.RequestedProduct = JsonSerializer.Deserialize<Product>(json, _json);
                    }
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error reloading contact page data");
            }

            if (!ModelState.IsValid)
            {
                return View(request);
            }
            if (!request.PrivacyAcknowledged)
            {
                ModelState.AddModelError(nameof(ContactRequest.PrivacyAcknowledged), "Please read the privacy notice before submitting this form.");
                return View(request);
            }
            

            try
            {
                var quoteCode = $"WL-{DateTime.UtcNow:yyyyMMddHHmmss}-{Guid.NewGuid().ToString()[..8].ToUpperInvariant()}";

                var payload = new
                {
                    quote_code = quoteCode,
                    first_name = InputSanitizer.StripHtml(request.FirstName),
                    last_name = InputSanitizer.StripHtml(request.LastName),
                    email = request.Email,
                    phone = request.Phone,
                    branch = request.Branch,
                    service = request.Service,
                    message = InputSanitizer.StripHtml(request.Message),
                    product_id = request.ProductId ?? "",
                    status = "pending"
                };

                var client = _http.CreateClient("NodeApi");
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");
                var response = await client.PostAsync("api/quote-requests", content);

                if (!response.IsSuccessStatusCode)
                {
                    var errJson = await response.Content.ReadAsStringAsync();
                    _logger.LogError("Node API quote error: {Error}", errJson);
                    ModelState.AddModelError("", "There was a problem submitting your quote request. Please try again.");
                    return View(request);
                }

                TempData["QuoteCode"] = quoteCode;
                return RedirectToAction(nameof(Confirmation));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error creating quote request");
                ModelState.AddModelError("", "There was a problem submitting your quote request. Please try again.");
                return View(request);
            }
        }

        public IActionResult Confirmation()
        {
            return View();
        }
    }
}