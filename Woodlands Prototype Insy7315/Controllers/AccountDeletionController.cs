using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Services;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    [Authorize]
    public class AccountDeletionController : Controller
    {
        private readonly IHttpClientFactory _http;
        private readonly SupabaseAuthService _authService;
        private readonly ILogger<AccountDeletionController> _logger;

        public AccountDeletionController(
            IHttpClientFactory http,
            SupabaseAuthService authService,
            ILogger<AccountDeletionController> logger)
        {
            _http = http;
            _authService = authService;
            _logger = logger;
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> SubmitRequest(string? password)
        {
            if (User.IsInRole("Admin"))
            {
                TempData["Error"] = "Administrator accounts cannot be deleted from here. Ask another administrator to remove it under Users.";
                return ToProfile();
            }

            var user = await AccountApiHelper.LoadUserAsync(_http, User);

            if (user == null)
            {
                TempData["Error"] = "Unable to load your account details.";
                return ToProfile();
            }

            if (string.IsNullOrWhiteSpace(password))
            {
                TempData["Error"] = "Enter your password to send the request.";
                return ToProfile();
            }

            var check = await _authService.LoginAsync(user.Email, password);

            if (!check.Success || check.User == null || check.User.Id != user.Id)
            {
                TempData["Error"] = "The password you entered is incorrect.";
                return ToProfile();
            }

            try
            {
                var payload = new
                {
                    user_id = user.Id,
                    email = user.Email,
                    full_name = user.FullName,
                    role = user.Role,
                    keep_data = user.KeepDataOnDelete
                };

                var client = _http.CreateClient("NodeApi");
                using var content = new StringContent(
                    JsonSerializer.Serialize(payload),
                    Encoding.UTF8,
                    "application/json");

                var response = await client.PostAsync("api/deletion-requests", content);

                if (!response.IsSuccessStatusCode)
                {
                    TempData["Error"] = await AccountApiHelper.ReadErrorAsync(
                        response,
                        "Unable to send your request. Please try again.");
                    return ToProfile();
                }

                TempData["Success"] = "Your deletion request was sent to an administrator. Your account stays active until it is approved.";
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error creating deletion request for {Id}", user.Id);
                TempData["Error"] = "Unable to send your request. Please try again.";
            }

            return ToProfile();
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> CancelRequest(string? id)
        {
            var user = await AccountApiHelper.LoadUserAsync(_http, User);

            if (user == null || string.IsNullOrWhiteSpace(id))
            {
                TempData["Error"] = "Unable to cancel the request.";
                return ToProfile();
            }

            var pending = await AccountApiHelper.LoadPendingRequestAsync(_http, user.Id);

            if (pending == null || pending.Id != id)
            {
                TempData["Error"] = "No pending request was found.";
                return ToProfile();
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                using var content = new StringContent("{}", Encoding.UTF8, "application/json");
                var response = await client.PutAsync(
                    $"api/deletion-requests/{Uri.EscapeDataString(id)}/cancel",
                    content);

                if (response.IsSuccessStatusCode)
                {
                    TempData["Success"] = "Your deletion request was cancelled.";
                }
                else
                {
                    TempData["Error"] = await AccountApiHelper.ReadErrorAsync(
                        response,
                        "Unable to cancel the request.");
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error cancelling deletion request {RequestId}", id);
                TempData["Error"] = "Unable to cancel the request.";
            }

            return ToProfile();
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> SetRecovery(bool enabled)
        {
            var user = await AccountApiHelper.LoadUserAsync(_http, User);

            if (user == null)
            {
                TempData["Error"] = "Unable to load your account details.";
                return ToSettings();
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                using var content = new StringContent(
                    JsonSerializer.Serialize(new { keep_data_on_delete = enabled }),
                    Encoding.UTF8,
                    "application/json");

                var response = await client.PutAsync($"api/app-users/{user.Id}", content);

                if (response.IsSuccessStatusCode)
                {
                    TempData["Success"] = enabled
                        ? "Recovery is on. A copy of your data will be kept if your account is deleted."
                        : "Recovery is off. Your data will not be kept if your account is deleted.";
                }
                else
                {
                    TempData["Error"] = "Unable to update your recovery setting.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating recovery setting for {Id}", user.Id);
                TempData["Error"] = "Unable to update your recovery setting.";
            }

            return ToSettings();
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> SendRestoreCode()
        {
            var user = await AccountApiHelper.LoadUserAsync(_http, User);

            if (user == null)
            {
                TempData["Error"] = "Unable to load your account details.";
                return ToSettings();
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                using var content = new StringContent(
                    JsonSerializer.Serialize(new { email = user.Email }),
                    Encoding.UTF8,
                    "application/json");

                var response = await client.PostAsync("api/account-archive/request-code", content);

                if (response.IsSuccessStatusCode)
                {
                    TempData["Success"] = "We emailed a 6-digit code to your address. It expires in 15 minutes.";
                }
                else
                {
                    TempData["Error"] = await AccountApiHelper.ReadErrorAsync(
                        response,
                        "Unable to send a code right now.");
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error requesting restore code for {Id}", user.Id);
                TempData["Error"] = "Unable to send a code right now.";
            }

            return ToSettings();
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> RestoreData(string? code)
        {
            var user = await AccountApiHelper.LoadUserAsync(_http, User);

            if (user == null)
            {
                TempData["Error"] = "Unable to load your account details.";
                return ToSettings();
            }

            if (string.IsNullOrWhiteSpace(code))
            {
                TempData["Error"] = "Enter the 6-digit code from your email.";
                return ToSettings();
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                using var content = new StringContent(
                    JsonSerializer.Serialize(new { email = user.Email, user_id = user.Id, code = code.Trim() }),
                    Encoding.UTF8,
                    "application/json");

                var response = await client.PostAsync("api/account-archive/restore", content);

                if (response.IsSuccessStatusCode)
                {
                    TempData["Success"] = "Your saved data was restored to your account.";
                }
                else
                {
                    TempData["Error"] = await AccountApiHelper.ReadErrorAsync(
                        response,
                        "Unable to restore your data.");
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error restoring data for {Id}", user.Id);
                TempData["Error"] = "Unable to restore your data.";
            }

            return ToSettings();
        }

        private IActionResult ToProfile() => RedirectToAction("Profile", "MyAccount");

        private IActionResult ToSettings() => RedirectToAction("Settings", "MyAccount");
    }
}
