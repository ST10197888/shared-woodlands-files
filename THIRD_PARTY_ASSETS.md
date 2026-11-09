# Third-party asset and license inventory

## Fonts

- **Inter** and **Fraunces** are requested from Google Fonts in `Woodlands Prototype Insy7315/Views/Shared/_ThemeHead.cshtml`. Google Fonts' upstream metadata identifies Inter as SIL Open Font License (OFL); Fraunces' upstream project includes its OFL license. The browser fetches font files from Google; the font binaries and license texts are not bundled here.
  - [Inter upstream metadata](https://github.com/google/fonts/blob/main/ofl/inter/METADATA.pb)
  - [Fraunces upstream project and OFL file](https://github.com/googlefonts/fraunces)
  - [SIL Open Font License 1.1](https://openfontlicense.org/)

## Images

The website references Unsplash CDN photos in `Woodlands Prototype Insy7315/Data/WoodLinkData.cs` and `Views/Home/About.cshtml`. The unique photo IDs found are:

- `photo-1720087448033-db1904db294b`
- `photo-1721738857328-3987700892e7`
- `photo-1721742604074-8e998cee43f2`
- `photo-1659930087003-2d64e33181f7`
- `photo-1497219055242-93359eeed651`
- `photo-1765371515101-6ff0001751cc`
- `photo-1611021061285-16c871740efa`

These are served from `images.unsplash.com`. Unsplash's standard license permits broad use of standard Unsplash images, but it does not itself grant rights to recognizable people, trademarks, artwork or all property depicted. Confirm each image's listing and any required releases before commercial use. The project has no contributor names or source-page records for these IDs.

- [Unsplash License](https://unsplash.com/license)
- [Unsplash guidance on releases and trademarks](https://help.unsplash.com/en/articles/2612329-releases-and-trademarks)

The archive also includes 24 local website image files (brand marks and product photos) and 16 local mobile image files under `WoodlandsMobile/WoodlandsMobile/app/src/main/res/`. Their creator, source, and license are not recorded in the supplied project. Confirm the business owns or has permission to use the logo and each local photo. Local source files were kept in the package.

## Bundled libraries

License notices are present beside the bundled Bootstrap, jQuery, jQuery Validation and jQuery Validation Unobtrusive files under `Woodlands Prototype Insy7315/wwwroot/lib/`. Keep those notices with the redistributed libraries. Tailwind CSS is currently loaded from its CDN rather than bundled as a local package.
