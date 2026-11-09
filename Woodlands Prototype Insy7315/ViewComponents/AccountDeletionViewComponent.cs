using Microsoft.AspNetCore.Mvc;
using Woodlands_Prototype_Insy7315.Models;
using Woodlands_Prototype_Insy7315.Services;

namespace Woodlands_Prototype_Insy7315.ViewComponents
{
    public class AccountDeletionViewModel
    {
        public bool IsAdmin { get; set; }

        public bool KeepData { get; set; }

        public DeletionRequest? Pending { get; set; }
    }

    public class AccountDeletionViewComponent : ViewComponent
    {
        private readonly IHttpClientFactory _http;

        public AccountDeletionViewComponent(IHttpClientFactory http)
        {
            _http = http;
        }

        public async Task<IViewComponentResult> InvokeAsync()
        {
            var model = new AccountDeletionViewModel
            {
                IsAdmin = UserClaimsPrincipal.IsInRole("Admin")
            };

            if (!model.IsAdmin)
            {
                var user = await AccountApiHelper.LoadUserAsync(_http, UserClaimsPrincipal);

                if (user != null)
                {
                    model.KeepData = user.KeepDataOnDelete;
                    model.Pending = await AccountApiHelper.LoadPendingRequestAsync(_http, user.Id);
                }
            }

            return View(model);
        }
    }
}
