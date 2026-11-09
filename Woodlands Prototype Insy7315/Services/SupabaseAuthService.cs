using System.Text;
using System.Text.Json;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Services
{
    public class SupabaseAuthService
    {
        private readonly IHttpClientFactory _httpClientFactory;
        private readonly JsonSerializerOptions _jsonOptions = new() { PropertyNameCaseInsensitive = true };

        public SupabaseAuthService(IHttpClientFactory httpClientFactory)
        {
            _httpClientFactory = httpClientFactory;
        }

        public async Task<(bool Success, string? Error, AppUser? User, string? Token)> LoginAsync(string email, string password)
        {
            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");
                var payload = new { email, password };
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");

                var response = await client.PostAsync("api/auth/login", content);
                var json = await response.Content.ReadAsStringAsync();

                if (!response.IsSuccessStatusCode)
                {
                    var errObj = JsonSerializer.Deserialize<ErrorResponse>(json, _jsonOptions);
                    return (false, errObj?.Error ?? "Invalid email or password.", null, null);
                }

                var result = JsonSerializer.Deserialize<LoginResponse>(json, _jsonOptions);
                if (result?.User == null || string.IsNullOrWhiteSpace(result.Token))
                    return (false, "Invalid login response.", null, null);

                return (true, null, result.User, result.Token);
            }
            catch (Exception ex)
            {
                return (false, "Login service unavailable: " + ex.Message, null, null);
            }
        }

        public async Task<(bool Success, string? Error)> RegisterAsync(string fullName, string email, string password, string? phone)
        {
            try
            {
                var client = _httpClientFactory.CreateClient("NodeApi");
                var payload = new { fullName, email, password, phone = phone ?? "" };
                var content = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");

                var response = await client.PostAsync("api/auth/register", content);
                var json = await response.Content.ReadAsStringAsync();

                if (!response.IsSuccessStatusCode)
                {
                    var errObj = JsonSerializer.Deserialize<ErrorResponse>(json, _jsonOptions);
                    return (false, GetFriendlyError(errObj?.Error ?? ""));
                }

                return (true, null);
            }
            catch (Exception ex)
            {
                return (false, "Registration service unavailable: " + ex.Message);
            }
        }

        private static string GetFriendlyError(string message)
        {
            var m = message.ToLowerInvariant();
            if (m.Contains("already registered") || m.Contains("already exists"))
                return "An account with this email address already exists.";
            if (m.Contains("password"))
                return "The password does not meet the required requirements.";
            if (m.Contains("email"))
                return "Please enter a valid email address.";
            return message;
        }

        // -- Helper classes for JSON mapping --
        private class LoginResponse
        {
            public AppUser? User { get; set; }
            public string? Token { get; set; }
        }
        private class ErrorResponse
        {
            public string? Error { get; set; }
        }
    }
}