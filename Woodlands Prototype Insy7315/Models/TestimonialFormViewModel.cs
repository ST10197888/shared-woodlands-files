using System.ComponentModel.DataAnnotations;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class TestimonialFormViewModel
    {
        public int Id { get; set; }

        [Required]
        public string Name { get; set; } = "";

        public string Role { get; set; } = "";
        public string Location { get; set; } = "";

        [Range(1, 5)]
        public int Rating { get; set; } = 5;

        [Required]
        public string Review { get; set; } = "";

        public string Project { get; set; } = "";
    }
}
