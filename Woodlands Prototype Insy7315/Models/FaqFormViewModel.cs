using System.ComponentModel.DataAnnotations;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class FaqFormViewModel
    {
        public int Id { get; set; }

        [Required]
        public string Category { get; set; } = "";

        [Required]
        public string Question { get; set; } = "";

        [Required]
        public string Answer { get; set; } = "";
    }
}