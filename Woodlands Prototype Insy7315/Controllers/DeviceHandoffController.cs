using System.Net;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using QRCoder;
using Woodlands_Prototype_Insy7315.Models;
using Woodlands_Prototype_Insy7315.Services;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class DeviceHandoffController : Controller
    {
        private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

        private readonly IHttpClientFactory _http;
        private readonly ILogger<DeviceHandoffController> _logger;

        public DeviceHandoffController(IHttpClientFactory http, ILogger<DeviceHandoffController> logger)
        {
            _http = http;
            _logger = logger;
        }

        [Authorize]
        [HttpGet]
        public async Task<IActionResult> Phone()
        {
            var model = new PhoneHandoffViewModel();

            try
            {
                var client = _http.CreateClient("NodeApi");
                var response = await client.PostAsync(
                    "api/auth/handoff",
                    new StringContent("{}", Encoding.UTF8, "application/json"));

                if (response.IsSuccessStatusCode)
                {
                    using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
                    var code = doc.RootElement.GetProperty("code").GetString() ?? "";

                    model.Code = code;
                    model.Display = code.Length == 10 ? code[..5] + "-" + code[5..] : code;
                    model.ExpiresIn = doc.RootElement.TryGetProperty("expires_in", out var seconds) ? seconds.GetInt32() : 60;

                    var handoffUrl = Url.Action(
                        "Open",
                        "DeviceHandoff",
                        new { code = code },
                        Request.Scheme
                    );

                    model.QrSvg = BuildQrSvg(handoffUrl!);
                }
                else if (response.StatusCode == HttpStatusCode.TooManyRequests)
                {
                    model.Error = "Too many codes were requested. Wait a minute and try again.";
                }
                else
                {
                    model.Error = "A code could not be created right now.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error creating phone handoff code");
                model.Error = "A code could not be created right now.";
            }

            return View(model);
        }

        [AllowAnonymous]
        [HttpGet]
        public async Task<IActionResult> WebSignIn()
        {
            if (User.Identity?.IsAuthenticated == true)
            {
                return RedirectToAction("Index", "Dashboard");
            }

            var model = new WebSignInViewModel();

            try
            {
                var forwarded = Request.Headers["X-Forwarded-For"].ToString().Split(',')[0].Trim();
                var clientIp = !string.IsNullOrWhiteSpace(forwarded)
                    ? forwarded
                    : HttpContext.Connection.RemoteIpAddress?.ToString() ?? "";

                var payload = new { agent = Request.Headers.UserAgent.ToString(), ip = clientIp };
                var body = new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json");

                var client = _http.CreateClient("NodeApi");
                var response = await client.PostAsync("api/auth/handoff/request", body);

                if (response.IsSuccessStatusCode)
                {
                    using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
                    var code = doc.RootElement.GetProperty("user_code").GetString() ?? "";

                    model.RequestId = doc.RootElement.GetProperty("request_id").GetString() ?? "";
                    model.UserCode = code.Length == 8 ? code[..4] + "-" + code[4..] : code;
                    model.ExpiresIn = doc.RootElement.TryGetProperty("expires_in", out var seconds) ? seconds.GetInt32() : 120;
                }
                else if (response.StatusCode == HttpStatusCode.TooManyRequests)
                {
                    model.Error = "Too many requests were made. Wait a minute and try again.";
                }
                else
                {
                    model.Error = "A sign-in code could not be created right now.";
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error starting web sign-in request");
                model.Error = "A sign-in code could not be created right now.";
            }

            return View(model);
        }

        [AllowAnonymous]
        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Poll(string? requestId)
        {
            if (string.IsNullOrWhiteSpace(requestId) || requestId.Length > 100)
            {
                return Json(new { status = "expired" });
            }

            try
            {
                var client = _http.CreateClient("NodeApi");
                var response = await client.GetAsync($"api/auth/handoff/request/{Uri.EscapeDataString(requestId)}");

                if (response.StatusCode == HttpStatusCode.Gone) return Json(new { status = "expired" });
                if (response.StatusCode == HttpStatusCode.TooManyRequests) return Json(new { status = "pending" });
                if (!response.IsSuccessStatusCode) return Json(new { status = "error" });

                using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
                var status = doc.RootElement.TryGetProperty("status", out var s) ? s.GetString() : "";

                if (status == "pending") return Json(new { status = "pending" });
                if (status == "denied") return Json(new { status = "denied" });
                if (status != "approved") return Json(new { status = "error" });

                var user = JsonSerializer.Deserialize<AppUser>(doc.RootElement.GetProperty("user").GetRawText(), JsonOptions);
                var token = doc.RootElement.GetProperty("token").GetString();

                if (user == null || string.IsNullOrWhiteSpace(token))
                {
                    return Json(new { status = "error" });
                }

                await WebSignInHelper.SignInAsync(HttpContext, user, token, false);

                TempData["Success"] = $"Signed in as {user.FullName} ({user.Email}) using your phone.";

                return Json(new { status = "approved", redirect = Url.Action("Index", "Dashboard") });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error checking web sign-in request");
                return Json(new { status = "error" });
            }
        }

        [AllowAnonymous]
        [HttpGet]
        public IActionResult Open(string? code)
        {
            if (string.IsNullOrWhiteSpace(code) ||
                code.Length != 10 ||
                !code.All(char.IsLetterOrDigit))
            {
                return BadRequest("Invalid sign-in link.");
            }

            var appLink = $"woodlands://handoff?code={Uri.EscapeDataString(code)}";

            return View("OpenApp", model: appLink);
        }

        private static string BuildQrSvg(string content)
        {
            using var generator = new QRCodeGenerator();

            using var data = generator.CreateQrCode(
                content,
                QRCodeGenerator.ECCLevel.Q
            );

            var qrCode = new SvgQRCode(data);

            return qrCode.GetGraphic(
                10,
                "#000000",
                "#FFFFFF",
                true
            );
        }
    }
}
