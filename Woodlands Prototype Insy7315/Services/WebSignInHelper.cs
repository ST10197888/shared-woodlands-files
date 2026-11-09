using System.Security.Claims;
using Microsoft.AspNetCore.Authentication;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Services
{
    public static class WebSignInHelper
    {
        public static async Task SignInAsync(
            HttpContext httpContext,
            AppUser user,
            string apiToken,
            bool persistent)
        {
            var claims = new List<Claim>
            {
                new Claim(ClaimTypes.NameIdentifier, user.Id),
                new Claim(ClaimTypes.Name, user.Email),
                new Claim(ClaimTypes.Email, user.Email),
                new Claim("FullName", user.FullName),
                new Claim(ClaimTypes.Role, user.Role),
                new Claim("Branch", user.Branch ?? ""),
                new Claim(ApiTokenHelper.ClaimType, apiToken)
            };

            var identity = new ClaimsIdentity(claims, "WoodlandsCookie");
            var principal = new ClaimsPrincipal(identity);

            var properties = new AuthenticationProperties
            {
                IsPersistent = persistent,
                ExpiresUtc = persistent
                    ? DateTimeOffset.UtcNow.AddDays(30)
                    : DateTimeOffset.UtcNow.AddHours(8)
            };

            await httpContext.SignInAsync("WoodlandsCookie", principal, properties);
        }
    }
}
