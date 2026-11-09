namespace Woodlands_Prototype_Insy7315.Models
{
    public class ProductsIndexViewModel
    {
        public string? ActiveCategory { get; set; } // null = "All"
        public string PageSubtitle { get; set; } = "";
        public List<Product> Products { get; set; } = new();
        public List<ProductCategory> Categories { get; set; } = new();
        public int TotalProductCount { get; set; }
    }
}
