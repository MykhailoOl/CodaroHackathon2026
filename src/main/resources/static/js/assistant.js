(function () {
    var root = document.getElementById("everrest-assistant");
    if (!root) {
        return;
    }
    var launcher = document.getElementById("ca-launcher");
    var panel = document.getElementById("ca-panel");
    var logEl = document.getElementById("ca-log");
    var controls = document.getElementById("ca-controls");
    var progress = document.getElementById("ca-progress");
    var closeBtn = document.getElementById("ca-close");
    var restartBtn = document.getElementById("ca-restart");
    var head = document.getElementById("ca-head");
    var STORE = "everrest-evelyn";
    var TOKEN_KEY = "everrest-evelyn-submit";
    var STEPS = ["home", "venue", "service", "pack", "deceased", "attendees", "phone", "extras", "payment", "note", "review", "success"];
    var authenticated = root.getAttribute("data-authenticated") === "true";
    var phoneRequired = root.getAttribute("data-phone-required") === "true";
    var loginUrl = root.getAttribute("data-login-url") || "/login";
    var historyUrl = root.getAttribute("data-history-url") || "/reservations";
    var noticesUrl = root.getAttribute("data-notices-url") || "/notifications";
    var userId = root.getAttribute("data-user-id") || "";
    var draft = emptyDraft();
    var catalog = { homes: [], venues: [], venue: null, extras: [], preview: null, created: null };
    var assigning = false;

    function emptyDraft() {
        return {
            step: "home",
            homeId: null,
            venueId: null,
            serviceType: "",
            funeralPackage: "",
            deceasedFullName: "",
            dateOfBirth: "",
            dateOfDeath: "",
            attendees: 1,
            phone: "",
            extraIds: [],
            paymentMethod: "CASH",
            note: ""
        };
    }

    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var headers = { "Accept": "application/json", "Content-Type": "application/json" };
        if (token && header) {
            headers[header.getAttribute("content")] = token.getAttribute("content");
        }
        return headers;
    }

    function loadStore() {
        try {
            return JSON.parse(localStorage.getItem(STORE) || "{}");
        } catch (e) {
            return {};
        }
    }

    function saveStore(patch) {
        var current = loadStore();
        Object.keys(patch).forEach(function (key) {
            current[key] = patch[key];
        });
        localStorage.setItem(STORE, JSON.stringify(current));
    }

    function persistSafe() {
        saveStore({
            open: !panel.hidden,
            x: root.style.left || "",
            y: root.style.top || "",
            userId: userId,
            homeId: draft.homeId,
            venueId: draft.venueId,
            serviceType: draft.serviceType,
            funeralPackage: draft.funeralPackage,
            extraIds: draft.extraIds,
            paymentMethod: draft.paymentMethod,
            attendees: draft.attendees,
            step: draft.step === "deceased" || draft.step === "phone" || draft.step === "note" ? "home" : draft.step
        });
    }

    function restoreSafe() {
        var stored = loadStore();
        if (stored.x && stored.y) {
            root.style.left = stored.x;
            root.style.top = stored.y;
            root.style.right = "auto";
            root.style.bottom = "auto";
        }
        if (String(stored.userId || "") !== String(userId)) {
            return;
        }
        draft.homeId = stored.homeId || null;
        draft.venueId = stored.venueId || null;
        draft.serviceType = stored.serviceType || "";
        draft.funeralPackage = stored.funeralPackage || "";
        draft.extraIds = stored.extraIds || [];
        draft.paymentMethod = stored.paymentMethod || "CASH";
        draft.attendees = stored.attendees || 1;
        if (stored.step && STEPS.indexOf(stored.step) >= 0 && stored.step !== "deceased" && stored.step !== "phone" && stored.step !== "note" && stored.step !== "success" && stored.step !== "wheel") {
            draft.step = stored.step;
        }
    }

    function inspectStorage() {
        var raw = localStorage.getItem(STORE) || "";
        return raw;
    }
    window.__everrestAssistantStorage = inspectStorage;

    function addBot(text) {
        var p = document.createElement("p");
        p.className = "ca-bot";
        p.textContent = text;
        logEl.appendChild(p);
        logEl.scrollTop = logEl.scrollHeight;
    }

    function addUser(text) {
        var p = document.createElement("p");
        p.className = "ca-user";
        p.textContent = text;
        logEl.appendChild(p);
        logEl.scrollTop = logEl.scrollHeight;
    }

    function clearControls() {
        controls.innerHTML = "";
    }

    function button(label, selected, onClick) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "ca-card-btn" + (selected ? " is-selected" : "");
        btn.textContent = label;
        btn.addEventListener("click", onClick);
        return btn;
    }

    function focusFirstControl() {
        var el = controls.querySelector("input, select, textarea, button");
        if (el && typeof el.focus === "function") {
            el.focus();
        }
    }

    function api(path, options) {
        return fetch("/api/reservation-assistant" + path, Object.assign({
            credentials: "same-origin",
            headers: csrfHeaders()
        }, options || {})).then(function (response) {
            return response.json().then(function (data) {
                if (response.status === 401) {
                    var unauth = new Error(data.message || "Sign in to arrange a ceremony.");
                    unauth.code = "UNAUTHENTICATED";
                    throw unauth;
                }
                if (!response.ok) {
                    var error = new Error(data.message || "That arrangement could not be completed.");
                    error.code = data.code || "VALIDATION";
                    error.field = data.field;
                    error.step = data.step;
                    throw error;
                }
                return data;
            });
        });
    }

    function newToken() {
        if (window.crypto && crypto.randomUUID) {
            return crypto.randomUUID();
        }
        return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
            var r = Math.random() * 16 | 0;
            var v = c === "x" ? r : (r & 0x3 | 0x8);
            return v.toString(16);
        });
    }

    function submissionToken() {
        var existing = sessionStorage.getItem(TOKEN_KEY);
        if (existing) {
            return existing;
        }
        var created = newToken();
        sessionStorage.setItem(TOKEN_KEY, created);
        return created;
    }

    function resetToken() {
        sessionStorage.removeItem(TOKEN_KEY);
    }

    function requestBody() {
        return {
            venueId: draft.venueId,
            serviceType: draft.serviceType,
            funeralPackage: draft.funeralPackage,
            deceasedFullName: draft.deceasedFullName,
            dateOfBirth: draft.dateOfBirth || null,
            dateOfDeath: draft.dateOfDeath,
            attendees: Number(draft.attendees),
            phone: draft.phone || null,
            extraIds: draft.extraIds,
            paymentMethod: draft.paymentMethod,
            note: draft.note || null,
            bookingSource: "CHAT_ASSISTANT",
            submissionToken: submissionToken()
        };
    }

    function firstMissing() {
        if (!draft.homeId) {
            return { step: "home", message: "Choose a funeral home." };
        }
        if (!draft.venueId) {
            return { step: "venue", message: "Choose a venue." };
        }
        if (!draft.serviceType) {
            return { step: "service", message: "Choose a ceremony type." };
        }
        if (!draft.funeralPackage) {
            return { step: "pack", message: "Choose a package." };
        }
        if (!draft.deceasedFullName || !String(draft.deceasedFullName).trim() || !draft.dateOfDeath) {
            return { step: "deceased", message: "Enter the name to remember and the date of death." };
        }
        if (draft.dateOfBirth && draft.dateOfDeath && draft.dateOfDeath < draft.dateOfBirth) {
            return { step: "deceased", message: "Date of death cannot be earlier than date of birth." };
        }
        var max = catalog.venue ? catalog.venue.maxAttendees : 40;
        var attendees = Number(draft.attendees);
        if (!attendees || attendees < 1 || attendees > max) {
            return { step: "attendees", message: "Guest count must be between 1 and " + max + "." };
        }
        if (phoneRequired && (!draft.phone || !String(draft.phone).trim())) {
            return { step: "phone", message: "A contact phone is required to complete this arrangement." };
        }
        if (draft.phone && String(draft.phone).trim() && !/^\+?[0-9\s().-]{7,20}$/.test(String(draft.phone).trim())) {
            return { step: "phone", message: "Enter a valid phone number." };
        }
        if (!draft.paymentMethod) {
            return { step: "payment", message: "Choose a payment method." };
        }
        return null;
    }

    function goMissing(step, message) {
        addBot(message);
        go(step);
    }

    function go(step) {
        draft.step = step;
        persistSafe();
        progress.textContent = "Step " + (STEPS.indexOf(step) + 1);
        render();
    }

    function render() {
        clearControls();
        if (!authenticated) {
            addBot("Hi, I am Evelyn from EverRest. I walk you through an arrangement with buttons — no free typing, and no AI.");
            addBot("Sign in to continue.");
            controls.appendChild(button("Sign in", false, function () {
                window.location.href = loginUrl;
            }));
            return;
        }
        var step = draft.step;
        if (step === "home") {
            renderHomes();
        } else if (step === "venue") {
            renderVenues();
        } else if (step === "service") {
            renderService();
        } else if (step === "pack") {
            renderPackage();
        } else if (step === "deceased") {
            renderDeceased();
        } else if (step === "attendees") {
            renderAttendees();
        } else if (step === "phone") {
            renderPhone();
        } else if (step === "extras") {
            renderExtras();
        } else if (step === "payment") {
            renderPayment();
        } else if (step === "note") {
            renderNote();
        } else if (step === "review") {
            renderReview();
        } else if (step === "success") {
            renderSuccess();
        }
    }

    function renderHomes() {
        addBot("Choose a funeral home.");
        api("/homes").then(function (homes) {
            catalog.homes = homes;
            homes.forEach(function (home) {
                controls.appendChild(button(home.name, draft.homeId === home.id, function () {
                    draft.homeId = home.id;
                    draft.venueId = null;
                    addUser(home.name);
                    go("venue");
                }));
            });
            focusFirstControl();
        }).catch(function (error) {
            addBot(error.message);
        });
    }

    function renderVenues() {
        addBot("Choose a venue.");
        api("/homes/" + draft.homeId + "/venues").then(function (venues) {
            catalog.venues = venues;
            venues.forEach(function (venue) {
                controls.appendChild(button(venue.name + " · " + venue.venueTypeLabel, draft.venueId === venue.id, function () {
                    draft.venueId = venue.id;
                    addUser(venue.name);
                    go("service");
                }));
            });
            focusFirstControl();
        }).catch(function (error) {
            addBot(error.message);
        });
    }

    function renderService() {
        api("/venues/" + draft.venueId).then(function (venue) {
            catalog.venue = venue;
            addBot("What kind of ceremony is this?");
            venue.serviceTypes.forEach(function (type) {
                controls.appendChild(button(type.label, draft.serviceType === type.code, function () {
                    draft.serviceType = type.code;
                    addUser(type.label);
                    go("pack");
                }));
            });
            focusFirstControl();
        }).catch(function (error) {
            addBot(error.message);
        });
    }

    function renderPackage() {
        var venue = catalog.venue;
        if (!venue) {
            go("service");
            return;
        }
        addBot("Choose a package. Duration is fixed; the date will be assigned later.");
        venue.packages.forEach(function (pack) {
            controls.appendChild(button(pack.label + " · " + pack.durationMinutes + " min · " + pack.amount + " PLN", draft.funeralPackage === pack.code, function () {
                draft.funeralPackage = pack.code;
                addUser(pack.label);
                go("deceased");
            }));
        });
        focusFirstControl();
    }

    function renderDeceased() {
        addBot("Enter the name to remember and the date of death. Date of birth is optional. These details stay on this page only until you send them.");
        var name = document.createElement("input");
        name.type = "text";
        name.maxLength = 120;
        name.placeholder = "Full name";
        name.autocomplete = "off";
        name.value = draft.deceasedFullName || "";
        var birth = document.createElement("input");
        birth.type = "date";
        birth.value = draft.dateOfBirth || "";
        var death = document.createElement("input");
        death.type = "date";
        death.value = draft.dateOfDeath || "";
        var next = document.createElement("button");
        next.type = "button";
        next.className = "ca-card-btn";
        next.textContent = "Continue";
        next.addEventListener("click", function () {
            if (!name.value.trim() || !death.value) {
                addBot("Name and date of death are required.");
                name.focus();
                return;
            }
            if (birth.value && death.value < birth.value) {
                addBot("Date of death cannot be earlier than date of birth.");
                death.focus();
                return;
            }
            draft.deceasedFullName = name.value.trim();
            draft.dateOfBirth = birth.value;
            draft.dateOfDeath = death.value;
            addUser("Details received");
            go("attendees");
        });
        controls.appendChild(name);
        controls.appendChild(birth);
        controls.appendChild(death);
        controls.appendChild(next);
        name.focus();
    }

    function renderAttendees() {
        var max = catalog.venue ? catalog.venue.maxAttendees : 40;
        addBot("How many guests will attend? Capacity is " + max + ".");
        var input = document.createElement("input");
        input.type = "number";
        input.min = "1";
        input.max = String(max);
        input.value = String(draft.attendees || 1);
        var next = document.createElement("button");
        next.type = "button";
        next.className = "ca-card-btn";
        next.textContent = "Continue";
        next.addEventListener("click", function () {
            var value = Number(input.value);
            if (!value || value < 1 || value > max) {
                addBot("Guest count must be between 1 and " + max + ".");
                input.focus();
                return;
            }
            draft.attendees = value;
            addUser(String(value) + " guests");
            go(phoneRequired ? "phone" : "extras");
        });
        controls.appendChild(input);
        controls.appendChild(next);
        input.focus();
    }

    function renderPhone() {
        addBot("A contact phone is required because your profile does not have one yet.");
        var input = document.createElement("input");
        input.type = "tel";
        input.maxLength = 20;
        input.autocomplete = "off";
        input.value = draft.phone || "";
        var next = document.createElement("button");
        next.type = "button";
        next.className = "ca-card-btn";
        next.textContent = "Continue";
        next.addEventListener("click", function () {
            if (!input.value.trim()) {
                addBot("Enter a phone number.");
                input.focus();
                return;
            }
            draft.phone = input.value.trim();
            addUser("Phone received");
            go("extras");
        });
        controls.appendChild(input);
        controls.appendChild(next);
        input.focus();
    }

    function renderExtras() {
        addBot("Add extras if you wish. Urn selection appears only for cremation.");
        api("/venues/" + draft.venueId + "/extras?serviceType=" + encodeURIComponent(draft.serviceType)).then(function (extras) {
            catalog.extras = extras;
            extras.forEach(function (extra) {
                var selected = draft.extraIds.indexOf(extra.id) >= 0;
                controls.appendChild(button(extra.name + " · " + extra.amount + " PLN" + (extra.pricingMode === "PER_ATTENDEE" ? " / guest" : ""), selected, function () {
                    var idx = draft.extraIds.indexOf(extra.id);
                    if (idx >= 0) {
                        draft.extraIds.splice(idx, 1);
                    } else {
                        draft.extraIds.push(extra.id);
                    }
                    persistSafe();
                    clearControls();
                    renderExtras();
                }));
            });
            controls.appendChild(button("Continue", false, function () {
                addUser(draft.extraIds.length ? "Extras selected" : "No extras");
                go("payment");
            }));
            focusFirstControl();
        }).catch(function (error) {
            addBot(error.message);
        });
    }

    function renderPayment() {
        addBot("How will you pay? This is a preference only — there is no online gateway.");
        [["CASH", "Cash"], ["CARD_ON_SITE", "Card at the funeral home"], ["ONLINE_TRANSFER", "Bank transfer"]].forEach(function (pair) {
            controls.appendChild(button(pair[1], draft.paymentMethod === pair[0], function () {
                draft.paymentMethod = pair[0];
                addUser(pair[1]);
                go("note");
            }));
        });
        focusFirstControl();
    }

    function renderNote() {
        addBot("Optional private family note. Skip if you prefer.");
        var area = document.createElement("textarea");
        area.maxLength = 1000;
        area.rows = 3;
        var skip = button("Skip", false, function () {
            draft.note = "";
            addUser("No note");
            go("review");
        });
        var next = button("Save note", false, function () {
            draft.note = area.value.trim();
            addUser("Note received");
            go("review");
        });
        controls.appendChild(area);
        controls.appendChild(skip);
        controls.appendChild(next);
        area.focus();
    }

    function renderReview() {
        var missing = firstMissing();
        if (missing) {
            goMissing(missing.step, missing.message);
            return;
        }
        addBot("Confirm to assign a currently available date. The quoted amount will not change.");
        var confirm = button("Confirm arrangements", false, function () {
            startAssignment();
        });
        controls.appendChild(confirm);
        confirm.focus();
    }

    function startAssignment() {
        var missing = firstMissing();
        if (missing) {
            if (window.EverRestWheel && window.EverRestWheel.isOpen()) {
                window.EverRestWheel.dismiss();
            }
            goMissing(missing.step, missing.message);
            return;
        }
        if (assigning || (window.EverRestWheel && window.EverRestWheel.isBusy())) {
            return;
        }
        assigning = true;
        var body = requestBody();
        window.EverRestWheel.open({
            copy: "Your available ceremony date is being assigned.",
            historyUrl: historyUrl,
            onRetry: function () {
                resetToken();
                assigning = false;
                startAssignment();
            },
            onClose: function () {
                assigning = false;
            }
        });
        api("/preview", { method: "POST", body: JSON.stringify(body) }).then(function (preview) {
            catalog.preview = preview;
            if (window.EverRestWheel.isBusy() && preview.dates) {
                window.EverRestWheel.setCandidates(preview.dates.map(String));
            }
        }).catch(function () {
        });
        api("/arrangements", { method: "POST", body: JSON.stringify(body) }).then(function (created) {
            catalog.created = created;
            window.EverRestWheel.reveal(created);
            assigning = false;
            go("success");
        }).catch(function (error) {
            assigning = false;
            if (error.step || error.field) {
                window.EverRestWheel.dismiss();
                var step = error.step || ({ venueId: "venue", serviceType: "service", funeralPackage: "pack", deceasedFullName: "deceased", dateOfDeath: "deceased", dateOfBirth: "deceased", attendees: "attendees", phone: "phone", extraIds: "extras", paymentMethod: "payment", note: "note" }[error.field]);
                goMissing(step || "review", error.message);
                return;
            }
            var retry = error.code === "STALE_SLOT" || error.code === "NO_SLOTS" || error.code === "LOCK_TIMEOUT";
            if (retry) {
                resetToken();
            }
            window.EverRestWheel.fail(error.message, retry);
        });
    }

    function renderSuccess() {
        var created = catalog.created;
        if (!created) {
            go("review");
            return;
        }
        addBot("Assigned " + String(created.startAt).replace("T", " ").slice(0, 16) + ". Status " + created.status + ". Amount " + created.formattedAmount + ".");
        controls.appendChild(button("Open history", false, function () {
            window.location.href = historyUrl;
        }));
        controls.appendChild(button("Open notices", false, function () {
            window.location.href = noticesUrl;
        }));
        focusFirstControl();
    }

    function restart() {
        draft = emptyDraft();
        catalog = { homes: [], venues: [], venue: null, extras: [], preview: null, created: null };
        logEl.innerHTML = "";
        resetToken();
        persistSafe();
        go("home");
    }

    function openPanel() {
        panel.hidden = false;
        panel.setAttribute("aria-hidden", "false");
        launcher.setAttribute("aria-expanded", "true");
        persistSafe();
        panel.focus();
        if (!logEl.childElementCount) {
            addBot("Hi, I am Evelyn from EverRest. I walk you through an arrangement with buttons — no free typing, and no AI.");
            render();
        }
    }

    function closePanel() {
        panel.hidden = true;
        panel.setAttribute("aria-hidden", "true");
        launcher.setAttribute("aria-expanded", "false");
        persistSafe();
        launcher.focus();
    }

    function isInteractive(target) {
        return !!(target && target.closest && target.closest("button,input,select,textarea,a"));
    }

    function allowDrag() {
        return window.matchMedia("(min-width: 721px)").matches;
    }

    launcher.addEventListener("click", function () {
        if (panel.hidden) {
            openPanel();
        } else {
            closePanel();
        }
    });
    closeBtn.addEventListener("click", closePanel);
    closeBtn.addEventListener("pointerdown", function (event) {
        event.stopPropagation();
    });
    restartBtn.addEventListener("click", restart);
    restartBtn.addEventListener("pointerdown", function (event) {
        event.stopPropagation();
    });
    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") {
            return;
        }
        if (window.EverRestWheel && window.EverRestWheel.isBusy()) {
            return;
        }
        if (window.EverRestWheel && window.EverRestWheel.isOpen()) {
            return;
        }
        if (!panel.hidden) {
            closePanel();
        }
    });

    (function drag() {
        var dragging = false;
        var dx = 0;
        var dy = 0;
        head.addEventListener("pointerdown", function (event) {
            if (!allowDrag() || isInteractive(event.target)) {
                return;
            }
            dragging = true;
            var rect = root.getBoundingClientRect();
            dx = event.clientX - rect.left;
            dy = event.clientY - rect.top;
            head.setPointerCapture(event.pointerId);
        });
        head.addEventListener("pointermove", function (event) {
            if (!dragging) {
                return;
            }
            root.style.left = (event.clientX - dx) + "px";
            root.style.top = (event.clientY - dy) + "px";
            root.style.right = "auto";
            root.style.bottom = "auto";
        });
        head.addEventListener("pointerup", function () {
            if (!dragging) {
                return;
            }
            dragging = false;
            persistSafe();
        });
    })();

    restoreSafe();
    if (loadStore().open) {
        openPanel();
    }
})();
