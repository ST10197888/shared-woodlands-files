using System.ComponentModel.DataAnnotations;
using System.Text.Json.Serialization;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class Service
    {
        [JsonPropertyName("id")]
        public int Id { get; set; }

        //[Required]
        [StringLength(120)]
        [JsonPropertyName("name")]
        public string Name { get; set; } = "";

        [StringLength(1000)]
        [JsonPropertyName("description")]
        public string? Description { get; set; } = "";

        [JsonPropertyName("image")]
        public string? Image { get; set; } = "";

        [JsonPropertyName("is_active")]
        public bool IsActive { get; set; } = true;
    }
}