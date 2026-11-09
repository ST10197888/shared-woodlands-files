using Microsoft.AspNetCore.Identity;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class ApplicationUser : IdentityUser
    {
        public string FullName { get; set; } = "";
        public string? Branch { get; set; }
    }
}
