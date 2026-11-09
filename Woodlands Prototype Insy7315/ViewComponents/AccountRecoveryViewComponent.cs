using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Services;

namespace Woodlands_Prototype_Insy7315.ViewComponents
{
    public class AccountRecoveryViewModel
    {
        public bool KeepData { get; set; }

        public bool ArchiveAvailable { get; set; }
    }

    public class AccountRecoveryViewComponent : ViewComponent
    {
        private readonly IHttpClientFactory _http;

        public AccountRecoveryViewComponent(IHttpClientFactory http)
        {
            _http = http;
        }

        public async Task<IViewComponentResult> InvokeAsync()
        {
            if (UserClaimsPrincipal.IsInRole("Admin"))
            {
                return Content(string.Empty);
            }

            var user = await AccountApiHelper.LoadUserAsync(_http, UserClaimsPrincipal);

            if (user == null)
            {
                return Content(string.Empty);
            }

            var model = new AccountRecoveryViewModel
            {
                KeepData = user.KeepDataOnDelete,
                ArchiveAvailable = await AccountApiHelper.ArchiveAvailableAsync(_http, user.Email)
            };

            return View(model);
        }
    }
}
