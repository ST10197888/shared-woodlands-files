using System.Net;
using System.Net.Http.Headers;

namespace Woodlands_Prototype_Insy7315.Services
{
    public class HostedFirstFallbackHandler : DelegatingHandler
    {
        private readonly IConfiguration _configuration;

        public HostedFirstFallbackHandler(IConfiguration configuration)
        {
            _configuration = configuration;
        }

        protected override async Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            try
            {
                // The HttpClient is configured with the Railway URL,
                // so this is always attempted first.
                return await base.SendAsync(request, cancellationToken);
            }
            catch (HttpRequestException)
            {
                return await SendToLocalApiAsync(request, cancellationToken);
            }
            catch (TaskCanceledException) when (!cancellationToken.IsCancellationRequested)
            {
                return await SendToLocalApiAsync(request, cancellationToken);
            }
        }

        private async Task<HttpResponseMessage> SendToLocalApiAsync(
            HttpRequestMessage originalRequest,
            CancellationToken cancellationToken)
        {
            var localBaseUrl =
                _configuration["NodeApi:LocalBaseUrl"]
                ?? "http://localhost:5000/";

            var localRequest = await CloneRequestAsync(
                originalRequest,
                cancellationToken);

            var pathAndQuery = originalRequest.RequestUri?.PathAndQuery ?? "/";

            localRequest.RequestUri =
                new Uri(new Uri(localBaseUrl), pathAndQuery);

            return await base.SendAsync(localRequest, cancellationToken);
        }

        private static async Task<HttpRequestMessage> CloneRequestAsync(
            HttpRequestMessage original,
            CancellationToken cancellationToken)
        {
            var clone = new HttpRequestMessage(
                original.Method,
                original.RequestUri);

            foreach (var header in original.Headers)
            {
                clone.Headers.TryAddWithoutValidation(
                    header.Key,
                    header.Value);
            }

            if (original.Content != null)
            {
                var content = await original.Content.ReadAsByteArrayAsync(
                    cancellationToken);

                var clonedContent = new ByteArrayContent(content);

                foreach (var header in original.Content.Headers)
                {
                    clonedContent.Headers.TryAddWithoutValidation(
                        header.Key,
                        header.Value);
                }

                clone.Content = clonedContent;
            }

            return clone;
        }
    }
}