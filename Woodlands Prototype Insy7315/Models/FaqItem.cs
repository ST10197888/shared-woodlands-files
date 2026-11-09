using System.Text.Json.Serialization;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class FaqItem
    {
        [JsonPropertyName("id")]
        public int Id { get; set; }
        [JsonPropertyName("category")]
        public string Category { get; set; } = "";
        [JsonPropertyName("question")]
        public string Question { get; set; } = "";
        [JsonPropertyName("answer")]
        public string Answer { get; set; } = "";
    }
}