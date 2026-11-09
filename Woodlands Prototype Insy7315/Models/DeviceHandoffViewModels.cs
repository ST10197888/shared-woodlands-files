namespace Woodlands_Prototype_Insy7315.Models
{
    public class PhoneHandoffViewModel
    {
        public string Code { get; set; } = "";
        public string Display { get; set; } = "";
        public string QrSvg { get; set; } = "";
        public int ExpiresIn { get; set; }
        public string? Error { get; set; }
    }

    public class WebSignInViewModel
    {
        public string RequestId { get; set; } = "";
        public string UserCode { get; set; } = "";
        public int ExpiresIn { get; set; }
        public string? Error { get; set; }
    }
}
