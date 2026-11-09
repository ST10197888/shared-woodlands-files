using System.Text.RegularExpressions;

namespace Woodlands_Prototype_Insy7315.Services
{
    public static class InputSanitizer
    {
        private static readonly Regex TagPattern = new("<[^>]*>", RegexOptions.Compiled);

        [return: System.Diagnostics.CodeAnalysis.NotNullIfNotNull(nameof(input))]
        public static string? StripHtml(string? input)
        {
            if (string.IsNullOrEmpty(input)) return input;

            var withoutTags = TagPattern.Replace(input, string.Empty);

            return withoutTags
                .Replace("javascript:", string.Empty, StringComparison.OrdinalIgnoreCase)
                .Trim();
        }
    }
}