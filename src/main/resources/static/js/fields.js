(function () {
    var SELECTOR = "input[type='text'], input[type='email'], input[type='tel'], input[type='password'], input[type='search'], input[type='number'], textarea";

    function isGuest(el) {
        return el && (el.name === "attendees" || el.id === "attendees" || el.getAttribute("data-guest-field") === "true");
    }

    function enhance(el) {
        if (!el || el.nodeType !== 1 || el.getAttribute("data-er-text") === "true") {
            return;
        }
        if (el.classList.contains("er-cal-native") || el.classList.contains("er-cal-year")) {
            return;
        }
        var type = (el.getAttribute("type") || el.type || "").toLowerCase();
        if (type === "checkbox" || type === "radio" || type === "hidden" || type === "file" || type === "date") {
            return;
        }
        el.setAttribute("data-er-text", "true");
        el.classList.add("er-text");
        if (el.tagName === "TEXTAREA") {
            el.classList.add("er-text-area");
        }
        if (el.tagName !== "TEXTAREA" && (el.closest(".ca-root") || el.closest(".ca-controls"))) {
            el.classList.add("is-compact");
        }
        if (isGuest(el)) {
            if (el.tagName === "INPUT" && el.type === "number") {
                el.type = "text";
            }
            el.setAttribute("inputmode", "numeric");
            el.setAttribute("autocomplete", "off");
            el.classList.add("er-text-guests");
        }
    }

    function enhanceAll(root) {
        (root || document).querySelectorAll(SELECTOR).forEach(enhance);
        if (root && root.matches && root.matches(SELECTOR)) {
            enhance(root);
        }
    }

    function observe() {
        if (!("MutationObserver" in window) || !document.body) {
            return;
        }
        var observer = new MutationObserver(function (records) {
            records.forEach(function (record) {
                record.addedNodes.forEach(function (node) {
                    if (node.nodeType !== 1) {
                        return;
                    }
                    enhanceAll(node);
                });
            });
        });
        observer.observe(document.body, { childList: true, subtree: true });
    }

    function start() {
        enhanceAll(document);
        observe();
    }

    document.addEventListener("submit", function (event) {
        var form = event.target;
        if (!form || !form.querySelectorAll) {
            return;
        }
        form.querySelectorAll(".er-text-guests, [data-guest-field='true'], #attendees").forEach(function (el) {
            el.value = String(el.value || "").replace(/[^0-9]/g, "");
        });
    }, true);

    window.EverRestFields = {
        enhance: enhance,
        enhanceAll: enhanceAll
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();
