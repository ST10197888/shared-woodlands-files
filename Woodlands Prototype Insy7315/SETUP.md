# Woodlands ASP.NET Identity and CRUD setup

This version adds local SQLite + ASP.NET Core Identity, customer registration, seeded prototype accounts, role-based access control, and CRUD for products, services, users, and testimonials.

## Roles

- Admin
- Manager (Soweto)
- Manager (Roodepoort)
- Manager (Randfontein)
- Customer

Public registration always creates Customer accounts. Only Admin can change an account's role.

## CRUD permissions

- Admin: Products, Services, User Accounts, Testimonials
- Managers: Products, Services
- Customer: No management CRUD
- Testimonials: Admin only

## Prototype accounts

| Role | Email | Password |
|---|---|---|
| Admin | admin@woodlandsdb.co.za | admin123 |
| Manager (Soweto) | soweto@woodlandsdb.co.za | manager123 |
| Manager (Roodepoort) | roodepoort@woodlandsdb.co.za | manager123 |
| Manager (Randfontein) | randfontein@woodlandsdb.co.za | manager123 |
| Customer | customer@example.com | customer123 |

## First run

Run these commands from the folder containing the `.csproj` file:

```powershell
dotnet restore
dotnet build
dotnet tool install --global dotnet-ef
dotnet ef migrations add InitialIdentityAndContent
dotnet ef database update
dotnet run
```

If `dotnet-ef` is already installed, use:

```powershell
dotnet tool update --global dotnet-ef
```

The application also calls `Database.MigrateAsync()` when it starts, so future migrations are applied automatically after they have been created.

## Database

The local SQLite database is created at:

`Data/woodlands.db`

Delete that file only when you intentionally want to reset the prototype database and re-run the migration/seed process.

## Quote form

The public quote form still does not send email, call an API, or persist quote requests. It only validates and shows the existing confirmation page.
