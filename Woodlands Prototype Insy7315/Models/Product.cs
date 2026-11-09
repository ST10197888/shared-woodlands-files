using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Woodlands_Prototype_Insy7315.Models
{
    public class Product
    {
        [Key]
        [JsonPropertyName("id")]
        public string Id { get; set; } = "";

        [Required]
        [JsonPropertyName("category")]
        public string Category { get; set; } = "";


        [Required]
        [JsonPropertyName("title")]
        public string Title { get; set; } = "";

        [JsonPropertyName("tagline")]
        public string Tagline { get; set; } = "";

        [JsonPropertyName("description")]
        public string Description { get; set; } = "";

        [JsonPropertyName("image")]
        public string Image { get; set; } = "";

  
        [JsonPropertyName("gallery")]
        public List<string> Gallery
        {
            get => ReadList(GalleryJson);
            set => GalleryJson = WriteList(value);
        }

        [JsonPropertyName("features")]
        public List<string> Features
        {
            get => ReadList(FeaturesJson);
            set => FeaturesJson = WriteList(value);
        }


        [JsonIgnore]
        public string GalleryJson { get; set; } = "[]";

        [JsonIgnore]
        public string FeaturesJson { get; set; } = "[]";

        [JsonIgnore]
        public string FinishesJson { get; set; } = "[]";

        [NotMapped]
        [JsonPropertyName("finishes")]
        public List<string> Finishes
        {
            get => ReadList(FinishesJson);
            set => FinishesJson = WriteList(value);
        }

        [JsonPropertyName("lead_time")]
        public string LeadTime { get; set; } = "";

        [JsonPropertyName("tag")]
        public string? Tag { get; set; }

        [JsonPropertyName("price")]
        public string? Price { get; set; }

        [JsonPropertyName("is_from_price")]
        public bool IsFromPrice { get; set; }

        [NotMapped]
        public string DisplayPrice
        {
            get
            {
                if (string.IsNullOrWhiteSpace(Price))
                    return "Price on request";

                var price = Price.Trim();

                if (IsFromPrice && !price.StartsWith("From ", StringComparison.OrdinalIgnoreCase))
                    return $"From {price}";

                return price;
            }
        }

        private static List<string> ReadList(string json)
        {
            if (string.IsNullOrWhiteSpace(json))
                return new List<string>();

            try
            {
 
                return JsonSerializer.Deserialize<List<string>>(json) ?? new List<string>();
            }
            catch
            {
                try
                {
 
                    var wrapped = JsonSerializer.Deserialize<string>(json);
                    if (wrapped != null)
                        return JsonSerializer.Deserialize<List<string>>(wrapped) ?? new List<string>();
                }
                catch { }
                return new List<string>();
            }
        }

        private static string WriteList(List<string>? values)
        {
            return JsonSerializer.Serialize(values ?? new List<string>());
        }
    }

    public class ProductCategory
    {
        public string Id { get; set; } = "";
        public string Label { get; set; } = "";
        public string Image { get; set; } = "";
        public int Count { get; set; }
        public string Description { get; set; } = "";
        public string Slug { get; set; } = "";
    }

    public class HeroSlide
    {
        public int Id { get; set; }
        public string Heading { get; set; } = "";
        public string Accent { get; set; } = "";
        public string Sub { get; set; } = "";
        public string Image { get; set; } = "";
        public string Cta { get; set; } = "";
        public string Link { get; set; } = "";
    }
}