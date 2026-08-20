(function () {
    var form = document.getElementById("arrange-form");
    if (!form) {
        return;
    }
    var confirmBtn = document.getElementById("confirm-btn");
    var quoteEl = document.getElementById("quote-amount");
    var errorEl = document.getElementById("arrange-error");
    var doneEl = document.getElementById("arrange-done");
    var spinning = false;
    var tokenKey = "everrest-submit-" + (form.venueId ? form.venueId.value : "form");
    var doneKey = "everrest-done-" + (form.venueId ? form.venueId.value : "form");

    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var headers = { "Accept": "application/json" };
        if (token && header) {
            headers[header.getAttribute("content")] = token.getAttribute("content");
        }
        return headers;
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
        var existing = sessionStorage.getItem(tokenKey);
        if (existing) {
            return existing;
        }
        var created = newToken();
        sessionStorage.setItem(tokenKey, created);
        return created;
    }

    function resetToken() {
        sessionStorage.removeItem(tokenKey);
    }

    function controlFor(name) {
        if (name === "funeralPackage") {
            return form.querySelector("input[name='funeralPackage']");
        }
        return form.querySelector("[name='" + name + "']");
    }

    function focusControl(input) {
        if (!input) {
            return;
        }
        var wrap = input.closest(".er-cal-wrap");
        var trigger = wrap ? wrap.querySelector(".er-cal-trigger") : null;
        var target = trigger || input;
        if (target.scrollIntoView) {
            target.scrollIntoView({ behavior: "smooth", block: "center" });
        }
        if (typeof target.focus === "function") {
            target.focus();
        }
    }

    function revealField(name, message, shouldFocus) {
        var input = controlFor(name);
        var error = form.querySelector("[data-error-for='" + name + "']");
        if (input) {
            input.classList.add("is-invalid");
            input.setAttribute("aria-invalid", "true");
        }
        if (error) {
            error.hidden = false;
            error.textContent = message;
        } else {
            errorEl.hidden = false;
            errorEl.textContent = message;
        }
        if (shouldFocus !== false) {
            focusControl(input || errorEl);
        }
    }

    function showError(name, message) {
        if (!name) {
            errorEl.hidden = false;
            errorEl.textContent = message;
            return;
        }
        revealField(name, message);
    }

    function clearErrors() {
        errorEl.hidden = true;
        errorEl.textContent = "";
        form.querySelectorAll(".is-invalid").forEach(function (el) {
            el.classList.remove("is-invalid");
            el.removeAttribute("aria-invalid");
        });
        form.querySelectorAll("[aria-invalid='true']").forEach(function (el) {
            el.removeAttribute("aria-invalid");
        });
        form.querySelectorAll("[data-error-for]").forEach(function (el) {
            el.hidden = true;
            el.textContent = "";
        });
    }

    function selectedService() {
        var select = form.serviceType;
        return select ? select.value : "";
    }

    function refreshExtras() {
        var service = selectedService();
        form.querySelectorAll(".extra-option").forEach(function (label) {
            var required = label.getAttribute("data-required-service") || "";
            var show = !required || required === service;
            label.hidden = !show;
            if (!show) {
                var box = label.querySelector("input[type='checkbox']");
                if (box) {
                    box.checked = false;
                }
            }
        });
        refreshQuote();
    }

    function formBody() {
        var data = new URLSearchParams();
        data.set("venueId", form.venueId.value);
        data.set("serviceType", form.serviceType.value);
        var pack = form.querySelector("input[name='funeralPackage']:checked");
        if (pack) {
            data.set("funeralPackage", pack.value);
        }
        data.set("deceasedFullName", form.deceasedFullName.value.trim());
        if (form.dateOfBirth && form.dateOfBirth.value) {
            data.set("dateOfBirth", form.dateOfBirth.value);
        }
        data.set("dateOfDeath", form.dateOfDeath.value);
        var guests = parseGuests(form.attendees.value);
        if (guests) {
            data.set("attendees", String(guests));
        }
        if (form.phone && form.phone.value) {
            data.set("phone", form.phone.value.trim());
        }
        data.set("paymentMethod", form.paymentMethod.value);
        if (form.note && form.note.value) {
            data.set("note", form.note.value.trim());
        }
        form.querySelectorAll("input[name='extraIds']:checked").forEach(function (box) {
            if (!box.closest(".extra-option") || box.closest(".extra-option").hidden) {
                return;
            }
            data.append("extraIds", box.value);
        });
        data.set("bookingSource", "FORM");
        data.set("submissionToken", submissionToken());
        return data;
    }

    function parseGuests(value) {
        var digits = String(value || "").replace(/[^0-9]/g, "");
        if (!digits) {
            return NaN;
        }
        return Number(digits);
    }

    function formatMoney(amount, currency) {
        var n = Number(amount);
        if (amount == null || amount === "" || isNaN(n)) {
            return "—";
        }
        return n.toFixed(2) + " " + (currency || "PLN");
    }

    function showQuotedTotal(data) {
        if (!quoteEl || !data) {
            return;
        }
        quoteEl.textContent = data.formattedAmount || formatMoney(data.amount, data.currency);
    }

    function canQuote() {
        var fields = ["serviceType", "funeralPackage", "deceasedFullName", "dateOfDeath", "attendees", "paymentMethod"];
        var i;
        for (i = 0; i < fields.length; i++) {
            if (fieldIssue(fields[i])) {
                return false;
            }
        }
        return true;
    }

    function refreshQuote() {
        if (!quoteEl) {
            return;
        }
        if (!canQuote()) {
            return;
        }
        var quoteUrl = form.getAttribute("data-quote-url");
        if (!quoteUrl) {
            return;
        }
        post(quoteUrl, formBody()).then(function (data) {
            showQuotedTotal(data);
        }).catch(function () {
        });
    }

    function todayIso() {
        var now = new Date();
        return now.getFullYear() + "-" + String(now.getMonth() + 1).padStart(2, "0") + "-" + String(now.getDate()).padStart(2, "0");
    }

    function clearFieldError(name) {
        var input = controlFor(name);
        var error = form.querySelector("[data-error-for='" + name + "']");
        if (input) {
            input.classList.remove("is-invalid");
            input.removeAttribute("aria-invalid");
        }
        if (error) {
            error.hidden = true;
            error.textContent = "";
        }
    }

    function fieldIssue(name) {
        var today = todayIso();
        if (name === "serviceType" && !form.serviceType.value) {
            return "Choose a ceremony type";
        }
        if (name === "funeralPackage" && !form.querySelector("input[name='funeralPackage']:checked")) {
            return "Choose a package";
        }
        if (name === "deceasedFullName" && !form.deceasedFullName.value.trim()) {
            return "Enter the name to remember";
        }
        if (name === "dateOfBirth" && form.dateOfBirth.value) {
            if (form.dateOfBirth.value > today) {
                return "Date of birth cannot be in the future";
            }
            if (form.dateOfDeath.value && form.dateOfDeath.value < form.dateOfBirth.value) {
                return "Date of birth cannot be after date of death";
            }
        }
        if (name === "dateOfDeath") {
            if (!form.dateOfDeath.value) {
                return "Enter the date of death";
            }
            if (form.dateOfDeath.value > today) {
                return "Date of death cannot be in the future";
            }
            if (form.dateOfBirth.value && form.dateOfDeath.value < form.dateOfBirth.value) {
                return "Date of death cannot be earlier than date of birth";
            }
        }
        if (name === "attendees") {
            var attendees = parseGuests(form.attendees.value);
            var max = Number(form.getAttribute("data-max-attendees") || form.attendees.getAttribute("data-max-attendees") || "1");
            if (!attendees || attendees < 1 || attendees > max) {
                return "Enter a guest count between 1 and " + max;
            }
        }
        if (name === "phone" && form.phone) {
            if (form.phone.required && !form.phone.value.trim()) {
                return "Phone is required to complete this arrangement";
            }
            if (form.phone.value.trim() && !/^\+?[0-9\s().-]{7,20}$/.test(form.phone.value.trim())) {
                return "Enter a valid phone number";
            }
        }
        if (name === "paymentMethod" && !form.paymentMethod.value) {
            return "Choose a payment method";
        }
        return null;
    }

    function validateField(name, requireFilled) {
        if (!name) {
            return true;
        }
        var message = fieldIssue(name);
        var input = controlFor(name);
        var empty = !input || !String(input.type === "radio" ? "" : (input.value || "")).trim();
        if (name === "funeralPackage") {
            empty = !form.querySelector("input[name='funeralPackage']:checked");
        }
        if (!message) {
            clearFieldError(name);
            return true;
        }
        if (name === "dateOfBirth" && !form.dateOfBirth.value) {
            clearFieldError(name);
            return true;
        }
        if (!requireFilled && empty) {
            return true;
        }
        if (!requireFilled && name === "phone" && form.phone) {
            var digits = form.phone.value.replace(/[^0-9]/g, "");
            if (digits.length > 0 && digits.length < 7 && /^\+?[0-9\s().-]*$/.test(form.phone.value.trim())) {
                return true;
            }
        }
        revealField(name, message, requireFilled);
        return false;
    }

    function validate() {
        clearErrors();
        var fields = ["serviceType", "funeralPackage", "deceasedFullName", "dateOfBirth", "dateOfDeath", "attendees", "phone", "paymentMethod"];
        var first = null;
        var i;
        for (i = 0; i < fields.length; i++) {
            var name = fields[i];
            var message = fieldIssue(name);
            if (name === "dateOfBirth" && !form.dateOfBirth.value) {
                continue;
            }
            if (message) {
                revealField(name, message, !first);
                if (!first) {
                    first = name;
                }
            }
        }
        return !first;
    }

    function post(url, body) {
        return fetch(url, {
            method: "POST",
            headers: Object.assign({ "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" }, csrfHeaders()),
            body: body.toString(),
            credentials: "same-origin"
        }).then(function (response) {
            return response.json().then(function (data) {
                if (!response.ok) {
                    var error = new Error(data.message || "That arrangement could not be completed.");
                    error.code = data.code;
                    error.field = data.field;
                    error.step = data.step;
                    throw error;
                }
                return data;
            });
        });
    }

    function markDone(data) {
        spinning = false;
        confirmBtn.disabled = true;
        sessionStorage.setItem(doneKey, String(data.id));
        showQuotedTotal(data);
        if (doneEl) {
            doneEl.hidden = false;
            doneEl.textContent = "Arrangement #" + data.id + " · " + data.status + " · " + data.formattedAmount;
        }
    }

    function confirmArrangements() {
        if (spinning) {
            return;
        }
        if (sessionStorage.getItem(doneKey) && !window.EverRestWheel) {
            return;
        }
        if (!validate()) {
            return;
        }
        spinning = true;
        confirmBtn.disabled = true;
        var body = formBody();
        var previewUrl = form.getAttribute("data-preview-url");
        var previewPromise = previewUrl ? post(previewUrl, body) : Promise.resolve({});
        previewPromise.then(function (preview) {
            showQuotedTotal(preview);
            window.EverRestWheel.open({
                copy: "Your available ceremony date is being assigned.",
                historyUrl: form.getAttribute("data-history-url"),
                onRetry: function () {
                    resetToken();
                    spinning = false;
                    confirmBtn.disabled = false;
                    confirmArrangements();
                },
                onClose: function () {
                    if (!sessionStorage.getItem(doneKey)) {
                        confirmBtn.disabled = false;
                        spinning = false;
                    }
                }
            });
            if (preview && preview.dates) {
                window.EverRestWheel.setCandidates(preview.dates.map(String));
            }
            return post(form.getAttribute("data-spin-url"), body);
        }).then(function (data) {
            if (!data || !data.id) {
                return;
            }
            window.EverRestWheel.reveal(data);
            markDone(data);
        }).catch(function (error) {
            spinning = false;
            confirmBtn.disabled = false;
            if (error.field) {
                if (window.EverRestWheel && window.EverRestWheel.isOpen()) {
                    window.EverRestWheel.dismiss();
                }
                revealField(error.field, error.message);
                return;
            }
            var retry = error.code === "STALE_SLOT" || error.code === "NO_SLOTS" || error.code === "LOCK_TIMEOUT";
            if (retry) {
                resetToken();
            }
            if (window.EverRestWheel && window.EverRestWheel.isOpen()) {
                window.EverRestWheel.fail(error.message, retry);
            } else {
                showError(null, error.message);
            }
            if (retry) {
                confirmBtn.disabled = false;
            }
        });
    }

    form.serviceType.addEventListener("change", refreshExtras);
    refreshExtras();
    confirmBtn.addEventListener("click", confirmArrangements);
    form.addEventListener("input", function (event) {
        if (event.target && event.target.name) {
            validateField(event.target.name, false);
            if (event.target.name === "dateOfBirth" || event.target.name === "dateOfDeath") {
                validateField("dateOfBirth", false);
                validateField("dateOfDeath", false);
            }
            if (event.target.name === "extraIds" || event.target.name === "funeralPackage" || event.target.name === "attendees" || event.target.name === "serviceType" || event.target.name === "paymentMethod") {
                refreshQuote();
            }
        }
    });
    form.addEventListener("change", function (event) {
        if (event.target && event.target.name) {
            validateField(event.target.name, false);
            if (event.target.name === "dateOfBirth" || event.target.name === "dateOfDeath") {
                validateField("dateOfBirth", false);
                validateField("dateOfDeath", false);
            }
            if (event.target.name === "extraIds" || event.target.name === "funeralPackage" || event.target.name === "attendees" || event.target.name === "serviceType" || event.target.name === "paymentMethod") {
                refreshQuote();
            }
        }
    });
    form.addEventListener("blur", function (event) {
        if (event.target && event.target.name) {
            validateField(event.target.name, true);
        }
    }, true);
    if (sessionStorage.getItem(doneKey)) {
        confirmBtn.disabled = true;
    }
    refreshQuote();
})();
