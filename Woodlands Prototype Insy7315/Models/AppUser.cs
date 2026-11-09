using System.Text.Json.Serialization;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class AppUser
    {
        [JsonPropertyName("id")]
        public string Id { get; set; } = "";
        [JsonPropertyName("full_name")]
        public string FullName { get; set; } = "";
        [JsonPropertyName("email")]
        public string Email { get; set; } = "";
        [JsonPropertyName("phone")]
        public string Phone { get; set; } = "";
        [JsonPropertyName("role")]
        public string Role { get; set; } = "Customer";
        [JsonPropertyName("branch")]
        public string? Branch { get; set; }
        [JsonPropertyName("active")]
        public bool Active { get; set; } = true;
        [JsonPropertyName("keep_data_on_delete")]
        public bool KeepDataOnDelete { get; set; }
        [JsonPropertyName("created_at")]
        public DateTime CreatedAt { get; set; }
    }
}