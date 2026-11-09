using System.ComponentModel.DataAnnotations;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class UserFormViewModel
    {
        public string? Id { get; set; }

        [Required]
        [Display(Name = "Full Name")]
        public string FullName { get; set; } = "";

        [Required]
        [EmailAddress]
        public string Email { get; set; } = "";

        [Phone]
        public string? PhoneNumber { get; set; }

        [Required]
        public string Role { get; set; } = IdentitySeederRoles.Customer;

        public string? Branch { get; set; }

        public bool Active { get; set; } = true;

        [DataType(DataType.Password)]
        [MinLength(8)]
        public string? Password { get; set; }
    }

    public static class IdentitySeederRoles
    {
        public const string Admin = "Admin";
        public const string SowetoManager = "Manager (Soweto)";
        public const string RoodepoortManager = "Manager (Roodepoort)";
        public const string RandfonteinManager = "Manager (Randfontein)";
        public const string Customer = "Customer";

        public static readonly string[] All =
        {
            Admin,
            SowetoManager,
            RoodepoortManager,
            RandfonteinManager,
            Customer
        };
    }
}