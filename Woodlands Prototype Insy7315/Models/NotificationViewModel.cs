
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class NotificationViewModel
    {
        [JsonPropertyName("id")]
        public Guid Id { get; set; }

        [JsonPropertyName("user_id")]
        public string UserId { get; set; } = string.Empty;

        [JsonPropertyName("actor_id")]
        public string? ActorId { get; set; }

        [JsonPropertyName("type")]
        public string Type { get; set; } = string.Empty;

        [JsonPropertyName("title")]
        public string Title { get; set; } = string.Empty;

        [JsonPropertyName("message")]
        public string Message { get; set; } = string.Empty;

        [JsonPropertyName("data")]
        public Dictionary<string, JsonElement> Data { get; set; } = new();

        [JsonPropertyName("action_required")]
        public bool ActionRequired { get; set; }

        [JsonPropertyName("action_status")]
        public string ActionStatus { get; set; } = "None";

        [JsonPropertyName("is_read")]
        public bool IsRead { get; set; }

        [JsonPropertyName("read_at")]
        public DateTimeOffset? ReadAt { get; set; }

        [JsonPropertyName("created_at")]
        public DateTimeOffset CreatedAt { get; set; }

        [JsonPropertyName("expires_at")]
        public DateTimeOffset? ExpiresAt { get; set; }
    }

    public class NotificationListViewModel
    {
        public List<NotificationViewModel> Notifications { get; set; } = new();

        public int UnreadCount { get; set; }

        public bool IsAvailable { get; set; } = true;
    }
}
