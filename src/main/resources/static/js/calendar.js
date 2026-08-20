(function () {
    var DAYS = ["Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"];
    var MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
    var openWrap = null;

    function pad(value) {
        return value < 10 ? "0" + value : String(value);
    }

    function todayDate() {
        var now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), now.getDate());
    }

    function toIso(date) {
        return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
    }

    function parseIso(value) {
        if (!value) {
            return null;
        }
        var parts = String(value).split("-");
        if (parts.length !== 3) {
            return null;
        }
        var date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
        if (isNaN(date.getTime())) {
            return null;
        }
        return date;
    }

    function display(value) {
        var date = parseIso(value);
        if (!date) {
            return "Choose date";
        }
        return pad(date.getDate()) + " " + MONTHS[date.getMonth()].slice(0, 3) + " " + date.getFullYear();
    }

    function boundDate(input, attr, fallbackAttr) {
        if (input.hasAttribute(fallbackAttr)) {
            return todayDate();
        }
        return parseIso(input.getAttribute(attr));
    }

    function inRange(date, min, max) {
        if (min && date < min) {
            return false;
        }
        if (max && date > max) {
            return false;
        }
        return true;
    }

    function closeOpen() {
        if (!openWrap) {
            return;
        }
        var pop = openWrap._erCal;
        var trigger = openWrap.querySelector(".er-cal-trigger");
        if (pop) {
            pop.hidden = true;
        }
        if (trigger) {
            trigger.setAttribute("aria-expanded", "false");
        }
        openWrap.classList.remove("is-open");
        openWrap = null;
    }

    function place(wrap) {
        var pop = wrap._erCal;
        var trigger = wrap.querySelector(".er-cal-trigger");
        if (!pop || !trigger) {
            return;
        }
        var rect = trigger.getBoundingClientRect();
        var compact = wrap.classList.contains("is-compact");
        var width = compact ? 248 : 292;
        var left = Math.min(Math.max(10, rect.left), window.innerWidth - width - 10);
        var below = rect.bottom + 8;
        pop.style.width = width + "px";
        pop.style.left = left + "px";
        pop.style.top = below + "px";
        pop.hidden = false;
        var popRect = pop.getBoundingClientRect();
        if (popRect.bottom > window.innerHeight - 8) {
            var above = rect.top - popRect.height - 8;
            pop.style.top = Math.max(8, above) + "px";
        }
    }

    function renderGrid(wrap, view) {
        var input = wrap.querySelector("input[type='date']");
        var pop = wrap._erCal;
        var grid = pop ? pop.querySelector(".er-cal-grid") : null;
        var label = pop ? pop.querySelector(".er-cal-month") : null;
        var yearSelect = pop ? pop.querySelector(".er-cal-year") : null;
        if (!input || !grid) {
            return;
        }
        var min = boundDate(input, "min", "data-min-today");
        var max = boundDate(input, "max", "data-max-today");
        var selected = parseIso(input.value);
        var year = view.getFullYear();
        var month = view.getMonth();
        if (label) {
            label.textContent = MONTHS[month];
        }
        if (yearSelect && String(yearSelect.value) !== String(year)) {
            yearSelect.value = String(year);
        }
        grid.innerHTML = "";
        DAYS.forEach(function (day) {
            var head = document.createElement("span");
            head.className = "er-cal-dow";
            head.textContent = day;
            grid.appendChild(head);
        });
        var first = new Date(year, month, 1);
        var start = (first.getDay() + 6) % 7;
        var days = new Date(year, month + 1, 0).getDate();
        var today = todayDate();
        var i;
        for (i = 0; i < start; i++) {
            var empty = document.createElement("span");
            empty.className = "er-cal-day is-empty";
            grid.appendChild(empty);
        }
        for (i = 1; i <= days; i++) {
            var date = new Date(year, month, i);
            var btn = document.createElement("button");
            btn.type = "button";
            btn.className = "er-cal-day";
            btn.textContent = String(i);
            if (toIso(date) === toIso(today)) {
                btn.classList.add("is-today");
            }
            if (selected && toIso(date) === toIso(selected)) {
                btn.classList.add("is-selected");
            }
            if (!inRange(date, min, max)) {
                btn.disabled = true;
            }
            btn.addEventListener("click", function (picked) {
                return function () {
                    setValue(wrap, toIso(picked));
                    closeOpen();
                };
            }(date));
            grid.appendChild(btn);
        }
    }

    function fillYears(select, input, view) {
        var min = boundDate(input, "min", "data-min-today");
        var max = boundDate(input, "max", "data-max-today");
        var start = min ? min.getFullYear() : 1900;
        var end = max ? max.getFullYear() : todayDate().getFullYear() + 2;
        select.innerHTML = "";
        var year;
        for (year = end; year >= start; year--) {
            var option = document.createElement("option");
            option.value = String(year);
            option.textContent = String(year);
            select.appendChild(option);
        }
        select.value = String(view.getFullYear());
    }

    function setValue(wrap, value) {
        var input = wrap.querySelector("input[type='date']");
        var trigger = wrap.querySelector(".er-cal-trigger");
        if (!input) {
            return;
        }
        input.value = value || "";
        if (trigger) {
            trigger.textContent = display(input.value);
            trigger.classList.toggle("is-empty", !input.value);
        }
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function destroy(wrap) {
        if (!wrap) {
            return;
        }
        if (openWrap === wrap) {
            closeOpen();
        }
        var pop = wrap._erCal;
        if (pop && pop.parentNode) {
            pop.parentNode.removeChild(pop);
        }
        wrap._erCal = null;
    }

    function teardown(node) {
        if (!node || node.nodeType !== 1) {
            return;
        }
        if (node.classList && node.classList.contains("er-cal-wrap")) {
            destroy(node);
        }
        if (node.querySelectorAll) {
            node.querySelectorAll(".er-cal-wrap").forEach(destroy);
        }
    }

    function enhance(input) {
        if (!input || input.type !== "date" || input.getAttribute("data-er-ready") === "true") {
            return;
        }
        input.setAttribute("data-er-ready", "true");
        var wrap = document.createElement("div");
        wrap.className = "er-cal-wrap";
        if (input.closest(".ca-root") || input.closest(".ca-controls")) {
            wrap.classList.add("is-compact");
        }
        input.parentNode.insertBefore(wrap, input);
        wrap.appendChild(input);
        input.classList.add("er-cal-native");
        input.setAttribute("tabindex", "-1");
        var trigger = document.createElement("button");
        trigger.type = "button";
        trigger.className = "er-cal-trigger" + (input.value ? "" : " is-empty");
        trigger.setAttribute("aria-haspopup", "dialog");
        trigger.setAttribute("aria-expanded", "false");
        trigger.textContent = display(input.value);
        wrap.appendChild(trigger);
        var pop = document.createElement("div");
        pop.className = "er-cal" + (wrap.classList.contains("is-compact") ? " is-compact" : "");
        pop.hidden = true;
        pop.setAttribute("role", "dialog");
        var head = document.createElement("div");
        head.className = "er-cal-head";
        var prev = document.createElement("button");
        prev.type = "button";
        prev.className = "er-cal-nav";
        prev.setAttribute("aria-label", "Previous month");
        prev.textContent = "‹";
        var month = document.createElement("p");
        month.className = "er-cal-month";
        var yearSelect = document.createElement("select");
        yearSelect.className = "er-cal-year";
        var next = document.createElement("button");
        next.type = "button";
        next.className = "er-cal-nav";
        next.setAttribute("aria-label", "Next month");
        next.textContent = "›";
        head.appendChild(prev);
        head.appendChild(month);
        head.appendChild(yearSelect);
        head.appendChild(next);
        var grid = document.createElement("div");
        grid.className = "er-cal-grid";
        pop.appendChild(head);
        pop.appendChild(grid);
        document.body.appendChild(pop);
        wrap._erCal = pop;
        var view = parseIso(input.value) || todayDate();
        fillYears(yearSelect, input, view);
        function paint() {
            renderGrid(wrap, view);
        }
        wrap._erPaint = function () {
            view = parseIso(input.value) || view;
            fillYears(yearSelect, input, view);
            paint();
            trigger.textContent = display(input.value);
            trigger.classList.toggle("is-empty", !input.value);
        };
        prev.addEventListener("click", function () {
            view = new Date(view.getFullYear(), view.getMonth() - 1, 1);
            fillYears(yearSelect, input, view);
            paint();
            place(wrap);
        });
        next.addEventListener("click", function () {
            view = new Date(view.getFullYear(), view.getMonth() + 1, 1);
            fillYears(yearSelect, input, view);
            paint();
            place(wrap);
        });
        yearSelect.addEventListener("change", function () {
            view = new Date(Number(yearSelect.value), view.getMonth(), 1);
            paint();
            place(wrap);
        });
        trigger.addEventListener("click", function () {
            if (openWrap === wrap) {
                closeOpen();
                return;
            }
            closeOpen();
            view = parseIso(input.value) || todayDate();
            fillYears(yearSelect, input, view);
            paint();
            wrap.classList.add("is-open");
            trigger.setAttribute("aria-expanded", "true");
            openWrap = wrap;
            place(wrap);
        });
        input.addEventListener("focus", function () {
            trigger.focus();
        });
        paint();
        input.addEventListener("change", function () {
            trigger.textContent = display(input.value);
            trigger.classList.toggle("is-empty", !input.value);
        });
    }

    function enhanceAll(root) {
        (root || document).querySelectorAll("input[type='date']").forEach(enhance);
    }

    document.addEventListener("mousedown", function (event) {
        if (!openWrap) {
            return;
        }
        var pop = openWrap._erCal;
        if (openWrap.contains(event.target) || (pop && pop.contains(event.target))) {
            return;
        }
        closeOpen();
    });
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
            closeOpen();
        }
    });
    window.addEventListener("resize", closeOpen);
    window.addEventListener("scroll", closeOpen, true);

    window.EverRestCalendar = {
        enhance: enhance,
        enhanceAll: enhanceAll,
        close: closeOpen
    };

    enhanceAll(document);
    if ("MutationObserver" in window && document.body) {
        var observer = new MutationObserver(function (records) {
            records.forEach(function (record) {
                record.addedNodes.forEach(function (node) {
                    if (node.nodeType !== 1) {
                        return;
                    }
                    if (node.matches && node.matches("input[type='date']")) {
                        enhance(node);
                    }
                    if (node.querySelectorAll) {
                        node.querySelectorAll("input[type='date']").forEach(enhance);
                    }
                });
                record.removedNodes.forEach(teardown);
            });
        });
        observer.observe(document.body, { childList: true, subtree: true });
    }
})();
