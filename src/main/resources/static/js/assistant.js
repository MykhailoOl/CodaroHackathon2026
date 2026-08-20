(function () {
    var root = document.getElementById("everrest-assistant");
    if (!root || root.getAttribute("data-initialized") === "true" || window.__everrestEvelynInit) {
        return;
    }
    window.__everrestEvelynInit = true;
    root.setAttribute("data-initialized", "true");
    var launcher = document.getElementById("ca-launcher");
    var panel = document.getElementById("ca-panel");
    var logEl = document.getElementById("ca-log");
    var controls = document.getElementById("ca-controls");
    var progress = document.getElementById("ca-progress");
    var restartBtn = document.getElementById("ca-restart");
    var SCHEMA = 3;
    var SCHEMA_FLAG = "everrest-evelyn-schema";
    var LEGACY_STORE = "everrest-evelyn";
    var TOKEN_KEY = "everrest-evelyn-submit";
    var STEPS = ["home", "venue", "service", "pack", "deceased", "attendees", "phone", "extras", "payment", "note", "review", "success"];
    var SENSITIVE_STEPS = { deceased: true, phone: true, note: true };
    var authenticated = root.getAttribute("data-authenticated") === "true";
    var phoneRequired = root.getAttribute("data-phone-required") === "true";
    var loginUrl = root.getAttribute("data-login-url") || "/login";
    var historyUrl = root.getAttribute("data-history-url") || "/reservations";
    var noticesUrl = root.getAttribute("data-notices-url") || "/notifications";
    var userMeta = document.querySelector('meta[name="everrest-user-id"]');
    var userId = root.getAttribute("data-user-id") || (userMeta ? userMeta.getAttribute("content") : "") || "anon";
    if (!userId) {
        userId = "anon";
    }
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

    function identity() {
        return String(userId || "anon");
    }

    function storeKey() {
        return "everrest-evelyn-v3:" + identity();
    }

    function loadStore() {
        try {
            return JSON.parse(localStorage.getItem(storeKey()) || "{}");
        } catch (e) {
            return {};
        }
    }

    function saveStore(patch) {
        var current = loadStore();
        Object.keys(patch).forEach(function (key) {
            current[key] = patch[key];
        });
        delete current.x;
        delete current.y;
        delete current.left;
        delete current.top;
        delete current.right;
        current.schema = SCHEMA;
        localStorage.setItem(storeKey(), JSON.stringify(current));
    }

    function clearPlacement() {
        [root, panel, launcher].forEach(function (el) {
            if (!el || !el.style) {
                return;
            }
            el.style.removeProperty("left");
            el.style.removeProperty("top");
            el.style.removeProperty("right");
            el.style.removeProperty("bottom");
            el.style.removeProperty("transform");
        });
    }

    function forgetLegacyKeys() {
        var remove = [LEGACY_STORE, "everrest-evelyn-v2:" + identity()];
        for (var i = 0; i < localStorage.length; i++) {
            var key = localStorage.key(i);
            if (key && key.indexOf("everrest-evelyn-v2:") === 0) {
                remove.push(key);
            }
        }
        remove.forEach(function (key) {
            localStorage.removeItem(key);
        });
    }

    function draftFromLegacy(source) {
        var step = source.currentStep || source.step || "home";
        if (step === "wheel") {
            step = "review";
        }
        return {
            schema: SCHEMA,
            open: false,
            homeId: source.homeId || null,
            venueId: source.venueId || null,
            serviceType: source.serviceType || "",
            funeralPackage: source.funeralPackage || "",
            extraIds: source.extraIds || [],
            paymentMethod: source.paymentMethod || "CASH",
            attendees: source.attendees || 1,
            currentStep: step
        };
    }

    function migrateOnce() {
        clearPlacement();
        if (localStorage.getItem(SCHEMA_FLAG) === "3") {
            var existing = loadStore();
            delete existing.x;
            delete existing.y;
            delete existing.left;
            delete existing.top;
            delete existing.right;
            localStorage.setItem(storeKey(), JSON.stringify(existing));
            return;
        }
        try {
            var v2 = JSON.parse(localStorage.getItem("everrest-evelyn-v2:" + identity()) || "null");
            var legacy = JSON.parse(localStorage.getItem(LEGACY_STORE) || "null");
            var source = null;
            if (v2 && typeof v2 === "object") {
                source = v2;
            } else if (legacy && String(legacy.userId || "anon") === identity()) {
                source = legacy;
            }
            if (source) {
                localStorage.setItem(storeKey(), JSON.stringify(draftFromLegacy(source)));
            } else {
                localStorage.setItem(storeKey(), JSON.stringify({ schema: SCHEMA, open: false }));
            }
        } catch (e) {
            localStorage.setItem(storeKey(), JSON.stringify({ schema: SCHEMA, open: false }));
        }
        forgetLegacyKeys();
        localStorage.setItem(SCHEMA_FLAG, "3");
        clearPlacement();
    }

    function persistSafe() {
        var step = draft.step;
        if (STEPS.indexOf(step) < 0) {
            step = "home";
        }
        saveStore({
            open: !panel.hidden,
            homeId: draft.homeId,
            venueId: draft.venueId,
            serviceType: draft.serviceType,
            funeralPackage: draft.funeralPackage,
            extraIds: draft.extraIds,
            paymentMethod: draft.paymentMethod,
            attendees: draft.attendees,
            currentStep: step,
            createdId: catalog.created ? catalog.created.id : null,
            createdStatus: catalog.created ? catalog.created.status : null,
            createdAmount: catalog.created ? catalog.created.amount : null,
            createdFormattedAmount: catalog.created ? catalog.created.formattedAmount : null,
            createdStartAt: catalog.created ? catalog.created.startAt : null
        });
    }

    function restoreSafe() {
        migrateOnce();
        var stored = loadStore();
        draft.homeId = stored.homeId || null;
        draft.venueId = stored.venueId || null;
        draft.serviceType = stored.serviceType || "";
        draft.funeralPackage = stored.funeralPackage || "";
        draft.extraIds = stored.extraIds || [];
        draft.paymentMethod = stored.paymentMethod || "CASH";
        draft.attendees = stored.attendees || 1;
        var step = stored.currentStep || stored.step || "home";
        if (step === "wheel") {
            step = "review";
        }
        if (STEPS.indexOf(step) >= 0) {
            draft.step = step;
        }
        if (stored.createdId && stored.createdStartAt) {
            catalog.created = {
                id: stored.createdId,
                status: stored.createdStatus,
                amount: stored.createdAmount,
                formattedAmount: stored.createdFormattedAmount,
                startAt: stored.createdStartAt
            };
        }
        clearPlacement();
    }

    function inspectStorage() {
        var raw = localStorage.getItem(storeKey()) || "";
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

    function todayIso() {
        var now = new Date();
        return now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, "0") + "-" + String(now.getDate()).padStart(2, "0");
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
        if (draft.dateOfBirth && draft.dateOfBirth > todayIso()) {
            return { step: "deceased", message: "Date of birth cannot be in the future." };
        }
        if (draft.dateOfDeath && draft.dateOfDeath > todayIso()) {
            return { step: "deceased", message: "Date of death cannot be in the future." };
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
        if (panel.hidden) {
            openPanel();
        }
        addBot(message);
        go(step);
    }

    function go(step) {
        draft.step = step;
        persistSafe();
        progress.textContent = "Step " + (STEPS.indexOf(step) + 1);
        render();
    }

    function idsMatch(left, right) {
        return String(left) === String(right);
    }

    function rehydrateThenRender() {
        if (!authenticated) {
            render();
            return;
        }
        var step = draft.step;
        if (!draft.homeId || step === "home") {
            render();
            return;
        }
        api("/homes").then(function (homes) {
            catalog.homes = homes || [];
            var homeOk = catalog.homes.some(function (home) {
                return idsMatch(home.id, draft.homeId);
            });
            if (!homeOk) {
                draft.homeId = null;
                draft.venueId = null;
                draft.serviceType = "";
                draft.funeralPackage = "";
                draft.extraIds = [];
                draft.step = "home";
                persistSafe();
                render();
                return null;
            }
            return api("/homes/" + draft.homeId + "/venues");
        }).then(function (venues) {
            if (venues == null) {
                return null;
            }
            catalog.venues = venues || [];
            if (!draft.venueId) {
                if (step !== "venue") {
                    draft.step = "venue";
                    persistSafe();
                }
                render();
                return null;
            }
            var venueOk = catalog.venues.some(function (venue) {
                return idsMatch(venue.id, draft.venueId);
            });
            if (!venueOk) {
                draft.venueId = null;
                draft.serviceType = "";
                draft.funeralPackage = "";
                draft.extraIds = [];
                draft.step = "venue";
                persistSafe();
                render();
                return null;
            }
            return api("/venues/" + draft.venueId);
        }).then(function (venue) {
            if (venue == null) {
                return;
            }
            catalog.venue = venue;
            var types = (venue.serviceTypes || []).map(function (type) {
                return type.code;
            });
            if (draft.serviceType && types.indexOf(draft.serviceType) < 0) {
                draft.serviceType = "";
                draft.funeralPackage = "";
                draft.extraIds = [];
                draft.step = "service";
                persistSafe();
                render();
                return;
            }
            var packs = (venue.packages || []).map(function (pack) {
                return pack.code;
            });
            if (draft.funeralPackage && packs.indexOf(draft.funeralPackage) < 0) {
                draft.funeralPackage = "";
                draft.step = "pack";
                persistSafe();
                render();
                return;
            }
            if (draft.serviceType && draft.extraIds && draft.extraIds.length) {
                return api("/venues/" + draft.venueId + "/extras?serviceType=" + encodeURIComponent(draft.serviceType)).then(function (extras) {
                    catalog.extras = extras || [];
                    var allowed = {};
                    catalog.extras.forEach(function (extra) {
                        allowed[String(extra.id)] = true;
                    });
                    var kept = draft.extraIds.filter(function (id) {
                        return allowed[String(id)];
                    });
                    if (kept.length !== draft.extraIds.length) {
                        draft.extraIds = kept;
                        if (STEPS.indexOf(draft.step) > STEPS.indexOf("extras")) {
                            draft.step = "extras";
                        }
                        persistSafe();
                    }
                    finishRehydrate();
                });
            }
            finishRehydrate();
        }).catch(function (error) {
            addBot(error.message);
            render();
        });
    }

    function finishRehydrate() {
        if (SENSITIVE_STEPS[draft.step]) {
            addBot("Please re-enter this private detail. It is not kept in the browser after you leave the page.");
        }
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
        birth.setAttribute("data-max-today", "");
        birth.value = draft.dateOfBirth || "";
        var death = document.createElement("input");
        death.type = "date";
        death.setAttribute("data-max-today", "");
        death.required = true;
        death.value = draft.dateOfDeath || "";
        var hint = document.createElement("p");
        hint.className = "ca-inline-error";
        hint.hidden = true;
        function setHint(text) {
            hint.hidden = !text;
            hint.textContent = text || "";
        }
        function liveDates() {
            draft.dateOfBirth = birth.value;
            draft.dateOfDeath = death.value;
            var today = todayIso();
            if (birth.value && birth.value > today) {
                setHint("Date of birth cannot be in the future.");
                return false;
            }
            if (death.value && death.value > today) {
                setHint("Date of death cannot be in the future.");
                return false;
            }
            if (birth.value && death.value && death.value < birth.value) {
                setHint("Date of death cannot be earlier than date of birth.");
                return false;
            }
            setHint("");
            return true;
        }
        birth.addEventListener("change", liveDates);
        death.addEventListener("change", liveDates);
        name.addEventListener("blur", function () {
            if (!name.value.trim()) {
                setHint("Enter the name to remember.");
            } else if (liveDates()) {
                setHint("");
            }
        });
        var next = document.createElement("button");
        next.type = "button";
        next.className = "ca-card-btn";
        next.textContent = "Continue";
        next.addEventListener("click", function () {
            if (!name.value.trim()) {
                setHint("Name and date of death are required.");
                name.focus();
                return;
            }
            if (!death.value) {
                setHint("Enter the date of death.");
                death.focus();
                return;
            }
            if (!liveDates()) {
                return;
            }
            draft.deceasedFullName = name.value.trim();
            draft.dateOfBirth = birth.value;
            draft.dateOfDeath = death.value;
            addUser("Details received");
            go("attendees");
        });
        var born = document.createElement("label");
        born.className = "ca-field";
        born.appendChild(document.createTextNode("Date of birth"));
        born.appendChild(birth);
        var died = document.createElement("label");
        died.className = "ca-field";
        died.appendChild(document.createTextNode("Date of death"));
        died.appendChild(death);
        var dates = document.createElement("div");
        dates.className = "ca-date-row";
        dates.appendChild(born);
        dates.appendChild(died);
        controls.appendChild(name);
        controls.appendChild(dates);
        controls.appendChild(hint);
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
        var hint = document.createElement("p");
        hint.className = "ca-inline-error";
        hint.hidden = true;
        function liveGuests() {
            var value = Number(input.value);
            if (!input.value) {
                hint.hidden = true;
                return false;
            }
            if (!value || value < 1 || value > max) {
                hint.hidden = false;
                hint.textContent = "Guest count must be between 1 and " + max + ".";
                return false;
            }
            hint.hidden = true;
            return true;
        }
        input.addEventListener("input", liveGuests);
        input.addEventListener("blur", liveGuests);
        var next = document.createElement("button");
        next.type = "button";
        next.className = "ca-card-btn";
        next.textContent = "Continue";
        next.addEventListener("click", function () {
            if (!liveGuests()) {
                input.focus();
                return;
            }
            draft.attendees = Number(input.value);
            addUser(String(draft.attendees) + " guests");
            go(phoneRequired ? "phone" : "extras");
        });
        controls.appendChild(input);
        controls.appendChild(hint);
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
        var hint = document.createElement("p");
        hint.className = "ca-inline-error";
        hint.hidden = true;
        function livePhone(requireFilled) {
            var value = input.value.trim();
            if (!value) {
                if (requireFilled) {
                    hint.hidden = false;
                    hint.textContent = "Enter a phone number.";
                    return false;
                }
                hint.hidden = true;
                return false;
            }
            var digits = value.replace(/[^0-9]/g, "");
            if (!requireFilled && digits.length < 7 && /^\+?[0-9\s().-]*$/.test(value)) {
                hint.hidden = true;
                return false;
            }
            if (!/^\+?[0-9\s().-]{7,20}$/.test(value)) {
                hint.hidden = false;
                hint.textContent = "Enter a valid phone number.";
                return false;
            }
            hint.hidden = true;
            return true;
        }
        input.addEventListener("input", function () {
            livePhone(false);
        });
        input.addEventListener("blur", function () {
            livePhone(true);
        });
        var next = document.createElement("button");
        next.type = "button";
        next.className = "ca-card-btn";
        next.textContent = "Continue";
        next.addEventListener("click", function () {
            if (!livePhone(true)) {
                input.focus();
                return;
            }
            draft.phone = input.value.trim();
            addUser("Phone received");
            go("extras");
        });
        controls.appendChild(input);
        controls.appendChild(hint);
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
        api("/preview", { method: "POST", body: JSON.stringify(body) }).then(function (preview) {
            catalog.preview = preview;
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
            if (preview.dates) {
                window.EverRestWheel.setCandidates(preview.dates.map(String));
            }
            return api("/arrangements", { method: "POST", body: JSON.stringify(body) });
        }).then(function (created) {
            if (!created || !created.id) {
                return;
            }
            catalog.created = created;
            window.EverRestWheel.reveal(created);
            assigning = false;
            go("success");
        }).catch(function (error) {
            assigning = false;
            if (error.step || error.field) {
                if (window.EverRestWheel && window.EverRestWheel.isOpen()) {
                    window.EverRestWheel.dismiss();
                }
                var step = error.step || ({ venueId: "venue", serviceType: "service", funeralPackage: "pack", deceasedFullName: "deceased", dateOfDeath: "deceased", dateOfBirth: "deceased", attendees: "attendees", phone: "phone", extraIds: "extras", paymentMethod: "payment", note: "note" }[error.field]);
                goMissing(step || "review", error.message);
                return;
            }
            var retry = error.code === "STALE_SLOT" || error.code === "NO_SLOTS" || error.code === "LOCK_TIMEOUT";
            if (retry) {
                resetToken();
            }
            if (window.EverRestWheel && window.EverRestWheel.isOpen()) {
                window.EverRestWheel.fail(error.message, retry);
            } else {
                addBot(error.message);
            }
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
        addBot("Hi, I am Evelyn from EverRest. I walk you through an arrangement with buttons — no free typing, and no AI.");
        go("home");
    }

    function openPanel() {
        panel.hidden = false;
        panel.setAttribute("aria-hidden", "false");
        launcher.setAttribute("aria-expanded", "true");
        root.classList.add("is-open");
        persistSafe();
        panel.focus();
        if (!logEl.childElementCount) {
            addBot("Hi, I am Evelyn from EverRest. I walk you through an arrangement with buttons — no free typing, and no AI.");
            rehydrateThenRender();
        }
    }

    function closePanel() {
        panel.hidden = true;
        panel.setAttribute("aria-hidden", "true");
        launcher.setAttribute("aria-expanded", "false");
        root.classList.remove("is-open");
        persistSafe();
        launcher.focus();
    }

    launcher.addEventListener("click", function () {
        if (panel.hidden) {
            openPanel();
        } else {
            closePanel();
        }
    });
    restartBtn.addEventListener("click", restart);
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
    window.addEventListener("pagehide", persistSafe);

    function startClosed() {
        panel.hidden = true;
        panel.setAttribute("aria-hidden", "true");
        launcher.setAttribute("aria-expanded", "false");
        root.classList.remove("is-open");
    }

    restoreSafe();
    startClosed();
})();
