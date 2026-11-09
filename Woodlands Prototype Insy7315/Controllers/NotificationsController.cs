
using System.Net;
using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    [Authorize]
    public class NotificationsController : Controller
    {
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<NotificationsController> _logger;

        private static readonly JsonSerializerOptions JsonOptions = new()
        {
            PropertyNameCaseInsensitive = true
        };

        public NotificationsController(
            IHttpClientFactory httpClientFactory,
            ILogger<NotificationsController> logger)
        {
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }

        public async Task<IActionResult> Index()
        {
            var model = new NotificationListViewModel();

            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");

                var notificationsTask = client.GetAsync("api/notifications");
                var unreadTask = client.GetAsync("api/notifications/unread-count");

                await Task.WhenAll(notificationsTask, unreadTask);

                var notificationsResponse = await notificationsTask;
                var unreadResponse = await unreadTask;

                if (!notificationsResponse.IsSuccessStatusCode ||
                    !unreadResponse.IsSuccessStatusCode)
                {
                    model.IsAvailable = false;
                    return View(model);
                }

                var notificationsJson =
                    await notificationsResponse.Content.ReadAsStringAsync();

                using (var document = JsonDocument.Parse(notificationsJson))
                {
                    var root = document.RootElement;

                    JsonElement notificationArray = root;

                    if (root.ValueKind == JsonValueKind.Object &&
                        root.TryGetProperty("notifications", out var notifications))
                    {
                        notificationArray = notifications;
                    }

                    if (notificationArray.ValueKind == JsonValueKind.Array)
                    {
                        model.Notifications =
                            JsonSerializer.Deserialize<List<NotificationViewModel>>(
                                notificationArray.GetRawText(),
                                JsonOptions) ?? new List<NotificationViewModel>();
                    }
                }

                var unreadJson =
                    await unreadResponse.Content.ReadAsStringAsync();

                using (var document = JsonDocument.Parse(unreadJson))
                {
                    if (document.RootElement.TryGetProperty(
                        "unread_count", out var unreadCount))
                    {
                        model.UnreadCount = unreadCount.GetInt32();
                    }
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Unable to load notifications.");
                model.IsAvailable = false;
            }

            return View(model);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> MarkRead(Guid id)
        {
            return await SendNotificationRequest(
                HttpMethod.Patch,
                $"api/notifications/{id}/read");
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> MarkAllRead()
        {
            return await SendNotificationRequest(
                HttpMethod.Patch,
                "api/notifications/read-all");
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Dismiss(Guid id)
        {
            return await SendNotificationRequest(
                HttpMethod.Delete,
                $"api/notifications/{id}");
        }

        private async Task<IActionResult> SendNotificationRequest(
            HttpMethod method,
            string endpoint)
        {
            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");

                using var request = new HttpRequestMessage(method, endpoint);
                using var response = await client.SendAsync(request);

                if (response.IsSuccessStatusCode)
                {
                    TempData["NotificationMessage"] =
                        "Your notifications have been updated.";
                }
                else if (response.StatusCode == HttpStatusCode.Unauthorized)
                {
                    TempData["NotificationError"] =
                        "Your session has expired. Please sign in again.";
                }
                else
                {
                    _logger.LogWarning(
                        "Notification API request failed. Endpoint: {Endpoint}, Status: {Status}",
                        endpoint,
                        response.StatusCode);

                    TempData["NotificationError"] =
                        "The notification could not be updated. Please try again.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(
                    ex,
                    "Error sending notification request to {Endpoint}.",
                    endpoint);

                TempData["NotificationError"] =
                    "The notification service is currently unavailable.";
            }

            return RedirectToAction(nameof(Index));
        }
    }
}
