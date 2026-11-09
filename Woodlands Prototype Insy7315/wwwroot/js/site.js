// Woodlands Designer Boards — vanilla JS interactivity (ported from the React prototype's
// useState/useEffect logic into plain DOM manipulation, since Razor views are server-rendered).

document.addEventListener("DOMContentLoaded", function () {

    // ---------- Mobile nav toggle ----------
    var navToggle = document.getElementById("nav-toggle");
    var mobileMenu = document.getElementById("mobile-menu");
    if (navToggle && mobileMenu) {
        navToggle.addEventListener("click", function () {
            var opened = mobileMenu.classList.toggle("hidden") === false;
            navToggle.setAttribute("aria-expanded", opened ? "true" : "false");
        });
        mobileMenu.addEventListener("click", function (event) {
            if (event.target.closest("a")) {
                mobileMenu.classList.add("hidden");
                navToggle.setAttribute("aria-expanded", "false");
            }
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && !mobileMenu.classList.contains("hidden")) {
                mobileMenu.classList.add("hidden");
                navToggle.setAttribute("aria-expanded", "false");
                navToggle.focus();
            }
        });
    }

    // ---------- Hero carousel ----------
    var slides = document.querySelectorAll("[data-hero-slide]");
    if (slides.length > 1) {
        var current = 0;
        var dots = document.querySelectorAll("[data-hero-dot]");
        var headingBlocks = document.querySelectorAll("[data-hero-copy]");

        function showSlide(idx) {
            slides.forEach(function (s, i) { s.classList.toggle("active", i === idx); });
            dots.forEach(function (d, i) {
                d.classList.toggle("w-6", i === idx);
                d.classList.toggle("bg-tan", i === idx);
                d.classList.toggle("w-2", i !== idx);
                d.classList.toggle("bg-white/40", i !== idx);
            });
            headingBlocks.forEach(function (h, i) {
                h.classList.toggle("hidden", i !== idx);
                h.setAttribute("aria-hidden", i !== idx ? "true" : "false");
            });
            dots.forEach(function (d, i) { d.setAttribute("aria-pressed", i === idx ? "true" : "false"); });
            current = idx;
        }

        function next() { showSlide((current + 1) % slides.length); }
        function prev() { showSlide((current - 1 + slides.length) % slides.length); }

        var reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        var carousel = slides[0].parentElement;
        var timer = null;
        function startTimer() {
            if (!reducedMotion && !timer) timer = setInterval(next, 6000);
        }
        function stopTimer() {
            if (timer) clearInterval(timer);
            timer = null;
        }
        startTimer();
        if (carousel) {
            carousel.addEventListener("mouseenter", stopTimer);
            carousel.addEventListener("mouseleave", startTimer);
            carousel.addEventListener("focusin", stopTimer);
            carousel.addEventListener("focusout", function (event) {
                if (!carousel.contains(event.relatedTarget)) startTimer();
            });
        }

        var nextBtn = document.querySelector("[data-hero-next]");
        var prevBtn = document.querySelector("[data-hero-prev]");
        if (nextBtn) nextBtn.addEventListener("click", function () { stopTimer(); next(); startTimer(); });
        if (prevBtn) prevBtn.addEventListener("click", function () { stopTimer(); prev(); startTimer(); });
        dots.forEach(function (d, i) {
            d.addEventListener("click", function () { stopTimer(); showSlide(i); startTimer(); });
        });
    }

    // ---------- Product detail gallery ----------
    var galleryMain = document.getElementById("gallery-main");
    var galleryThumbs = document.querySelectorAll("[data-gallery-thumb]");
    if (galleryMain && galleryThumbs.length) {
        galleryThumbs.forEach(function (thumb) {
            thumb.addEventListener("click", function () {
                galleryMain.src = thumb.getAttribute("data-src");
                galleryThumbs.forEach(function (t) { t.classList.remove("border-accent"); t.classList.add("border-transparent", "opacity-60"); });
                thumb.classList.add("border-accent");
                thumb.classList.remove("border-transparent", "opacity-60");
            });
        });
    }

    // ---------- FAQ accordion ----------
    document.querySelectorAll("[data-faq-toggle]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            var body = btn.nextElementSibling;
            var chevron = btn.querySelector("[data-faq-chevron]");
            var isOpen = !body.classList.contains("max-h-0");
            document.querySelectorAll("[data-faq-body]").forEach(function (b) {
                b.classList.add("max-h-0");
                b.classList.remove("max-h-96");
            });
            document.querySelectorAll("[data-faq-chevron]").forEach(function (c) { c.classList.remove("rotate-180"); });
            if (!isOpen) {
                body.classList.remove("max-h-0");
                body.classList.add("max-h-96");
                chevron.classList.add("rotate-180");
            }
        });
    });

    // ---------- FAQ category filter ----------
    document.querySelectorAll("[data-faq-filter]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            var cat = btn.getAttribute("data-faq-filter");
            document.querySelectorAll("[data-faq-filter]").forEach(function (b) {
                b.classList.remove("bg-brand-700", "text-white");
                b.classList.add("bg-white", "text-brand-700", "border", "border-brand-700/20");
            });
            btn.classList.add("bg-brand-700", "text-white");
            btn.classList.remove("bg-white", "text-brand-700", "border", "border-brand-700/20");

            document.querySelectorAll("[data-faq-item]").forEach(function (item) {
                var show = cat === "All" || item.getAttribute("data-faq-item") === cat;
                item.classList.toggle("hidden", !show);
            });
        });
    });

});
