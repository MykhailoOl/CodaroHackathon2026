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

    function revealField(name, message) {
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
        var target = input || errorEl;
        if (target && target.scrollIntoView) {
            target.scrollIntoView({ behavior: "smooth", block: "center" });
        }
        if (input && typeof input.focus === "function") {
            input.focus();
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
        data.set("attendees", form.attendees.value);
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

    function validate() {
        clearErrors();
        if (!form.serviceType.value) {
            showError("serviceType", "Choose a ceremony type");
            return false;
        }
        if (!form.querySelector("input[name='funeralPackage']:checked")) {
            showError("funeralPackage", "Choose a package");
            return false;
        }
        if (!form.deceasedFullName.value.trim()) {
            showError("deceasedFullName", "Enter the name to remember");
            return false;
        }
        if (!form.dateOfDeath.value) {
            showError("dateOfDeath", "Enter the date of death");
            return false;
        }
        if (form.dateOfBirth.value && form.dateOfDeath.value && form.dateOfDeath.value < form.dateOfBirth.value) {
            showError("dateOfDeath", "Date of death cannot be earlier than date of birth");
            return false;
        }
        var attendees = Number(form.attendees.value);
        var max = Number(form.getAttribute("data-max-attendees") || "1");
        if (!attendees || attendees < 1 || attendees > max) {
            showError("attendees", "Guest count must be between 1 and " + max);
            return false;
        }
        if (form.phone && form.phone.required && !form.phone.value.trim()) {
            showError("phone", "Phone is required to complete this arrangement");
            return false;
        }
        if (form.phone && form.phone.value.trim()) {
            var phoneOk = /^\+?[0-9\s().-]{7,20}$/.test(form.phone.value.trim());
            if (!phoneOk) {
                showError("phone", "Enter a valid phone number");
                return false;
            }
        }
        if (!form.paymentMethod.value) {
            showError("paymentMethod", "Choose a payment method");
            return false;
        }
        return true;
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
        if (quoteEl && data.formattedAmount) {
            quoteEl.textContent = data.formattedAmount;
        }
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
        var previewUrl = form.getAttribute("data-preview-url");
        if (previewUrl) {
            post(previewUrl, body).then(function (preview) {
                if (preview.amount != null) {
                    quoteEl.textContent = preview.amount + " " + (preview.currency || "PLN");
                }
                if (window.EverRestWheel.isBusy() && preview.dates) {
                    window.EverRestWheel.setCandidates(preview.dates.map(String));
                }
            }).catch(function () {
            });
        }
        post(form.getAttribute("data-spin-url"), body).then(function (data) {
            window.EverRestWheel.reveal(data);
            markDone(data);
        }).catch(function (error) {
            if (error.field) {
                window.EverRestWheel.dismiss();
                spinning = false;
                confirmBtn.disabled = false;
                revealField(error.field, error.message);
                return;
            }
            var retry = error.code === "STALE_SLOT" || error.code === "NO_SLOTS" || error.code === "LOCK_TIMEOUT";
            if (retry) {
                resetToken();
            }
            window.EverRestWheel.fail(error.message, retry);
            spinning = false;
            if (!retry) {
                confirmBtn.disabled = false;
            }
        });
    }

    form.serviceType.addEventListener("change", refreshExtras);
    refreshExtras();
    confirmBtn.addEventListener("click", confirmArrangements);
    if (sessionStorage.getItem(doneKey)) {
        confirmBtn.disabled = true;
    }
})();
