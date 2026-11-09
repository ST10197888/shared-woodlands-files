# Project compliance review

This is a code review of the supplied INSY7315 student prototype, not a legal certification. The public UI itself identifies contact details, pricing and imagery as placeholders. Confirm all business facts, data flows and final policy wording with the responsible business before using the site with real customers.

## Findings and changes

| Item | Result |
|---|---|
| Privacy policy | Replaced the generic template with a project-specific privacy notice. It describes quote and account fields, use, external page resources, and the limits of the current deletion and retention features. |
| Terms of service | Added a short prototype terms page. It explains that quote requests are not confirmed orders and identifies missing legal business details. |
| Refund policy | Added a refunds and cancellations page. The site has no checkout or payment flow; final custom-order, deposit and cancellation terms still need the owner's review. |
| Cookie policy | Added a cookie notice describing the essential sign-in cookie and the absence of advertising or analytics cookies in this codebase. |
| Form consent | Website quote and account forms and mobile quote, contact and registration forms now require acknowledgement of the privacy notice. Quote contact is limited to responding to the request; there is no marketing opt-in. |
| Unnecessary data | Website quote phone number is optional. Other quote fields support identifying and answering the quote. Account phone was already optional. Mobile quote and contact phone fields are also optional. |
| Dark patterns | No preselected marketing choices or coercive consent controls were present. Removed the unsupported promise “No spam, ever” and removed unsubstantiated proof counters from public pages. |
| Fake reviews | Removed invented seeded testimonials from future database initialization and removed public rendering of unverified review records. The public testimonials page now explains that verified feedback is not yet published. |
| Accessibility alt text | Public images had alt text. Changed stock-photo descriptions that implied the photos showed actual Woodlands work or branches. Decorative product-gallery thumbnails remain intentionally empty-alt inside labelled controls. |
| Colour contrast | Added visible keyboard focus and increased low-opacity white text used for content. This is a targeted source-level improvement; no automated contrast scan was performed. |
| Keyboard navigation | The site already had a skip link, native navigation links and a keyboard-operable mobile-menu button. Removed the inappropriate ARIA menubar role, labelled carousel controls, synchronized slide state, and pause rotation for focus, pointer hover, hidden tabs and reduced-motion preferences. |
| Business details | The site contains a business name, service areas, placeholder phone numbers and a placeholder-looking email address, but no verified legal entity, registration number or street address. They were not invented; the policy pages flag owner verification before launch. |
| Unsubscribe links | No email campaign, mailing list or outbound marketing email implementation was found, so an unsubscribe flow does not apply. Do not add marketing without a separate opt-in and unsubscribe mechanism. |
| Font and image licensing | Added an asset inventory. Google Fonts serves Inter and Fraunces; the upstream font projects use the SIL Open Font License. Unsplash image references are listed, with a reminder to check subject, property and trademark rights. The 40 local web and mobile image files have no creator or license records in the supplied project, so ownership must be confirmed. Bundled Bootstrap, jQuery and validation libraries already include license files. |
| Data deletion requests | The mobile app now lets a signed-in user delete the local account and quote records saved on that device. The website privacy notice gives an email route for access, correction and deletion requests; there is no website self-service deletion or retention schedule. The owner must define request handling and lawful retention before production. |

## Owner actions before launch

- Verify the business's legal name, registration details, physical address, phone and privacy contact address.
- Confirm the API, database, hosting, administrators, backups, security logging, retention period and deletion process. The web API implementation is not included in the supplied archive, so downstream storage and deletion could not be verified. The mobile app stores its account and quote records locally on device.
- Have the responsible business review and approve policy wording and quote/order terms.
- Confirm image listing records and any releases needed for people, property, brands or artwork in the selected photos.
- Replace placeholder content with approved business information and genuine project imagery before presenting the prototype as a live business site.
