# INSY7315 Task 2 - Woodlands Project
<br>

## **Member Access**

* **Andile Nkonyane**  
  * **Student Number:** `ST10474534`  
  * **GitHub:** [@Goatmilton](https://github.com/Goatmilton)

<br>

* **Kennedy II Mwashusha**  
  * **Student Number:** `ST10197888`  
  * **GitHub:** [@ST10197888](https://github.com/ST10197888)

<br>

* **Nairon Cossa**  
  * **Student Number:** `ST10255547`  
  * **GitHub:** [@NaironCossa](https://github.com/NaironCossa)

<br>

* **Simphiwe Mathenjwa**  
  * **Student Number:** `ST10258505`  
  * **GitHub:** [@Mathenjwa-Simphiwe](https://github.com/Mathenjwa-Simphiwe)

<br>

* **Rico Baloi**  
  * **Student Number:** `ST10441543`  
  * **GitHub:** [@st10441543](https://github.com/st10441543)
  * **GitHub:** [@TechHashira or ricobaloi-gif](https://github.com/ricobaloi-gif)
>TechHashira or ricobaloi-gif is Rico Baloi's personal account used before the repository was mirrored to the official campus Project repository and cannot be added as a collaborator as it is not in the campus Org

<br>


## Branches

### 1. main

This is the production-ready branch. It contains code that is fully tested and deployed to the live environment. Only merge into this branch when a release is ready. Never commit directly to this branch.

### 2. develop

This is the integration branch. All feature branches merge into this branch first. It contains the latest development changes and is used for testing before release. This is where you verify that everything works together.

### 3. release

This is the release preparation branch. Create this when you are preparing for a submission or deployment. Use it for final testing and bug fixes before merging into main. Delete it after merging into main and back into develop.

### 4. hotfix

This is for emergency fixes to production. Create this only when there is a critical bug in main that needs immediate fixing. Merge back into both main and develop after fixing.

### 5. staging

This is the staging environment branch. It mirrors the production environment for final testing before going live. Deploy to Azure Staging from this branch.

<br><br>
<hr>

# Demo videos
>An issue orccured with Github LargeFiles causing the repo cloning to give a video not found error and videos not being uploaded when the error was caused by 1 video. To combat this the videos have been uploaded to YouTube and are accessible from: 

<br>
https://youtu.be/syKDdVxO0fI

>Woodlands Web Application Demo

<br>

https://youtu.be/NxUCMbnbb54

>Woodlands Mobile Application Demo

<br>

https://youtu.be/5--PaSAepkc

>Woodlands API Application and Database Demo

<br><br>
<hr>

# Woodlands Designer Boards - Mobile Application

This is a native Android Studio project created from the current Woodlands Designer Boards ASP.NET Core MVC prototype and the mobile wireframes in `TASK 1 (1).docx`.

## Current architecture

- **Android app:** Kotlin, native Android Views, local SQLite database.
- **API:** the mobile app now talks to the Woodlands API. It reads products, branches, FAQs, testimonials, users and quotes from the API and sends new quotes, contact messages and changes back to it.
- **Local database as a cache:** the local SQLite database stores a copy of the data on the phone, so the app still opens and shows content when there is no internet.
- **Offline changes:** anything created or edited while offline (for example a quote request) is saved in a waiting list on the phone and uploaded automatically once the connection is back.
- **Server address:** the app tries the hosted API first. If that can't be reached, it tries a local API on the emulator (`http://10.0.2.2:5000`). Both addresses are set in `Apiclient.kt`.
- **Website:** the original ASP.NET Core MVC website remains unchanged. The website and the app now share the same data through the API.
- **Future-ready:** the screens only talk to the local database layer and the sync layer, so the API or database behind them can be swapped later without redesigning the screens.

## Mobile functions included

- Home screen with a sliding hero banner, browse-by-category cards, featured products, the PG Bison partner banner and a testimonials strip.

<img width="336" height="1600" alt="image" src="https://github.com/user-attachments/assets/0eb3f468-5d90-45a0-98a1-66b92cac51ad" />

<br>

- Product/service gallery with category filtering

<img width="360" height="1364" alt="image" src="https://github.com/user-attachments/assets/af569feb-1ad3-496a-b5c7-a3d43cd3d1d4" />

<br>

- Product detail pages with features, finishes, pricing and lead time.

<img width="514" height="1600" alt="image" src="https://github.com/user-attachments/assets/77659a4e-dd0b-4f5b-9afb-d5680797fe34" />

<br>

- Product-to-quote flow, with a quote request form and confirmation.

https://github.com/user-attachments/assets/d1da9957-d3aa-406e-818d-dde822051e24

<br>

- Branch locator with Maps intent. Admins can add and edit branches, including a branch photo.

<img width="630" height="1600" alt="image" src="https://github.com/user-attachments/assets/2fc4e9f0-38c9-4e85-87fc-37d4f368d04c" />

https://github.com/user-attachments/assets/1460c4f3-1e40-4a8e-9a44-afcea5e06f1c

<br>

- About Us

<img width="360" height="1435" alt="image" src="https://github.com/user-attachments/assets/ddc5ee34-710f-449d-b512-7eaa877a5163" />

Testimonials

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/0ef32d5b-9a9e-4118-9189-cbbed9f3eb44" />

<br>

FAQs (category filters, expandable answers)

<img width="451" height="1600" alt="image" src="https://github.com/user-attachments/assets/cf9ecb00-4dcd-45b9-87b6-3c3634463f53" />

<br>

Contact form.

<img width="606" height="1600" alt="image" src="https://github.com/user-attachments/assets/f2dcf14b-d8aa-432f-9f59-f47b02eaee5a" />

<br>

- Legal Information: Privacy Notice, Terms of Service, Refunds and Cookies, matching the website pages.

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/4ccb54d3-c027-4a60-bb1e-534a265818c1" />

Privacy Notice

<img width="538" height="1600" alt="image" src="https://github.com/user-attachments/assets/be6f745a-8434-45b0-9aff-d4e57a600a85" />

Terms of Service

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/3f40390e-239d-4825-808b-c6c136c05a16" />

Refunds

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/6ea7ae05-03d6-42c5-84ed-890a7039549b" />

Cookies

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/78171164-21a1-4f53-b6c2-21452880acc7" />

<br>

- Connection Status screen (in the More tab) that shows whether the app can reach the Woodlands server and database, which services are online, and how many changes are still waiting to upload.

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/fe791ba8-8083-41cd-b185-6eeff611f8c9" />

>everything is connected

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/45a8bbf3-61dc-43e0-aa19-d48f55234b09" />

>If the device running the app is offline

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/29d276da-0d62-4bc6-a04d-c886547180f5" />

>if some entities in the api cant be reached or a connection issue cause the app to fail

<br>

- Persistent bottom navigation: Home, Gallery, Quote, Branches, More. Staff accounts see Quotes in place of Quote.

<img width="720" height="109" alt="image" src="https://github.com/user-attachments/assets/2570d5e3-ba34-428b-86fe-65adf05d652d" />

<br>

- A genuine slide-in sidebar (opened from the header's ? button) with site navigation and
  account actions, separate from the More tab - matching the website's mobile hamburger menu.

<img width="720" height="1515" alt="image" src="https://github.com/user-attachments/assets/ef6a0aee-97f3-4aa3-99dd-1883d89da9aa" />

>user not logged in

<img width="1080" height="2400" alt="image" src="https://github.com/user-attachments/assets/37bc5b99-978f-42ee-9319-731b4851a948" />

>when logged in as a manager

<br>

- Every tappable element (buttons, cards, chips, nav items) has a ripple/darken touch reaction,
  and the active bottom-nav tab is highlighted.

<br>

- **Accounts, roles and permissions**, mirroring the website's ASP.NET Core Identity setup:

<br>

- Register / Login / Logout. Login and registration check with the server, so an internet connection is needed for these. New accounts are created as Customers. The same seeded prototype accounts as the website are available (see `seeded accounts.md`):
  `admin@woodlandsdb.co.za` / `admin123`, `soweto@woodlandsdb.co.za` / `manager123`,
  `roodepoort@woodlandsdb.co.za` / `manager123`, `randfontein@woodlandsdb.co.za` / `manager123`,
  `customer@example.com` / `customer123`.
  - Profile screen (view role/branch, edit name/phone) and a Settings screen
    that mirrors the website's Dashboard ? Settings "My Account"/"Security" panels. Changing a password is not available in the app yet.
  - Role-aware Dashboard: Admin sees totals, per-branch summaries and links to manage Users,
    Products, Testimonials and FAQs; Branch Managers see their branch's pending/in-progress/
    completed counts; Customers don't get a dashboard, just My Quotes.
  - Quotes screen: Admins see every quote, Managers see their branch's quotes (with status
    controls: Pending/In Progress/Completed/Cancelled), Customers see only their own quotes
    (read-only) - the same visibility rules as `DashboardController` on the website.
  - Full CRUD management screens for Products (Admin + Managers) and, Admin-only, Users,
    Testimonials and FAQs - the same permission split as `ManagementController`/`AdminController`.
- Content comes from the API and is saved on the phone, so the catalogue, services, FAQs, testimonials, branches and quotes match what is on the website.

## Open in Android Studio

1. Open the `WoodlandsMobile` folder in Android Studio.
2. Allow Android Studio to sync Gradle and install any requested Android SDK components.
3. Use an Android emulator or a physical Android device running Android 15 (API 35) or newer.
4. Run the `app` configuration.

The app needs an internet connection the first time it opens, so it can download the catalogue. After that it can be used offline.

The generated `build/`, `.gradle/` and `.idea/` folders are not needed to open the project and can be left out when sharing it.

## Important prototype behaviour

Quote and contact submissions are sent to the Woodlands API, so staff on the website and in the app can see them. If the phone is offline, the submission is kept on the phone and sent later, and the Connection Status screen shows how many changes are still waiting.

Signing in, registering and the first download of data all need a connection. Browsing content that has already been downloaded works offline.

The source website contains several Unsplash image URLs. To keep this Android prototype usable without requiring a network image service, the app uses the supplied local Woodlands product photographs as packaged fallback imagery for those catalogue entries. Images that come from the API are downloaded once and kept on the phone.

The app allows plain `http` traffic so it can reach the local API on the emulator. This should be switched off before a real release.

## Where to edit the design

The code is split by concern so no single file gets unwieldy:

- `MainActivity.kt` - app chrome (header, bottom nav), screen switching, the gallery, product, quote, More and contact screens, and every
  shared UI helper (`button`, `card`, `chip`, `field`, colours, ripple/touch-reaction backgrounds).
- `Homescreens.kt` - the home screen sections (hero banner, categories, featured products, PG Bison banner, testimonials strip, footer).
- `Infoscreens.kt` - About Us, Testimonials, FAQs and the Legal Information screens.
- `BranchScreens.kt` - branch list, branch details and the admin branch form.
- `ConnectionsCard.kt` - the Connection Status screen.
- `Sidebar.kt` - the slide-in navigation drawer opened from the header's ? button.
- `AuthScreens.kt` - Login, Register, Profile, Settings.
- `AdminScreens.kt` - Dashboard, Quotes, and the Users/Products/Testimonials/FAQs management
  screens, gated by role.
- `Apiclient.kt` - sends requests to the API (hosted first, then local) and reports success, failure or offline.
- `Syncmanager.kt` - downloads data from the API, uploads waiting changes, handles login/register and the connection checks.
- `Woodlandsapp.kt` - starts the sync when the app opens and whenever the network comes back.
- `ImageLoader.kt` - downloads and caches images into the database.
- `LocalDb.kt` - SQLite tables for the on-phone copy of the data and the waiting list of changes.
- `Queries.kt` - all read/write helpers against `LocalDb`, used by every screen file.
- `Models.kt` - data classes plus the `Roles` object mirroring `IdentitySeederRoles` on the website.
- `Session.kt` - the signed-in-user session. `Security.kt` is an older local password helper and is not used for login any more.

Colours, typography and layout constants are intentionally straightforward so the design can be changed quickly.

The packaged product images are in:

`app/src/main/res/drawable-nodpi/`

## Moving to a production setup

The app already follows this path:

`Android UI -> Local database and sync -> REST API -> ASP.NET Core services -> EF Core -> database`

When the final hosting is chosen, only the server addresses in `Apiclient.kt` need to change. Do not make the Android app connect directly to the production SQL database.

<br><br>

<hr>

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
  "HostedBaseUrl": "https://Hosted api address",
  "LocalBaseUrl": "http://localhost:5000/"
}
```

- `HostedBaseUrl` is tried first on every request.
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

<br><br>

<hr>

# WoodLink Database

This section is on the WoodLink database, this database runs on Supabase - PostgreSQL. It is used by the website, the Node.js API and the Android app.

## Supabase Project Details (Database)

<br>

<img width="1919" height="988" alt="image" src="https://github.com/user-attachments/assets/721429de-7590-455c-9184-0b801ea609cf" />

<br>

- **Supabase Project:** INSY7315 Project (using production branch, free plan)
- **Region:** West EU (Ireland), `eu-west-1`
- **Project ID:** `hgpwxbmkkerbobtvnjx`


<br>



- PostgreSQL 17.6.1.166, Auth 2.197.0, PostgREST 14.5
- Supabase turns the tables into a REST API automatically (the Data API)

Project settings

<img width="1919" height="1929" alt="merged-image-2026-10-03T22-31-04" src="https://github.com/user-attachments/assets/f50b8b69-a5ec-4bae-aef9-a4c30e20b9cb" />

## Tables

| Table | Purpose |
|---|---|
| `app_users` | User profiles and roles |
| `branches` | The three branch details |
| `products` | Product catalogue |
| `quote_requests` | Customer quote requests |
| `services`, `faqs`, `testimonials`, `homepage_assets` | Website and mobile app content |

Database tables

<img width="1919" height="988" alt="image" src="https://github.com/user-attachments/assets/ef247872-d478-4ef6-a834-a402ba121c37" />

<br>

## Relationships and Constraints

- Every table has it's own primary key.
- `app_users.id` links to `auth.users(id)` and deleting a login also deletes the profile.
- `testimonials.rating` must be between 1 and 5.
- Other like a quote's branch and product are stored as plain text and not enforced.

Schema diagram

<img width="866" height="3925" alt="merged-image-2026-10-03T22-36-00" src="https://github.com/user-attachments/assets/62f7ea96-3d49-4fd5-a702-0acb10b6deac" />

<img width="940" height="534" alt="image" src="https://github.com/user-attachments/assets/f42c4da1-d0bc-4f4b-b162-bffbea09d6b4" />

<br>

## Authentication

- Supabase Auth with the email provider turned on
- Email confirmation is on, and anonymous sign-ins are off
- Roles - Admin, branch managers and Customer are stored in `app_users.role`
- The functions `is_admin`, `is_staff` and `get_user_branch` support role checks

Auth settings

<img width="940" height="485" alt="image" src="https://github.com/user-attachments/assets/eaf95b83-c438-4cb5-844b-488d90793ab6" />

<br>

## Row Level Security

- RLS is on for seven tables, with no policies yet. The public Data API returns no data for them.
- Only the Node API can read and write, because it uses the secret key.
- RLS is off on `app_users`.

RLS policies

<img width="940" height="996" alt="image" src="https://github.com/user-attachments/assets/4a1e6f17-99a6-4ec4-8e2f-631e1b87e4cf" />

<br>

## Storage

- One public bucket: `woodlands-assets` (unchanged values: 50 MB default limit, any file type)
- Two policies: "Public Access" (can only view) and "Admin Uploads" (allows for uploads)

Storage

<img width="940" height="191" alt="image" src="https://github.com/user-attachments/assets/7d38ef71-2354-446c-b23b-f216e0a255a2" />

>Existing buckets used by WoodLands webapp and mobile app

<img width="940" height="485" alt="image" src="https://github.com/user-attachments/assets/3f28126e-8c75-40bf-8363-b74461faac00" />

The woodlands-assets bucket polices

<br>

## Environment and Connection

> Both mobile and webapp do not touch the database or supabase but talk to the Node API which then relays what the webapp and mobile app require like completing requests, role specific actions and data control.

- The Node API connects over HTTP / HTTPS using the Supabase client and runs on port 5000.
- The URL and secret keys are kept within the API's `.env` file, which is included in the `.Gitignore` file so it is excluded in the GitHub commits.
- The webapp only talks to the Node API.
- The mobile app only talks to the Node API. It keeps a local SQLite copy for offline use.
- A direct Postgres connection also exists (using port 5432, database `postgres`).

Connection settings

<img width="1919" height="1950" alt="merged-image-2026-10-03T22-48-20" src="https://github.com/user-attachments/assets/5231baf5-3e1b-4768-8116-828a301bd408" />

<br>

API connection

<img width="940" height="313" alt="image" src="https://github.com/user-attachments/assets/ed722227-0b8c-41a2-9482-e108d323b44c" />

<br>
