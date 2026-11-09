using System.Security.Claims;
using System.Text.Json;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Services
{
    public static class AccountApiHelper
    {
        private static readonly JsonSerializerOptions Json = new() { PropertyNameCaseInsensitive = true };

        public static async Task<AppUser?> LoadUserAsync(IHttpClientFactory http, ClaimsPrincipal principal)
        {
            var id = principal.FindFirst(ClaimTypes.NameIdentifier)?.Value;
            var email = principal.FindFirst(ClaimTypes.Email)?.Value;

            if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(email))
            {
                return null;
            }

            try
            {
                var client = http.CreateClient("NodeApi");
                var response = await client.GetAsync($"api/app-users?email={Uri.EscapeDataString(email)}");

                if (!response.IsSuccessStatusCode)
                {
                    return null;
                }

                var json = await response.Content.ReadAsStringAsync();
                var users = JsonSerializer.Deserialize<List<AppUser>>(json, Json) ?? new();

                return users.FirstOrDefault(u => u.Id == id);
            }
            catch
            {
                return null;
            }
        }

        public static async Task<DeletionRequest?> LoadPendingRequestAsync(IHttpClientFactory http, string userId)
        {
            try
            {
                var client = http.CreateClient("NodeApi");
                var response = await client.GetAsync(
                    $"api/deletion-requests?status=Pending&user_id={Uri.EscapeDataString(userId)}");

                if (!response.IsSuccessStatusCode)
                {
                    return null;
                }

                var json = await response.Content.ReadAsStringAsync();
                var requests = JsonSerializer.Deserialize<List<DeletionRequest>>(json, Json) ?? new();

                return requests.FirstOrDefault(r => r.UserId == userId
                    && string.Equals(r.Status, "Pending", StringComparison.OrdinalIgnoreCase));
            }
            catch
            {
                return null;
            }
        }

        public static async Task<bool> ArchiveAvailableAsync(IHttpClientFactory http, string email)
        {
            try
            {
                var client = http.CreateClient("NodeApi");
                var response = await client.GetAsync(
                    $"api/account-archive/available?email={Uri.EscapeDataString(email)}");

                if (!response.IsSuccessStatusCode)
                {
                    return false;
                }

                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);

                return doc.RootElement.TryGetProperty("available", out var value)
                       && value.ValueKind == JsonValueKind.True;
            }
            catch
            {
                return false;
            }
        }

        public static async Task<string> ReadErrorAsync(HttpResponseMessage response, string fallback)
        {
            try
            {
                var json = await response.Content.ReadAsStringAsync();
                using var doc = JsonDocument.Parse(json);

                if (doc.RootElement.TryGetProperty("error", out var error)
                    && error.ValueKind == JsonValueKind.String)
                {
                    var message = error.GetString();

                    if (!string.IsNullOrWhiteSpace(message))
                    {
                        return message;
                    }
                }
            }
            catch
            {
                return fallback;
            }

            return fallback;
        }
    }
}