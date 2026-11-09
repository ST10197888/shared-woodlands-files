using System.Security.Claims;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;
using Woodlands_Prototype_Insy7315.Services;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    [Authorize]
    public class MyAccountController : Controller
    {
        private const string CookieScheme = "WoodlandsCookie";

        private readonly IHttpClientFactory _http;
        private readonly SupabaseAuthService _authService;
        private readonly ILogger<MyAccountController> _logger;
        private readonly JsonSerializerOptions _json = new() { PropertyNameCaseInsensitive = true };

        public MyAccountController(
            IHttpClientFactory http,
            SupabaseAuthService authService,
            ILogger<MyAccountController> logger)
        {
            _http = http;
            _authService = authService;
            _logger = logger;
        }

        [HttpGet]
        public async Task<IActionResult> Profile()
        {
            var user = await LoadCurrentUser();

            if (user == null)
            {
                TempData["Error"] = "Unable to load your account details.";
                return RedirectToAction("Index", "Dashboard");
            }

            return View(ToViewModel(user));
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Profile(ProfileViewModel model)
        {
            var user = await LoadCurrentUser();

            if (user == null)
            {
                TempData["Error"] = "Unable to load your account details.";
                return RedirectToAction("Index", "Dashboard");
            }

            model.Email = user.Email;
            model.Role = user.Role;
            model.Branch = user.Branch;
            model.FullName = InputSanitizer.StripHtml(model.FullName) ?? "";

            if (string.IsNullOrWhiteSpace(model.FullName))
            {
                ModelState.AddModelError(nameof(model.FullName), "Full name is required.");
            }

            if (!ModelState.IsValid)
            {
                return View(model);
            }

            try
            {
                var payload = new
                {
                    full_name = model.FullName,
                    phone = model.PhoneNumber?.Trim() ?? ""
                };

                var client = _http.CreateClient("NodeApi");
                using var content = new StringContent(
                    JsonSerializer.Serialize(payload),
                    Encoding.UTF8,
                    "application/json");

                var response = await client.PutAsync($"api/app-users/{user.Id}", content);

                if (!response.IsSuccessStatusCode)
                {
                    ModelState.AddModelError("", "Unable to save your details. Please try again.");
                    return View(model);
                }

                await RefreshNameClaim(model.FullName);

                TempData["Success"] = "Profile updated.";
                return RedirectToAction(nameof(Profile));
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating profile for {Id}", user.Id);
                ModelState.AddModelError("", "Unable to save your details. Please try again.");
                return View(model);
            }
        }

        [HttpGet]
        public IActionResult Settings()
        {
            return View();
        }

        private async Task<AppUser?> LoadCurrentUser()
        {
            var id = User.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            var email = User.FindFirst(ClaimTypes.Email)?.Value;

            if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(email))
            {
                return null;
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                var response = await client.GetAsync(
                    $"api/app-users?email={Uri.EscapeDataString(email)}");

                if (!response.IsSuccessStatusCode)
                {
                    return null;
                }

                var json = await response.Content.ReadAsStringAsync();
                var users = JsonSerializer.Deserialize<List<AppUser>>(json, _json) ?? new();

                return users.FirstOrDefault(u => u.Id == id);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error loading account {Id}", id);
                return null;
            }
        }

        private static ProfileViewModel ToViewModel(AppUser user) => new()
        {
            FullName = user.FullName,
            PhoneNumber = user.Phone,
            Email = user.Email,
            Role = user.Role,
            Branch = user.Branch
        };

        private async Task RefreshNameClaim(string fullName)
        {
            var auth = await HttpContext.AuthenticateAsync(CookieScheme);

            if (!auth.Succeeded || auth.Principal == null)
            {
                return;
            }

            var claims = auth.Principal.Claims.Where(c => c.Type != "FullName").ToList();
            claims.Add(new Claim("FullName", fullName));

            var principal = new ClaimsPrincipal(new ClaimsIdentity(claims, CookieScheme));

            await HttpContext.SignInAsync(CookieScheme, principal, auth.Properties);
        }
    }
}