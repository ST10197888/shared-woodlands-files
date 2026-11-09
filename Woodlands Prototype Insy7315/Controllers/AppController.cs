using Microsoft.AspNetCore.Mvc;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class AppController : Controller
    {
        private readonly IConfiguration _configuration;

        public AppController(IConfiguration configuration)
        {
            _configuration = configuration;
        }

        public IActionResult Index()
        {
            ViewBag.ApkUrl = _configuration["AppDownload:ApkUrl"] ?? "/downloads/woodlands-debug.apk";
            ViewBag.Version = _configuration["AppDownload:Version"] ?? "1.0.0";
            return View();
        }
    }
}