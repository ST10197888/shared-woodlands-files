
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;

namespace Woodlands_Prototype_Insy7315.ViewComponents
{
    public class NotificationUnreadCountViewComponent : ViewComponent
    {
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly ILogger<NotificationUnreadCountViewComponent> _logger;

        public NotificationUnreadCountViewComponent(
            IHttpClientFactory httpClientFactory,
            ILogger<NotificationUnreadCountViewComponent> logger)
        {
            _httpClientFactory = httpClientFactory;
            _logger = logger;
        }

        public async Task<IViewComponentResult> InvokeAsync()
        {
            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");

                using var response = await client.GetAsync(
                    "api/notifications/unread-count");

                if (!response.IsSuccessStatusCode)
                {
                    return View(0);
                }

                var json = await response.Content.ReadAsStringAsync();

                using var document = JsonDocument.Parse(json);

                if (document.RootElement.TryGetProperty(
                    "unread_count", out var countElement) &&
                    countElement.TryGetInt32(out var count))
                {
                    return View(Math.Max(0, count));
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(
                    ex,
                    "Could not retrieve the unread notification count.");
            }

            return View(0);
        }
    }
}
