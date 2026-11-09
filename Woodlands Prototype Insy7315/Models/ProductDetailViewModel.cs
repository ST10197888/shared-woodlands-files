namespace Woodlands_Prototype_Insy7315.Models
{
    public class ProductDetailViewModel
    {
        public Product Product { get; set; } = new();
        public string CategorySlug { get; set; } = "";
        public List<Product> Related { get; set; } = new();
    }
}
