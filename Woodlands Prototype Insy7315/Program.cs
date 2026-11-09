using Woodlands_Prototype_Insy7315.Services;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.StaticFiles;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllersWithViews();
builder.Services.AddRazorPages();
builder.Services.AddHttpContextAccessor();

// Register HttpClient to talk to the Node API
builder.Services.AddTransient<ApiAuthHandler>();
builder.Services.AddTransient<HostedFirstFallbackHandler>();

builder.Services.AddHttpClient("NodeApi", client =>
{
    var baseUrl = builder.Configuration["NodeApi:BaseUrl"] ?? "http://localhost:5000/";
    client.BaseAddress = new Uri(baseUrl);
    var internalApiKey = builder.Configuration["API_INTERNAL_KEY"];
    if (!string.IsNullOrWhiteSpace(internalApiKey))
    {
        client.DefaultRequestHeaders.TryAddWithoutValidation("X-Internal-Api-Key", internalApiKey);
    }
})
.AddHttpMessageHandler<ApiAuthHandler>()            // adds the signed-in user's Bearer token
.AddHttpMessageHandler<HostedFirstFallbackHandler>(); // then hosted API first, local API as fallback

builder.Services.AddScoped<SupabaseAuthService>();
builder.Services.AddSingleton<BackupStore>();
builder.Services.AddScoped<BackupService>();

// CORS for the Android app
builder.Services.AddCors(options =>
{
    options.AddPolicy("AllowAndroidApp", policy =>
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader());
});

// Render terminates HTTPS in front of the container; trust its forwarded headers so the site knows the original request was HTTPS (needed for Secure cookies and redirects).
builder.Services.Configure<ForwardedHeadersOptions>(options =>
{
    options.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    options.KnownNetworks.Clear();
    options.KnownProxies.Clear();
});

// Cookie authentication
builder.Services
    .AddAuthentication(options =>
    {
        options.DefaultAuthenticateScheme = "WoodlandsCookie";
        options.DefaultChallengeScheme = "WoodlandsCookie";
        options.DefaultSignInScheme = "WoodlandsCookie";
    })
    .AddCookie("WoodlandsCookie", options =>
    {
        options.LoginPath = "/Account/Login";
        options.AccessDeniedPath = "/Account/AccessDenied";
        options.ExpireTimeSpan = TimeSpan.FromHours(8);
        options.SlidingExpiration = true;

        options.Cookie.HttpOnly = true;                              // not readable from JavaScript
        options.Cookie.SameSite = SameSiteMode.Lax;                  // not sent on cross-site POSTs
        options.Cookie.SecurePolicy = CookieSecurePolicy.SameAsRequest; // Secure on HTTPS (Render)

        // The cookie carries the API's token. When that token has expired the API would answer
        // 401 to every call, so end the session and send the user back to the login page.
        options.Events.OnValidatePrincipal = async context =>
        {
            var token = context.Principal?.FindFirst(ApiTokenHelper.ClaimType)?.Value;
            if (ApiTokenHelper.IsExpired(token))
            {
                context.RejectPrincipal();
                await context.HttpContext.SignOutAsync("WoodlandsCookie");
            }
        };
    });

builder.Services.AddAuthorization();

var app = builder.Build();

app.UseForwardedHeaders();

if (app.Environment.IsDevelopment())
{
    app.UseDeveloperExceptionPage();
}
else
{
    app.UseExceptionHandler("/Home/Error");
    app.UseHsts();
}

// Basic security headers on every response
app.Use(async (context, next) =>
{
    var headers = context.Response.Headers;
    headers["X-Content-Type-Options"] = "nosniff";
    headers["X-Frame-Options"] = "SAMEORIGIN";
    headers["Referrer-Policy"] = "strict-origin-when-cross-origin";
    headers["Permissions-Policy"] = "camera=(), microphone=(), geolocation=()";
    await next();
});

app.UseHttpsRedirection();
// Backups hold customer data: never serve the Updates folder as static files (downloads go through BackupsController)
app.Use(async (context, next) =>
{
    if (context.Request.Path.StartsWithSegments("/Updates"))
    {
        context.Response.StatusCode = StatusCodes.Status404NotFound;
        return;
    }
    await next();
});

var contentTypes = new FileExtensionContentTypeProvider();
contentTypes.Mappings[".apk"] = "application/vnd.android.package-archive";
app.UseStaticFiles(new StaticFileOptions { ContentTypeProvider = contentTypes });
app.UseRouting();
app.UseCors("AllowAndroidApp");
app.UseAuthentication();
app.UseAuthorization();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");
app.MapRazorPages();

app.Run();