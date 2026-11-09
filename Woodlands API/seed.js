require('dotenv').config();
const { createClient } = require('@supabase/supabase-js');

const supabase = createClient(process.env.SUPABASE_URL, process.env.SUPABASE_KEY);

 
// HELPERS
 
const jsonList = (arr) => JSON.stringify(arr || []);

// Strip leading "From " from prices (we use is_from_price flag separately)
const cleanPrice = (price) => (price || "").replace(/^From\s+/i, "").trim();
const isFrom = (price) => /^From\s+/i.test(price || "");

 
// PRODUCTS
 
const products = [
    {
        id: "modern-kitchen-suite",
        category: "Kitchen Units",
        title: "Modern Kitchen Suite",
        tagline: "Sleek lines. Enduring quality.",
        description: "Contemporary kitchen units crafted with premium PG Bison melamine boards. Fully customised to your kitchen dimensions and chosen finishes - from handle-less slab doors to classic shaker profiles. Our installation team handles everything from delivery to final fitting.",
        image: "/images/products/Kitchen Unit 12.jpeg",
        gallery: jsonList(["/images/products/Kitchen Unit 12.jpeg", "/images/products/Kitchen Unit 7.jpeg", "/images/products/Kitchen Unit 10.jpeg"]),
        features: jsonList(["Floor-to-ceiling cabinets", "Soft-close hinges & drawers", "Custom island options", "Integrated appliance housing", "15+ colour finishes"]),
        finishes: jsonList(["Arctic White", "Graphite Matt", "Woodgrain Oak", "Concrete Grey", "Gloss White"]),
        lead_time: "2-4 weeks",
        tag: "Popular",
        price: cleanPrice("From R8,500"),
        is_from_price: isFrom("From R8,500")
    },
    {
        id: "curved-luxury-kitchen",
        category: "Kitchen Units",
        title: "Curved Luxury Kitchen",
        tagline: "Flowing forms, flawless finish.",
        description: "Curved kitchen cabinetry that redefines what is possible with PG Bison board materials. CNC precision-cutting allows us to achieve gentle arcs and radius profiles that transform a kitchen into a design centrepiece. Available in any PG Bison melamine colour.",
        image: "/images/products/Kitchen Unit 6.jpeg",
        gallery: jsonList(["/images/products/Kitchen Unit 6.jpeg", "/images/products/Kitchen Unit 9.jpeg", "/images/products/Kitchen Unit 11.jpeg"]),
        features: jsonList(["Curved radius profiles", "Integrated appliance housing", "Marble-effect finishes", "LED under-cabinet lighting", "Bespoke island designs"]),
        finishes: jsonList(["Pearl White", "Warm Linen", "Deep Navy", "Sage Green", "Terrazzo Effect"]),
        lead_time: "3-5 weeks",
        tag: null,
        price: cleanPrice("From R14,000"),
        is_from_price: isFrom("From R14,000")
    },
    {
        id: "compact-galley-kitchen",
        category: "Kitchen Units",
        title: "Compact Galley Kitchen",
        tagline: "Maximum storage, minimal footprint.",
        description: "Purpose-built for narrow or apartment kitchens. Every centimetre is optimised - pull-out pantry columns, slim-line overhead units, and deep base drawers eliminate wasted space without sacrificing style.",
        image: "/images/products/Kitchen Unit 4.jpeg",
        gallery: jsonList(["/images/products/Kitchen Unit 4.jpeg", "/images/products/Kitchen Unit 3.jpeg", "/images/products/Kitchen 2.jpeg"]),
        features: jsonList(["Pull-out pantry columns", "Overhead storage units", "Deep base drawers", "Corner carousel units", "Space-saving design"]),
        finishes: jsonList(["White Gloss", "Light Oak", "Anthracite", "Soft Grey"]),
        lead_time: "2-3 weeks",
        tag: null,
        price: cleanPrice("From R6,200"),
        is_from_price: isFrom("From R6,200")
    },
    {
        id: "wall-mounted-tv-unit",
        category: "TV Stands",
        title: "Wall-Mounted TV Unit",
        tagline: "Float it. Flaunt it.",
        description: "Floating TV units that create a clean, contemporary look with integrated cable management channels so no wires are ever visible. Specify your TV size and we engineer the unit to match - including the correct wall-mounting substrate.",
        image: "/images/products/Floating TV-1.jpeg",
        gallery: jsonList(["/images/products/Floating TV-1.jpeg", "/images/products/Floating TV-2.jpeg", "/images/products/Floating TV-4.jpeg"]),
        features: jsonList(["Hidden cable management", "Wall-mounted / floating", "Open + closed storage", "Custom width up to 3m", "LED strip options"]),
        finishes: jsonList(["White Matt", "Smoked Oak", "Charcoal", "Walnut Effect", "Bianco"]),
        lead_time: "1-2 weeks",
        tag: "New",
        price: cleanPrice("From R3,800"),
        is_from_price: isFrom("From R3,800")
    },
    {
        id: "entertainment-console",
        category: "TV Stands",
        title: "Entertainment Console",
        tagline: "Storage that works as hard as you play.",
        description: "A floor-standing entertainment centre with deep media shelves, adjustable compartments, and a mix of doors and open bays. Designed to house sound equipment, streaming devices, gaming consoles, and decor in one cohesive unit.",
        image: "/images/products/Floating TV-8.jpeg",
        gallery: jsonList(["/images/products/Floating TV-8.jpeg", "/images/products/Floating TV-9.jpeg", "/images/products/Floating TV-10.jpeg", "/images/products/Floating TV-5.jpeg"]),
        features: jsonList(["Deep media shelves", "Adjustable compartments", "Door + open bay combo", "Matching wall panels", "Base plinth or leg options"]),
        finishes: jsonList(["Arctic White", "Teak Effect", "Black Matt", "Light Grey"]),
        lead_time: "1-2 weeks",
        tag: null,
        price: cleanPrice("From R2,900"),
        is_from_price: isFrom("From R2,900")
    },
    {
        id: "full-length-wardrobe",
        category: "Built-In Cupboards",
        title: "Full-Length Wardrobe",
        tagline: "Every centimetre, perfectly used.",
        description: "Floor-to-ceiling built-in wardrobes that make the most of your bedroom height. Choose sliding or hinged doors, full-length mirrors, internal drawer systems, and a mix of hanging and shelving zones. Installed by our own team with minimal disruption.",
        image: "https://images.unsplash.com/photo-1720087448033-db1904db294b?w=800&h=600&fit=crop&auto=format",
        gallery: jsonList([
            "https://images.unsplash.com/photo-1720087448033-db1904db294b?w=800&h=600&fit=crop&auto=format",
            "https://images.unsplash.com/photo-1721738857328-3987700892e7?w=800&h=600&fit=crop&auto=format",
            "https://images.unsplash.com/photo-1721742604074-8e998cee43f2?w=800&h=600&fit=crop&auto=format"
        ]),
        features: jsonList(["Full-length mirror options", "Internal drawer systems", "Hanging + shelving zones", "Soft-close doors", "Floor-to-ceiling height"]),
        finishes: jsonList(["White", "Sand", "Pewter", "Cashmere", "Stone Grey"]),
        lead_time: "1-2 weeks",
        tag: "Popular",
        price: cleanPrice("From R4,500"),
        is_from_price: isFrom("From R4,500")
    },
    {
        id: "bedroom-suite-storage",
        category: "Built-In Cupboards",
        title: "Bedroom Suite Storage",
        tagline: "Coordinated. Considered. Complete.",
        description: "A fully coordinated bedroom storage solution combining open display shelving, closed cupboards, and drawer pedestals in a unified design. Pair with a matching headboard panel for a truly bespoke bedroom suite.",
        image: "https://images.unsplash.com/photo-1721738857328-3987700892e7?w=800&h=600&fit=crop&auto=format",
        gallery: jsonList([
            "https://images.unsplash.com/photo-1721738857328-3987700892e7?w=800&h=600&fit=crop&auto=format",
            "https://images.unsplash.com/photo-1720087448033-db1904db294b?w=800&h=600&fit=crop&auto=format"
        ]),
        features: jsonList(["Modular configuration", "Integrated LED lighting", "Bedside pedestals", "Headboard panel option", "Premium handles & ironmongery"]),
        finishes: jsonList(["Linen", "Dove White", "Dusty Rose", "Sage", "Midnight Blue"]),
        lead_time: "2-3 weeks",
        tag: null,
        price: cleanPrice("From R6,800"),
        is_from_price: isFrom("From R6,800")
    },
    {
        id: "precision-board-cutting",
        category: "Cutting & Edging",
        title: "Precision Board Cutting",
        tagline: "Your measurements. Our precision.",
        description: "CNC-precision cutting of any PG Bison chipboard, MDF, or melamine board to your exact specifications. Ideal for contractors, cabinet-makers, and advanced DIY builders. Supply your cut-list and we do the rest - often same day.",
        image: "https://images.unsplash.com/photo-1659930087003-2d64e33181f7?w=800&h=600&fit=crop&auto=format",
        gallery: jsonList([
            "https://images.unsplash.com/photo-1659930087003-2d64e33181f7?w=800&h=600&fit=crop&auto=format",
            "https://images.unsplash.com/photo-1497219055242-93359eeed651?w=800&h=600&fit=crop&auto=format"
        ]),
        features: jsonList(["+/-0.5mm tolerance", "All PG Bison board ranges", "Same-day turnaround (bulk orders)", "Digital cut-list accepted", "Bulk discount pricing"]),
        finishes: jsonList(["All PG Bison melamine colours", "Raw chipboard", "MDF", "Supawood"]),
        lead_time: "Same day - 2 days",
        tag: null,
        price: cleanPrice("From R18/cut"),
        is_from_price: isFrom("From R18/cut")
    },
    {
        id: "edge-banding-finishing",
        category: "Cutting & Edging",
        title: "Edge Banding & Finishing",
        tagline: "The detail that defines the finish.",
        description: "Professional PVC and ABS edge banding applied with a hot-melt adhesive press and trimmed flush - colour-matched from the full PG Bison ABS edging range. Available on any board thickness from 16mm to 38mm.",
        image: "https://images.unsplash.com/photo-1497219055242-93359eeed651?w=800&h=600&fit=crop&auto=format",
        gallery: jsonList([
            "https://images.unsplash.com/photo-1497219055242-93359eeed651?w=800&h=600&fit=crop&auto=format",
            "https://images.unsplash.com/photo-1659930087003-2d64e33181f7?w=800&h=600&fit=crop&auto=format"
        ]),
        features: jsonList(["PVC & ABS edging", "Full colour-match range", "Flush trim finish", "16mm-38mm boards", "Bulk pricing available"]),
        finishes: jsonList(["Matched to all PG Bison decors", "Contrasting accent options"]),
        lead_time: "Same day - 2 days",
        tag: null,
        price: cleanPrice("From R8/linear metre"),
        is_from_price: isFrom("From R8/linear metre")
    }
];

 
// TESTIMONIALS
 
const testimonials = [
    { name: "Thabo Mokoena", role: "Homeowner", location: "Soweto", rating: 5, project: "Full Kitchen Renovation", review: "Woodlands did my entire kitchen from scratch. The finish on the PG Bison boards is absolutely stunning - my neighbours keep asking who did it. Professional team, on time, and within budget." },
    { name: "Priya Naidoo", role: "Interior Designer", location: "Johannesburg", rating: 5, project: "Multiple Residential Projects", review: "As a designer, I'm very particular about material quality and precision. Woodlands consistently delivers flawless cuts and edge-banding that make my clients' projects look polished. My go-to supplier." },
    { name: "Lebo Sithole", role: "Building Contractor", location: "Randfontein", rating: 5, project: "Bulk Cutting & Edging", review: "I've used the Randfontein branch for over three years. Bulk cutting orders are ready same day, and the edge-banding quality is consistent every single time. Reliable partner for any contractor." },
    { name: "Sandra Van Wyk", role: "Homeowner", location: "Roodepoort", rating: 5, project: "Built-In Bedroom Cupboards", review: "My built-in cupboards look like they came straight out of a magazine. The team measured twice, cut once, and the installation was immaculate. I especially love the soft-close doors - such a premium touch." },
    { name: "Mpho Dlamini", role: "Property Developer", location: "Gauteng", rating: 5, project: "Multi-Unit Development", review: "We fitted 12 units for a new development using Woodlands. Three branches made logistics easy - we split orders between Soweto and Roodepoort and still got everything on schedule. Quality always top-notch." },
    { name: "Anita Joubert", role: "Homeowner", location: "Soweto", rating: 4, project: "Custom TV Unit", review: "The TV unit they built for my lounge is exactly what I had in mind - floating, clean lines, with enough storage for all our media equipment. Good communication throughout. Very satisfied." },
    { name: "Kagiso Nkosi", role: "Architect", location: "Johannesburg", rating: 5, project: "High-End Kitchen Suite", review: "Specified Woodlands for a high-end residential project. The CNC precision on the kitchen cabinets was exceptional - tolerances I'd struggle to find elsewhere at this price point. Will specify again." },
    { name: "Fatima Essack", role: "Homeowner", location: "Roodepoort", rating: 5, project: "Kitchen Units + Island", review: "From the initial quote to the final installation, the process was smooth and professional. The team cleaned up completely after themselves. The kitchen is everything I dreamed of." },
    { name: "Deon Pretorius", role: "DIY Builder", location: "Randfontein", rating: 5, project: "Repeat Cutting & Edging Orders", review: "I build my own cabinets and Woodlands does all my cutting and edging. Same-day service on most orders, and the cut precision is spot on every time. Best cutting service in the West Rand." }
];

 
// SERVICES
 
const services = [
    { name: "Kitchen Units", description: "Custom kitchen cabinetry built to your exact specs.", image: "", is_active: true },
    { name: "TV Stands", description: "Wall-mounted and floor-standing entertainment units.", image: "", is_active: true },
    { name: "Built-In Cupboards", description: "Floor-to-ceiling wardrobes and storage.", image: "", is_active: true },
    { name: "Cutting & Edging", description: "CNC cutting and edge banding.", image: "", is_active: true },
    { name: "General Enquiry", description: "Anything else", image: "", is_active: true }
];

 
// FAQS
 
const faqs = [
    { category: "Materials", question: "What is PG Bison and why does it matter?", answer: "PG Bison is South Africa's leading manufacturer of timber-based board products - melamine, chipboard, MDF, and Supawood. Using PG Bison materials means your units meet the highest local standards for strength, durability, and finish quality. As an authorised partner, we guarantee every board is 100% genuine." },
    { category: "Process", question: "Do you offer a measuring and design service?", answer: "Yes. We offer a free consultation and site measurement for all custom built-in units. A team member will visit your space, take precise measurements, suggest the best material and finish options, and provide a detailed quote before any work begins." },
    { category: "Services", question: "What services do you offer besides custom units?", answer: "In addition to full kitchen units, TV stands, and built-in cupboards, we provide precision CNC board cutting and professional edge-banding services. Contractors and DIY builders who just need boards cut to size or edged are welcome - often with same-day turnaround." },
    { category: "Process", question: "How long does a custom kitchen or cupboard take?", answer: "Lead times depend on complexity. A standard built-in cupboard typically takes 5-10 business days from confirmed order to installation. A full kitchen suite may take 2-4 weeks. You'll receive a firm delivery timeline in your quote." },
    { category: "Materials", question: "What colour and finish options are available?", answer: "We stock the full PG Bison Melamine range - over 70 colours and textures from solid whites and greys to wood-grain effects and high-gloss finishes. Special-order finishes from the PG Bison catalogue are also available upon request." },
    { category: "Installation", question: "Do you install the units or just supply them?", answer: "We do both. Choose a supply-only option (ideal for contractors with their own installation teams) or a full supply-and-install service. All installation is carried out by our own trained team with proper tools and fixings." },
    { category: "Branches", question: "Which branch should I visit?", answer: "We have three branches: Soweto, Roodepoort, and Randfontein. Visit whichever is closest to your project site. All three branches stock the same product ranges and offer the same services. You can also call or WhatsApp us to start your quote remotely." },
    { category: "Trade", question: "Do you work with interior designers and contractors?", answer: "Absolutely. A significant portion of our work comes from interior designers and building contractors. We offer trade pricing, bulk-order discounts, and dedicated account management for regular clients." },
    { category: "Services", question: "Is there a minimum order for cutting and edging?", answer: "There is no strict minimum for walk-in customers - we can cut and edge a single sheet if needed. For bulk orders (10+ sheets), we offer discounted pricing and prioritised turnaround. Contact your nearest branch for current bulk pricing." },
    { category: "Warranty", question: "What warranty do you offer?", answer: "All custom-built units come with a 12-month workmanship warranty covering any defects in construction or installation. PG Bison board materials carry their own manufacturer warranty for structural integrity. We're committed to making it right if anything falls short." }
];

 
// BRANCHES
 
const branches = [
    { name: "Soweto", region: "Soweto", phone: "+27 11 xxx xxxx", hours: "Mon-Fri: 8am-5pm", notes: "Main branch", address: "Soweto, Johannesburg, Gauteng" },
    { name: "Roodepoort", region: "Roodepoort", phone: "+27 11 xxx xxxx", hours: "Mon-Fri: 8am-5pm", notes: "Branch office", address: "Roodepoort, Johannesburg, Gauteng" },
    { name: "Randfontein", region: "Randfontein", phone: "+27 11 xxx xxxx", hours: "Mon-Fri: 8am-5pm", notes: "Branch office", address: "Randfontein, Gauteng" }
];

 
// MOCK QUOTE REQUESTS (from DashboardMockData)
 
const quoteRequests = [
    { quote_code: "QT-0041", first_name: "Thabo", last_name: "Mokoena",  email: "thabo@example.com",       phone: "071 234 5678", branch: "Soweto",      service: "Kitchen Units",      message: "Full kitchen renovation",        status: "Completed",   value: "R18 500" },
    { quote_code: "QT-0040", first_name: "Sandra", last_name: "Van Wyk", email: "sandra@example.com",      phone: "082 555 1234", branch: "Roodepoort",  service: "Built-In Cupboards", message: "Bedroom cupboards",              status: "In Progress", value: "R9 200" },
    { quote_code: "QT-0039", first_name: "Mpho",   last_name: "Dlamini", email: "mpho@example.com",        phone: "083 456 7890", branch: "Soweto",      service: "TV Stand",           message: "Modern floating TV unit",        status: "Pending",     value: "R4 800" },
    { quote_code: "QT-0038", first_name: "Anita",  last_name: "Joubert", email: "anita@example.com",       phone: "084 222 9911", branch: "Randfontein", service: "Cutting & Edging",   message: "Cutting list for 5 sheets",      status: "Completed",   value: "R1 650" },
    { quote_code: "QT-0037", first_name: "Lebo",   last_name: "Sithole", email: "lebo@example.com",        phone: "072 333 4455", branch: "Randfontein", service: "Kitchen Units",      message: "Bulk kitchen cabinets",          status: "Cancelled",   value: "R22 000" },
    { quote_code: "QT-0036", first_name: "Priya",  last_name: "Naidoo",  email: "priya@example.com",       phone: "073 888 2200", branch: "Roodepoort",  service: "Built-In Cupboards", message: "Multi-unit development",         status: "In Progress", value: "R13 400" },
    { quote_code: "QT-0035", first_name: "Kagiso", last_name: "Sithole", email: "kagiso@example.com",      phone: "082 111 2222", branch: "Soweto",      service: "TV Stand",           message: "Entertainment console",          status: "Pending",     value: "R6 100" },
    { quote_code: "QT-0032", first_name: "Nomsa",  last_name: "Dube",    email: "nomsa@example.com",       phone: "071 999 8888", branch: "Soweto",      service: "Kitchen Units",      message: "Compact galley kitchen",         status: "In Progress", value: "R22 400" },
    { quote_code: "QT-0029", first_name: "Bongani",last_name: "Zulu",    email: "bongani@example.com",     phone: "060 333 4444", branch: "Soweto",      service: "Built-In Cupboards", message: "Full-length wardrobe",           status: "Completed",   value: "R11 200" }
];

 
// PROTOTYPE ACCOUNTS
 
const accounts = [
    { email: "admin@woodlandsdb.co.za",        password: "admin123",    full_name: "Admin User",          role: "Admin",                 branch: null },
    { email: "soweto@woodlandsdb.co.za",       password: "manager123",  full_name: "Soweto Manager",      role: "Manager (Soweto)",      branch: "Soweto" },
    { email: "roodepoort@woodlandsdb.co.za",   password: "manager123",  full_name: "Roodepoort Manager",  role: "Manager (Roodepoort)",  branch: "Roodepoort" },
    { email: "randfontein@woodlandsdb.co.za",  password: "manager123",  full_name: "Randfontein Manager", role: "Manager (Randfontein)", branch: "Randfontein" },
    { email: "customer@example.com",           password: "customer123", full_name: "Prototype Customer",  role: "Customer",              branch: null }
];

 
// SEEDING LOGIC
 

async function clearTable(table) {
    const { error } = await supabase
        .from(table)
        .delete()
        .neq('id', '00000000-0000-0000-0000-000000000000');
    if (error) console.warn(`  [!] Could not clear ${table}: ${error.message}`);
}

async function seedTable(table, rows, label, { reset = false } = {}) {
    console.log(`\n>> Seeding ${label}...`);

    if (reset) {
        await clearTable(table);
    } else {
        const { data: existing } = await supabase.from(table).select('id').limit(1);
        if (existing && existing.length > 0) {
            console.log(`  [skip] ${label} already has data - use --reset to wipe and reseed.`);
            return;
        }
    }

    const { error } = await supabase.from(table).insert(rows);
    if (error) {
        console.error(`  [FAIL] ${label}: ${error.message}`);
    } else {
        console.log(`  [ok] Seeded ${rows.length} ${label}.`);
    }
}

async function seedPrototypeUsers({ reset = false } = {}) {
    console.log(`\n>> Seeding prototype accounts...`);

    for (const acct of accounts) {
        const { data: existing } = await supabase
            .from('app_users')
            .select('id')
            .eq('email', acct.email)
            .limit(1);

        if (existing && existing.length > 0) {
            if (reset) {
                await supabase.from('app_users').delete().eq('email', acct.email);
            } else {
                console.log(`  [skip] ${acct.email} already exists.`);
                continue;
            }
        }

        const { data: authData, error: authError } = await supabase.auth.admin.createUser({
            email: acct.email,
            password: acct.password,
            email_confirm: true
        });

        let userId;
        if (authError) {
            const { data: list } = await supabase.auth.admin.listUsers();
            const found = list?.users?.find(u => u.email === acct.email);
            if (!found) {
                console.error(`  [FAIL] Auth failed for ${acct.email}: ${authError.message}`);
                continue;
            }
            userId = found.id;
        } else {
            userId = authData.user.id;
        }

        const { error: profileError } = await supabase.from('app_users').insert([{
            id: userId,
            full_name: acct.full_name,
            email: acct.email,
            phone: "",
            role: acct.role,
            branch: acct.branch,
            active: true
        }]);

        if (profileError) {
            console.error(`  [FAIL] Profile for ${acct.email}: ${profileError.message}`);
        } else {
            console.log(`  [ok] Seeded ${acct.email} (${acct.role})`);
        }
    }
}

 
// MAIN
 
async function run() {
    const reset = process.argv.includes('--reset');
    console.log(`Woodlands database seed${reset ? ' (RESET MODE)' : ''}\n${'='.repeat(50)}`);

    await seedTable('products', products, 'products', { reset });
    await seedTable('testimonials', testimonials, 'testimonials', { reset });
    await seedTable('services', services, 'services', { reset });
    await seedTable('faqs', faqs, 'faqs', { reset });
    await seedTable('branches', branches, 'branches', { reset });
    await seedTable('quote_requests', quoteRequests, 'quote requests', { reset });
    await seedPrototypeUsers({ reset });

    console.log("\nSeeding complete.\n");
    process.exit(0);
}

run().catch(err => {
    console.error("Fatal error during seeding:", err);
    process.exit(1);
});