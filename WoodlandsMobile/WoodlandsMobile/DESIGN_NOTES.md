# Wireframe-to-App Mapping

The mobile wireframes in the supplied assessment document describe a compact mobile layout with a header containing a menu/logo/quote action, vertically stacked content, category chips, product cards, calls to action, and a bottom navigation concept.

Implemented screens:

| Wireframe/content | Android screen |
|---|---|
| Home / dashboard | `home` |
| Products / services gallery | `gallery` |
| Product details | `product` |
| About Us | `about` |
| Testimonials | `testimonials` |
| FAQs | `faqs` |
| Contact / quote request | `contact` / `quote` |
| Branch locator | `branches` |
| Bottom navigation | Home / Gallery / Quote / Branches / More |

The supplied website's existing seed data is the source of truth for the current mobile seed data. Where the website currently uses placeholder branch contact information (`011 XXX XXXX`) the mobile app keeps that same value rather than inventing a real number.

The app is deliberately not connected to the website because the current development requirement is two side-by-side applications with independent local databases and similar seeded data.

## Accounts & roles

The mobile app reproduces the website's account system locally rather than connecting to it:

| Website (`WoodLink.Models.IdentitySeederRoles`, ASP.NET Core Identity) | Mobile app |
|---|---|
| `Admin`, `Manager (Soweto/Roodepoort/Randfontein)`, `Customer` roles | Same four role strings, stored on a local `users` table |
| Passwords hashed by ASP.NET Core Identity | Passwords salted + SHA-256 hashed locally (`Security.kt`) before storage |
| Seeded prototype accounts (`Data/IdentitySeeder.cs`) | Same emails/passwords seeded into the local SQLite database |
| Auth cookie / `ClaimsPrincipal` | `SharedPreferences`-backed `Session.kt` |
| `_DashboardLayout.cshtml` sidebar, role-conditional links | `Sidebar.kt` drawer + `AdminScreens.kt`, same role checks |
| `DashboardController`, `ManagementController`, `AdminController` authorization | `requireStaff()` / `requireAdmin()` guards in `AdminScreens.kt`, matching each controller's `[Authorize(Roles = ...)]` |

The hamburger (☰) button opens a real slide-in sidebar (not a full screen) with site navigation
up top and account actions at the bottom — the mobile equivalent of the website's `#mobile-menu`.
The bottom-nav "More" tab is a separate account hub: user-management actions (Login/Register or
account summary + Dashboard/My Quotes/Profile/Settings/Logout) at the top, informational links
(About Us → Contact) below — the "Quick links" (Request a Quote / Browse Gallery) shortcuts were
removed from here since they duplicate the bottom nav itself.
