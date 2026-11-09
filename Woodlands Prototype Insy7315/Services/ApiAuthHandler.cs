using System.Net.Http.Headers;
using System.Text.Json;

namespace Woodlands_Prototype_Insy7315.Services
{
    
    // Adds "Authorization: Bearer {token}" to every call the website makes to the Node API. using the API token that was stored in the signed-in user's cookie at login.
    
    // Anonymous visitors (public pages, login, register, quote form) are sent without a token.
  
    public class ApiAuthHandler : DelegatingHandler
    {
        private readonly IHttpContextAccessor _accessor;

        public ApiAuthHandler(IHttpContextAccessor accessor)
        {
            _accessor = accessor;
        }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            var token = _accessor.HttpContext?.User?.FindFirst(ApiTokenHelper.ClaimType)?.Value;

            if (!string.IsNullOrWhiteSpace(token) && request.Headers.Authorization == null)
            {
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            }

            return base.SendAsync(request, cancellationToken);
        }
    }

    public static class ApiTokenHelper
    {
        //Name of the cookie claim that holds the API's JWT
        public const string ClaimType = "ApiToken";


        public static bool IsExpired(string? token)
        {
            if (string.IsNullOrWhiteSpace(token)) return true;

            try
            {
                var parts = token.Split('.');
                if (parts.Length != 3) return true;

                var payload = parts[1].Replace('-', '+').Replace('_', '/');
                payload = payload.PadRight(payload.Length + (4 - payload.Length % 4) % 4, '=');

                using var doc = JsonDocument.Parse(Convert.FromBase64String(payload));
                if (!doc.RootElement.TryGetProperty("exp", out var exp)) return true;

                return DateTimeOffset.FromUnixTimeSeconds(exp.GetInt64()) <= DateTimeOffset.UtcNow.AddSeconds(30);
            }
            catch
            {
                return true;
            }
        }
    }
}