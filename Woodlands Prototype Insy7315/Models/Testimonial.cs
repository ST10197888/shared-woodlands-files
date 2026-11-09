using System.Text.Json.Serialization;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class Testimonial
    {
        [JsonPropertyName("id")]
        public int Id { get; set; }
        [JsonPropertyName("name")]
        public string Name { get; set; } = "";
        [JsonPropertyName("role")]
        public string Role { get; set; } = "";
        [JsonPropertyName("location")]
        public string Location { get; set; } = "";
        [JsonPropertyName("rating")]
        public int Rating { get; set; } = 5;
        [JsonPropertyName("review")]
        public string Review { get; set; } = "";
        [JsonPropertyName("project")]
        public string Project { get; set; } = "";
    }
}