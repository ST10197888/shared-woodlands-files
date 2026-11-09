using Supabase.Postgrest.Attributes;
using Supabase.Postgrest.Models;

namespace Woodlands_Prototype_Insy7315.Models
{
    [Table("products")]
    public class SupabaseProduct : BaseModel
    {
        [PrimaryKey("id", false)]
        [Column("id")]
        public string Id { get; set; } = "";

        [Column("category")]
        public string Category { get; set; } = "";

        [Column("title")]
        public string Title { get; set; } = "";

        [Column("tagline")]
        public string Tagline { get; set; } = "";

        [Column("description")]
        public string Description { get; set; } = "";

        [Column("image")]
        public string Image { get; set; } = "";

        [Column("gallery")]
        public string Gallery { get; set; } = "[]";

        [Column("features")]
        public string Features { get; set; } = "[]";

        [Column("finishes")]
        public string Finishes { get; set; } = "[]";

        [Column("lead_time")]
        public string LeadTime { get; set; } = "";

        [Column("tag")]
        public string? Tag { get; set; }

        [Column("price")]
        public string? Price { get; set; }
    }

    [Table("testimonials")]
    public class SupabaseTestimonial : BaseModel
    {
        [PrimaryKey("id", false)]
        public int Id { get; set; }

        [Column("name")]
        public string Name { get; set; } = "";

        [Column("role")]
        public string Role { get; set; } = "";

        [Column("location")]
        public string Location { get; set; } = "";

        [Column("rating")]
        public int Rating { get; set; }

        [Column("review")]
        public string Review { get; set; } = "";

        [Column("project")]
        public string Project { get; set; } = "";
    }

    [Table("faqs")]
    public class SupabaseFaqItem : BaseModel
    {
        [PrimaryKey("id", false)]
        public int Id { get; set; }

        [Column("category")]
        public string Category { get; set; } = "";

        [Column("question")]
        public string Question { get; set; } = "";

        [Column("answer")]
        public string Answer { get; set; } = "";
    }

    [Table("services")]
    public class SupabaseService : BaseModel
    {
        [PrimaryKey("id", false)]
        public int Id { get; set; }

        [Column("name")]
        public string Name { get; set; } = "";

        [Column("description")]
        public string Description { get; set; } = "";

        [Column("image")]
        public string Image { get; set; } = "";

        [Column("is_active")]
        public bool IsActive { get; set; }
    }

    [Table("branches")]
    public class SupabaseBranch : BaseModel
    {
        [PrimaryKey("id", false)]
        public long Id { get; set; }

        [Column("name")]
        public string Name { get; set; } = "";

        [Column("region")]
        public string Region { get; set; } = "";

        [Column("address")]
        public string Address { get; set; } = "";

        [Column("phone")]
        public string Phone { get; set; } = "";

        [Column("hours")]
        public string Hours { get; set; } = "";

        [Column("notes")]
        public string Notes { get; set; } = "";

        [Column("image")]
        public string Image { get; set; } = "";
    }

    [Table("homepage_assets")]
    public class SupabaseHomepageAsset : BaseModel
    {
        [PrimaryKey("id", false)]
        public long Id { get; set; }

        [Column("asset_type")]
        public string AssetType { get; set; } = "";

        [Column("heading")]
        public string Heading { get; set; } = "";

        [Column("accent")]
        public string Accent { get; set; } = "";

        [Column("sub_heading")]
        public string SubHeading { get; set; } = "";

        [Column("image")]
        public string Image { get; set; } = "";

        [Column("cta_text")]
        public string CtaText { get; set; } = "";

        [Column("link")]
        public string Link { get; set; } = "";

        [Column("display_order")]
        public int DisplayOrder { get; set; }

        [Column("is_active")]
        public bool IsActive { get; set; }

        [Column("created_at")]
        public DateTime CreatedAt { get; set; }
    }

    [Table("quote_requests")]
    public class SupabaseQuoteRequest : BaseModel
    {
        [PrimaryKey("id", false)]
        public long Id { get; set; }

        [Column("quote_code")]
        public string QuoteCode { get; set; } = "";

        [Column("first_name")]
        public string FirstName { get; set; } = "";

        [Column("last_name")]
        public string LastName { get; set; } = "";

        [Column("email")]
        public string Email { get; set; } = "";

        [Column("phone")]
        public string Phone { get; set; } = "";

        [Column("branch")]
        public string Branch { get; set; } = "";

        [Column("service")]
        public string Service { get; set; } = "";

        [Column("message")]
        public string Message { get; set; } = "";

        [Column("product_id")]
        public string ProductId { get; set; } = "";

        [Column("status")]
        public string Status { get; set; } = "";

        [Column("value")]
        public string? Value { get; set; }

        [Column("created_at")]
        public DateTime CreatedAt { get; set; }
    }

    [Table("app_users")]
    public class SupabaseAppUser : BaseModel
    {
        [PrimaryKey("id", false)]
        public string Id { get; set; } = "";

        [Column("full_name")]
        public string FullName { get; set; } = "";

        [Column("email")]
        public string Email { get; set; } = "";

        [Column("phone")]
        public string Phone { get; set; } = "";

        [Column("role")]
        public string Role { get; set; } = "";

        [Column("branch")]
        public string? Branch { get; set; }

        [Column("active")]
        public bool Active { get; set; }

        [Column("created_at")]
        public DateTime CreatedAt { get; set; }
    }
}