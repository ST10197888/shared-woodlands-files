namespace Woodlands_Prototype_Insy7315.Models
{
    public class HomeViewModel
    {
        public List<HeroSlide> HeroSlides { get; set; } = new();
        public List<ProductCategory> Categories { get; set; } = new();
        public List<Product> FeaturedProducts { get; set; } = new();
        public List<Testimonial> TestimonialsSnippet { get; set; } = new();
    }
}
