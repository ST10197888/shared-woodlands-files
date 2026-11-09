namespace Woodlands_Prototype_Insy7315.Models
{
    public class ProductDeletionRequestViewModel
    {
        public string Id { get; set; } = "";
        public string ProductTitle { get; set; } = "";
        public string RequestedBy { get; set; } = "";
        public string Reason { get; set; } = "";
        public string AdminNote { get; set; } = "";
        public string Status { get; set; } = "Pending";
        public DateTime? CreatedAt { get; set; }

        public bool IsPending => string.Equals(Status, "Pending", StringComparison.OrdinalIgnoreCase);
    }

    public class EmailChangeRequestViewModel
    {
        public string Id { get; set; } = "";
        public string CurrentEmail { get; set; } = "";
        public string NewEmail { get; set; } = "";
        public string Status { get; set; } = "Pending";
        public DateTime? CreatedAt { get; set; }

        public bool IsPending => string.Equals(Status, "Pending", StringComparison.OrdinalIgnoreCase);
    }
}
