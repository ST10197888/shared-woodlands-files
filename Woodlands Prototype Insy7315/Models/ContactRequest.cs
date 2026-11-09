using System.ComponentModel.DataAnnotations;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class ContactRequest
    {
        [Required(ErrorMessage = "Required")]
        [StringLength(100)]
        [Display(Name = "First Name")]
        public string FirstName { get; set; } = "";

        [Required(ErrorMessage = "Required")]
        [StringLength(100)]
        [Display(Name = "Last Name")]
        public string LastName { get; set; } = "";

        [Required(ErrorMessage = "Valid email required"), EmailAddress(ErrorMessage = "Valid email required")]
        [Display(Name = "Email Address")]
        public string Email { get; set; } = "";

        [Required(ErrorMessage = "Required")]
        [Display(Name = "Phone Number")]
        public string Phone { get; set; } = "";

        [Required(ErrorMessage = "Select a branch")]
        [Display(Name = "Nearest Branch")]
        public string Branch { get; set; } = "";

        [Required(ErrorMessage = "Select a service")]
        [Display(Name = "Service Required")]
        public string Service { get; set; } = "";

        [Required(ErrorMessage = "Describe your project")]
        [StringLength(2000, ErrorMessage = "Please keep the description under 2000 characters.")]
        [Display(Name = "Project Description")]
        public string Message { get; set; } = "";

        [Required]
        [Range(
        typeof(bool),
        "true",
        "true",
        ErrorMessage = "Please read the privacy notice before submitting your quote request.")]
        public bool PrivacyAcknowledged { get; set; }
        // Carries the requested product through the form so it can be displayed in the summary card and re-shown if validation fails.
        public string? ProductId { get; set; }
    }
}
