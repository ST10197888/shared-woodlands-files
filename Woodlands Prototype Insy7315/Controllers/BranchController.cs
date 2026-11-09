using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;

namespace Woodlands_Prototype_Insy7315.Controllers
{
    public class BranchesController : Controller
    {
        private readonly IHttpClientFactory _httpClientFactory;

        public BranchesController(IHttpClientFactory httpClientFactory)
        {
            _httpClientFactory = httpClientFactory;
        }

        public async Task<IActionResult> Index()
        {
            var client = _httpClientFactory.CreateClient("NodeApi");

            try
            {
                var response = await client.GetAsync("api/branches");

                if (!response.IsSuccessStatusCode)
                {
                    TempData["Error"] = "Unable to load branches.";
                    return View(new List<Branch>());
                }

                var json = await response.Content.ReadAsStringAsync();

                var options = new JsonSerializerOptions
                {
                    PropertyNameCaseInsensitive = true
                };

                var branches =
                    JsonSerializer.Deserialize<List<Branch>>(json, options)
                    ?? new List<Branch>();

                return View(branches);
            }
            catch
            {
                TempData["Error"] = "The branch service is currently unavailable.";
                return View(new List<Branch>());
            }
        }

        [Authorize(Roles = "Admin,Manager (Soweto),Manager (Roodepoort),Manager (Randfontein)")]
        public async Task<IActionResult> Manage()
        {
            var branches = (await GetBranches()).Where(CanManage).ToList();

            ViewData["Title"] = "Branch Management";
            ViewData["Subtitle"] = "Manage branch information, contact details and branch photos.";

            return View("~/Views/Branches/Manage.cshtml", branches);
        }

        [Authorize(Roles = "Admin,Manager (Soweto),Manager (Roodepoort),Manager (Randfontein)")]
        [HttpGet]
        public async Task<IActionResult> Edit(long id)
        {
            var branch = await GetBranch(id);

            if (branch == null)
            {
                TempData["Error"] = "Branch not found.";
                return RedirectToAction(nameof(Manage));
            }

            if (!CanManage(branch))
            {
                return Forbid();
            }

            ViewData["Title"] = $"Edit {branch.Name} Branch";
            ViewData["Subtitle"] = "Update branch information shown on the website and mobile app.";

            return View("~/Views/Branches/Edit.cshtml", branch);
        }

        [Authorize(Roles = "Admin,Manager (Soweto),Manager (Roodepoort),Manager (Randfontein)")]
        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Edit(
            long id,
            Branch model,
            IFormFile? imageFile)
        {
            if (id != model.Id)
            {
                return BadRequest();
            }

            var existing = await GetBranch(id);

            if (existing == null)
            {
                TempData["Error"] = "Branch not found.";
                return RedirectToAction(nameof(Manage));
            }

            if (!CanManage(existing))
            {
                return Forbid();
            }

            model.Name = existing.Name;

            if (string.IsNullOrWhiteSpace(model.Region))
            {
                ModelState.AddModelError(nameof(model.Region), "Region is required.");
            }

            if (!ModelState.IsValid)
            {
                ViewData["Title"] = $"Edit {model.Name} Branch";
                ViewData["Subtitle"] = "Update branch information shown on the website and mobile app.";
                return View("~/Views/Branches/Edit.cshtml", model);
            }

            var client = _httpClientFactory.CreateClient("NodeApi");

            var payload = new
            {
                region = model.Region.Trim(),
                address = model.Address?.Trim() ?? "",
                phone = model.Phone?.Trim() ?? "",
                hours = model.Hours?.Trim() ?? "",
                notes = model.Notes?.Trim() ?? ""
            };

            var json = JsonSerializer.Serialize(payload);

            using var content = new StringContent(
                json,
                Encoding.UTF8,
                "application/json");

            var response = await client.PutAsync(
                $"api/branches/{id}",
                content);

            if (!response.IsSuccessStatusCode)
            {
                TempData["Error"] = "Unable to save the branch.";
                return View("~/Views/Branches/Edit.cshtml", model);
            }

            if (imageFile != null && imageFile.Length > 0)
            {
                if (imageFile.Length > 8 * 1024 * 1024)
                {
                    TempData["Error"] = "The branch image must be smaller than 8 MB.";
                    return RedirectToAction(nameof(Edit), new { id });
                }

                var contentType =
                    imageFile.ContentType == "image/png"
                        ? "image/png"
                        : "image/jpeg";

                await using var stream = imageFile.OpenReadStream();
                using var memory = new MemoryStream();

                await stream.CopyToAsync(memory);

                var imagePayload = new
                {
                    contentType,
                    data = Convert.ToBase64String(memory.ToArray())
                };

                var imageJson = JsonSerializer.Serialize(imagePayload);

                using var imageContent = new StringContent(
                    imageJson,
                    Encoding.UTF8,
                    "application/json");

                var imageResponse = await client.PutAsync(
                    $"api/branches/{id}/image",
                    imageContent);

                if (!imageResponse.IsSuccessStatusCode)
                {
                    TempData["Error"] =
                        "Branch details were saved, but the new image could not be uploaded.";

                    return RedirectToAction(nameof(Edit), new { id });
                }
            }

            TempData["Success"] = $"{model.Name} branch updated successfully.";

            return RedirectToAction(nameof(Manage));
        }

        private bool CanManage(Branch branch)
        {
            if (User.IsInRole("Admin")) return true;

            var assigned = User.FindFirst("Branch")?.Value;

            return !string.IsNullOrWhiteSpace(assigned) &&
                   string.Equals(branch.Name?.Trim(), assigned.Trim(), StringComparison.OrdinalIgnoreCase);
        }

        private async Task<List<Branch>> GetBranches()
        {
            var client = _httpClientFactory.CreateClient("NodeApi");

            try
            {
                var response = await client.GetAsync("api/branches");

                if (!response.IsSuccessStatusCode)
                {
                    return new List<Branch>();
                }

                var json = await response.Content.ReadAsStringAsync();

                return JsonSerializer.Deserialize<List<Branch>>(
                           json,
                           new JsonSerializerOptions
                           {
                               PropertyNameCaseInsensitive = true
                           })
                       ?? new List<Branch>();
            }
            catch
            {
                return new List<Branch>();
            }
        }

        private async Task<Branch?> GetBranch(long id)
        {
            var client = _httpClientFactory.CreateClient("NodeApi");

            try
            {
                var response =
                    await client.GetAsync($"api/branches/{id}");

                if (!response.IsSuccessStatusCode)
                {
                    return null;
                }

                var json = await response.Content.ReadAsStringAsync();

                return JsonSerializer.Deserialize<Branch>(
                    json,
                    new JsonSerializerOptions
                    {
                        PropertyNameCaseInsensitive = true
                    });
            }
            catch
            {
                return null;
            }
        }
    }
}