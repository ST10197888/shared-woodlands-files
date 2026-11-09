using System.Text.Json;
using Microsoft.AspNetCore.Html;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ViewComponents;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.ViewComponents
{
    public class PendingDeletionBadgeViewComponent : ViewComponent
    {
        private readonly IHttpClientFactory _http;
        private readonly JsonSerializerOptions _json = new() { PropertyNameCaseInsensitive = true };

        public PendingDeletionBadgeViewComponent(IHttpClientFactory http)
        {
            _http = http;
        }

        public async Task<IViewComponentResult> InvokeAsync()
        {
            if (!UserClaimsPrincipal.IsInRole("Admin"))
            {
                return Content(string.Empty);
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                var response = await client.GetAsync("api/deletion-requests?status=Pending");

                if (!response.IsSuccessStatusCode)
                {
                    return Content(string.Empty);
                }

                var json = await response.Content.ReadAsStringAsync();
                var pending = (JsonSerializer.Deserialize<List<DeletionRequest>>(json, _json) ?? new())
                    .Where(r => string.Equals(r.Status, "Pending", StringComparison.OrdinalIgnoreCase))
                    .ToList();

                if (pending.Count == 0)
                {
                    return Content(string.Empty);
                }

                var badge = $"<span class=\"ml-auto px-2 py-0.5 rounded-full bg-danger text-white text-[0.65rem] font-semibold\">{pending.Count}</span>";

                return new HtmlContentViewComponentResult(new HtmlString(badge));
            }
            catch
            {
                return Content(string.Empty);
            }
        }
    }
}