

# WoodLink Database

This section is on the WoodLink database, this database runs on Supabase - PostgreSQL. It is used by the website, the Node.js API and the Android app.

## Supabase Project Details (Database)

- **Supabase Project:** INSY7315 Project (using production branch, free plan)
- **Region:** West EU (Ireland), `eu-west-1`
- **Project ID:** `hgpwxbmkkerbobtvnjx`

Project overview <img width="1919" height="988" alt="image" src="https://github.com/user-attachments/assets/721429de-7590-455c-9184-0b801ea609cf" />

<br>

## **Member Access:**
  - Andile Nkonyane - ST10474534
  - Kennedy II Mwashusha - ST10197888
  - Nairon Cossa - ST10255547
  - Rico Baloi - ST10441543
  - Simphiwe Mathenjwa - ST10258505

<br>


## Database

- PostgreSQL 17.6.1.166, Auth 2.197.0, PostgREST 14.5
- Supabase turns the tables into a REST API automatically (the Data API)

>Project settings
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
