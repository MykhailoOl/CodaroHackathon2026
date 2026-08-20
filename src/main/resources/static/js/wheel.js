(function () {
    var overlay = document.getElementById("wheel-overlay");
    var wheelEl = document.getElementById("overlay-wheel");
    var copyEl = document.getElementById("wheel-overlay-copy");
    var statusEl = document.getElementById("wheel-overlay-status");
    var resultEl = document.getElementById("wheel-overlay-result");
    var actionsEl = document.getElementById("wheel-overlay-actions");
    var closeBtn = document.getElementById("wheel-overlay-close");
    var retryBtn = document.getElementById("wheel-overlay-retry");
    var historyLink = document.getElementById("wheel-overlay-history");
    if (!overlay || !wheelEl) {
        return;
    }
    var COLORS = ["#2F6FED", "#7B2D8E", "#0F8B8D", "#8B1E3F", "#D4A017", "#E05A4F"];
    var busy = false;
    var open = false;
    var settled = false;
    var lastFocus = null;
    var candidates = [];
    var onRetry = null;
    var onClose = null;
    var timer = null;

    function reduced() {
        return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    }

    function formatDate(value) {
        var parts = String(value).split("-");
        if (parts.length !== 3) {
            return value;
        }
        return parts[2] + "." + parts[1];
    }

    function paint(dates, winner) {
        wheelEl.innerHTML = "";
        wheelEl.style.transition = "none";
        wheelEl.style.transform = "rotate(0deg)";
        var count = dates.length;
        if (!count) {
            wheelEl.style.background = COLORS[0];
            return;
        }
        var slice = 360 / count;
        var stops = dates.map(function (_, index) {
            return COLORS[index % COLORS.length] + " " + (index * slice) + "deg " + ((index + 1) * slice) + "deg";
        });
        wheelEl.style.background = "conic-gradient(" + stops.join(",") + ")";
        dates.forEach(function (date, index) {
            var label = document.createElement("span");
            label.className = "wheel-slice-label";
            if (winner && String(date) === String(winner)) {
                label.classList.add("is-winner");
            }
            var angle = slice * index + slice / 2;
            label.style.transform = "rotate(" + angle + "deg) translate(0, -118px) rotate(" + (-angle) + "deg)";
            label.textContent = formatDate(date);
            wheelEl.appendChild(label);
        });
    }

    function focusables() {
        return Array.prototype.slice.call(overlay.querySelectorAll("a[href], button:not([disabled]), [tabindex]:not([tabindex='-1'])"))
            .filter(function (el) {
                return !el.hidden && el.offsetParent !== null;
            });
    }

    function trap(event) {
        if (!open || event.key !== "Tab") {
            return;
        }
        var items = focusables();
        if (!items.length) {
            event.preventDefault();
            overlay.focus();
            return;
        }
        var first = items[0];
        var last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    function hideActions() {
        actionsEl.hidden = true;
        retryBtn.hidden = true;
        resultEl.hidden = true;
        resultEl.textContent = "";
        statusEl.hidden = false;
        statusEl.textContent = "Finding an available date…";
    }

    function openOverlay(options) {
        options = options || {};
        lastFocus = document.activeElement;
        onRetry = options.onRetry || null;
        onClose = options.onClose || null;
        if (options.historyUrl) {
            historyLink.setAttribute("href", options.historyUrl);
        }
        copyEl.textContent = options.copy || "Your available ceremony date is being assigned.";
        hideActions();
        candidates = [];
        paint(["—", "—", "—", "—", "—", "—"], null);
        overlay.hidden = false;
        overlay.setAttribute("aria-hidden", "false");
        overlay.setAttribute("tabindex", "-1");
        document.body.classList.add("wheel-open");
        busy = true;
        settled = false;
        open = true;
        overlay.focus();
    }

    function finish() {
        busy = false;
        settled = true;
        var items = focusables();
        if (items.length) {
            items[0].focus();
        }
    }

    function closeOverlay() {
        if (busy) {
            return;
        }
        if (timer) {
            window.clearTimeout(timer);
            timer = null;
        }
        overlay.hidden = true;
        overlay.setAttribute("aria-hidden", "true");
        document.body.classList.remove("wheel-open");
        open = false;
        busy = false;
        var restore = lastFocus;
        lastFocus = null;
        var cb = onClose;
        onClose = null;
        onRetry = null;
        if (restore && typeof restore.focus === "function") {
            restore.focus();
        }
        if (typeof cb === "function") {
            cb();
        }
    }

    function dismissOverlay() {
        busy = false;
        closeOverlay();
    }

    window.EverRestWheel = {
        open: openOverlay,
        setCandidates: function (dates) {
            candidates = (dates || []).map(String);
            if (candidates.length) {
                paint(candidates, null);
            }
        },
        reveal: function (data) {
            var winner = String(data.startAt).slice(0, 10);
            var dates = (data.dates || candidates || []).map(String);
            if (dates.indexOf(winner) < 0) {
                dates = dates.concat([winner]);
            }
            if (!dates.length) {
                dates = [winner];
            }
            candidates = dates;
            paint(dates, winner);
            var count = dates.length;
            var slice = 360 / count;
            var index = dates.indexOf(winner);
            if (index < 0) {
                index = 0;
            }
            var reduce = reduced();
            var rotation = 360 * (reduce ? 0 : 6) + (360 - (index * slice + slice / 2));
            requestAnimationFrame(function () {
                wheelEl.style.transition = reduce ? "none" : "transform 2.8s cubic-bezier(0.12, 0.7, 0.16, 1)";
                wheelEl.style.transform = "rotate(" + rotation + "deg)";
            });
            var delay = reduce ? 0 : 2900;
            timer = window.setTimeout(function () {
                statusEl.hidden = true;
                resultEl.hidden = false;
                resultEl.textContent = "Assigned " + String(data.startAt).replace("T", " ").slice(0, 16)
                    + " · " + data.formattedAmount
                    + " · " + data.status
                    + " · reservation #" + data.id;
                actionsEl.hidden = false;
                retryBtn.hidden = true;
                finish();
            }, delay);
        },
        fail: function (message, canRetry) {
            statusEl.hidden = false;
            statusEl.textContent = message || "No ceremony times are free right now.";
            resultEl.hidden = true;
            actionsEl.hidden = false;
            retryBtn.hidden = !canRetry;
            finish();
        },
        close: closeOverlay,
        dismiss: dismissOverlay,
        isBusy: function () {
            return busy;
        },
        isOpen: function () {
            return open;
        }
    };

    closeBtn.addEventListener("click", closeOverlay);
    retryBtn.addEventListener("click", function () {
        if (typeof onRetry === "function") {
            var retry = onRetry;
            hideActions();
            busy = true;
            settled = false;
            retry();
        }
    });
    document.addEventListener("keydown", function (event) {
        if (!open) {
            return;
        }
        if (event.key === "Escape") {
            if (!busy) {
                closeOverlay();
            }
            return;
        }
        trap(event);
    });
})();
