using System.ComponentModel.DataAnnotations;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class ProductFormViewModel
    {
        public string? Id { get; set; }

        [Required(ErrorMessage = "Category is required.")]
        public string Category { get; set; } = "";

        [Required(ErrorMessage = "Title is required.")]
        public string Title { get; set; } = "";


        // Everything else is optional
        public bool IsFromPrice { get; set; }
        public string Tagline { get; set; } = "";
        public string Description { get; set; } = "";
        public string Image { get; set; } = "";
        public string Gallery { get; set; } = "";
        public string Features { get; set; } = "";
        public string Finishes { get; set; } = "";
        public string LeadTime { get; set; } = "";
        public string? Tag { get; set; }
        public string? Price { get; set; }
    }
}