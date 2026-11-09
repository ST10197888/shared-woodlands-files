# Woodlands Designer Boards - Web App

This is the ASP.NET Core MVC website for Woodlands Designer Boards. It shows the public catalogue allowing customers to request quotes and it gives staff a role-based access dashboard to manage the content and quote requests. All of its data comes from the Woodlands API, so the website and the Android app always show the same information.

## Current architecture

- **Web app:** ASP.NET Core MVC on .NET 10 with Razor views and a shared stylesheet (`wwwroot/css/site.css`).
- **API:** the site does not have its own database. Every page reads and writes through the Woodlands Node API, which sits within Supabase.
- **Server address:** the site first tries the hosted API first. If that can't be reached or takes longer than 8 seconds, it tries a local API at `http://localhost:5000/`. Both addresses are set in `appsettings.json`.
- **Login:** the website sends a login request to the API, which checks them with Supabase Auth and returns the user's profile. The site then signs the user in with a cookie.
- **Roles:** Admin, 3 main Managers Soweto, Roodepoort and Randfontein then the Customer. The role and branch come from the user's profile and are stored in the login cookie.
- **Mobile app:** the Android app uses the same API, so changes made in the website show up there too.

## Website functions included

- Home page with a time-based hero banner, browse-by-category cards, featured products and a testimonials strip if testimonials exist in the system.
- Product catalogue with category filtering and product detail pages with features, finishes, pricing and lead time.
- Services, FAQs and Testimonials pages.
- Branch locator showing the details and photo for each branch.
- About Us page.
- Legal pages for Privacy Notice, Terms of Service, Refunds and Cookies.
- Quote request form, which can be opened from a product so the product is filled in for the customer. The customer gets a confirmation page with their quote reference.
- Register, Login (with a remember me option), Logout and an Access Denied page.
- **Role-based Dashboard** after login:
  - Admin sees totals and a summary for every branch.
  - Branch Managers see the quotes for their own branch.
  - Customers see only their own quotes.
- Quote status controls (Pending, In Progress, Completed, Cancelled) for Admins and Managers. Managers can only change the status of quotes in their own branch.
- **Management screens** with create, edit and delete for products, services, testimonials, users, FAQs and branches.

## Who can do what

| Area | Admin | Managers | Customer |
|---|---|---|---|
| Public pages and quote form | Yes (website only) | Yes (website only) | Yes |
| Dashboard | All quotes | Their branch's quotes | Their own quotes |
| Update quote status | Yes | Their branch only | No |
| Products | Manage | Manage | No |
| Services and service requests | Manage | Manage | No |
| Testimonials | Manage | Manage | No |
| Users | Manage | No | No |
| FAQs | Manage | No | No |
| Branches | Manage | No | No |

Public registration always creates a Customer. Only an Admin can change a user's role. Staff accounts cannot submit the public quote form.

## Project structure

- `Program.cs` - sets up the API client, cookie login and routing.
- `Controllers` - one controller each: `HomeController`, `ProductsController`, `ServicesController`, `FAQsController`, `TestimonialsController`, `BranchController`, `ContactController`, `AccountController`, `DashboardController`, `ManagementController` and `AdminController`.
- `Models` - data classes and view models for the forms and dashboard.
- `Views` - the Razor pages, split by controller, with the shared layouts in `Views/Shared/`.
- `Services/SupabaseAuthService.cs` - sends login and register requests to the API.
- `Services/HostedFirstFallbackHandler.cs` - tries the hosted API first, then the local one.
- `Services/InputSanitizer.cs` - strips HTML from text that customers type into forms.
- `Data/WoodLinkData.cs` - the hero slides and category lists used on the home and product pages.
- `wwwroot` - the stylesheet, scripts, brand images and product photos.
- `appsettings.json` - the API addresses.

## Getting started

1. Install the .NET 10 SDK
2. Start the Woodlands API using `npx nodemon server.js` 
3. The webapp will either use the hosted API or run Node API locally on port 5000 if the hosted API can't be reached (see the API README).
4. Open the `Woodlands Prototype Insy7315.sln` solution in Visual Studio then run the application
5. Open `http://localhost:5159` or `https://localhost:7190` when using the https profile they both direct to the same Woodlands site.

## Configuration

The API addresses are set in `appsettings.json`:

```json5
"NodeApi": {
  "BaseUrl": "https://Hosted api address",
  "LocalBaseUrl": "http://localhost:5000/"
}
```

- `BaseUrl` is tried first on every request.
- `LocalBaseUrl` is used if the hosted API can't be reached or times out.

When the final hosting is chosen, only these two addresses need to change.

## Prototype accounts

These accounts are created when the API is seeded, so the website and the mobile app share them:
>These are testing and demo accounts that will be removed once the applications are complete and out of "prototype" phase.

| Role | Email | Password |
|---|---|---|
| Admin | admin@woodlandsdb.co.za | admin123 |
| Manager (Soweto) | soweto@woodlandsdb.co.za | manager123 |
| Manager (Roodepoort) | roodepoort@woodlandsdb.co.za | manager123 |
| Manager (Randfontein) | randfontein@woodlandsdb.co.za | manager123 |
| Customer | customer@example.com | customer123 |

## Important prototype behaviour

Quote requests are sent to the Woodlands API with a reference code that starts with `WL-` and a status `pending`. Staff can see them on the dashboard, and they also appear in the mobile app. If the API can't be reached, the customer sees an error message and can try again.

Logging in and registering both need the API to be online. Pages that load data show empty lists if the API can't be reached.

The login cookie lasts 8 hours, or 30 days when the user ticks remember me.

Text typed into forms has HTML tags removed before it is sent, and every form uses an anti forgery token.

## Moving to a production setup

Before going live, these need be addressed:

- Change or remove the seeded prototype accounts and seeded data.
- Move the API addresses and any secrets into the hosting provider's secret settings instead of `appsettings.json`.
- Lock down the API endpoints so they check who is calling them, because the website currently relies on its own role checks alone.
- Restrict CORS to the real website and app addresses instead of allowing every origin.

The path to production stays the same, only the settings change:

`Web app to REST API to Supabase (PostgreSQL, Auth and Storage)`
