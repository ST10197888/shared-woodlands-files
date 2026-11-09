using System.Text.Json.Serialization;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class QuoteRequest
    {
        [JsonPropertyName("id")]
        public Guid Id { get; set; }
        [JsonPropertyName("quote_code")]
        public string QuoteCode { get; set; } = "";
        [JsonPropertyName("first_name")]
        public string FirstName { get; set; } = "";
        [JsonPropertyName("last_name")]
        public string LastName { get; set; } = "";
        [JsonPropertyName("email")]
        public string Email { get; set; } = "";
        [JsonPropertyName("phone")]
        public string? Phone { get; set; }
        [JsonPropertyName("branch")]
        public string? Branch { get; set; }
        [JsonPropertyName("service")]
        public string? Service { get; set; }
        [JsonPropertyName("message")]
        public string? Message { get; set; }
        [JsonPropertyName("product_id")]
        public string? ProductId { get; set; }
        [JsonPropertyName("status")]
        public string Status { get; set; } = "Pending";
        [JsonPropertyName("value")]
        public string? Value { get; set; }
        [JsonPropertyName("created_at")]
        public DateTime CreatedAt { get; set; }
    }
}