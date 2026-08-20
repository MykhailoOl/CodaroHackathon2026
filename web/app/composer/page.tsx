"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { PageShell } from "@/components/PageShell";
import {
  ApiError,
  createArrangement,
  getHomes,
  getVenues,
  logout,
  previewArrangement,
  venueFits,
} from "@/lib/api";
import { clearSession, loadSession } from "@/lib/auth";
import type {
  ArrangementCreated,
  ArrangementRequest,
  AssistantHome,
  AssistantPreview,
  AssistantVenue,
} from "@/lib/types";

const SERVICE_TYPES = [
  { value: "BURIAL_CEREMONY", label: "Burial ceremony" },
  { value: "CREMATION_CEREMONY", label: "Cremation" },
  { value: "MEMORIAL_SERVICE", label: "Memorial service" },
  { value: "FAREWELL_CEREMONY", label: "Farewell ceremony" },
];

const PACKAGES = [
  { value: "ESSENTIAL", label: "Essential" },
  { value: "CLASSIC", label: "Classic" },
  { value: "TRIBUTE", label: "Tribute" },
];

const PAYMENT_METHOD = "ONLINE_TRANSFER";

function formatDate(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
}

function formatDateTime(value: string): string {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime())
    ? value
    : parsed.toLocaleString(undefined, {
        weekday: "short",
        day: "numeric",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
      });
}

export default function ComposerPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState<string | null>(null);
  const [phoneRequired, setPhoneRequired] = useState(false);

  const [deceased, setDeceased] = useState("");
  const [dateOfDeath, setDateOfDeath] = useState("");
  const [serviceType, setServiceType] = useState(SERVICE_TYPES[0].value);
  const [funeralPackage, setFuneralPackage] = useState(PACKAGES[1].value);
  const [attendees, setAttendees] = useState(40);
  const [phone, setPhone] = useState("");

  const [homes, setHomes] = useState<AssistantHome[]>([]);
  const [homeId, setHomeId] = useState<number | null>(null);
  const [venues, setVenues] = useState<AssistantVenue[]>([]);
  const [venueId, setVenueId] = useState<number | null>(null);

  const [preview, setPreview] = useState<AssistantPreview | null>(null);
  const [created, setCreated] = useState<ArrangementCreated | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const session = loadSession();
    if (!session) {
      router.replace("/");
      return;
    }
    setDisplayName(session.username);
    setPhoneRequired(session.phoneRequired);
    getHomes()
      .then((list) => {
        setHomes(list);
        if (list.length) setHomeId(list[0].id);
      })
      .catch(() => setError("The funeral homes could not be loaded."));
  }, [router]);

  useEffect(() => {
    if (homeId === null) return;
    setVenueId(null);
    getVenues(homeId)
      .then(setVenues)
      .catch(() => setError("That home's spaces could not be loaded."));
  }, [homeId]);

  // A space is offered only if it holds everyone and hosts this kind of service —
  // the server refuses the mismatch, so never put it on screen as a choice.
  const offered = useMemo(
    () => venues.filter((venue) => venueFits(venue, serviceType, attendees)),
    [venues, serviceType, attendees],
  );

  const chosenVenue = offered.find((venue) => venue.id === venueId) ?? null;

  function buildRequest(): ArrangementRequest {
    return {
      venueId: venueId as number,
      serviceType,
      funeralPackage,
      deceasedFullName: deceased.trim(),
      dateOfDeath,
      attendees,
      paymentMethod: PAYMENT_METHOD,
      extraIds: [],
      ...(phone.trim() ? { phone: phone.trim() } : {}),
    };
  }

  async function handlePreview(e: React.FormEvent) {
    e.preventDefault();
    if (!venueId) {
      setError("Choose a space.");
      return;
    }
    setLoading(true);
    setError(null);
    setCreated(null);
    try {
      setPreview(await previewArrangement(buildRequest()));
    } catch (err) {
      setPreview(null);
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirm() {
    setLoading(true);
    setError(null);
    try {
      setCreated(await createArrangement(buildRequest()));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Nothing was confirmed. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  async function handleLogout() {
    await logout();
    clearSession();
    router.push("/");
  }

  const field =
    "mt-1 w-full rounded-md border border-stone-400 bg-white px-3 py-3 text-sm outline-none focus:border-forest";
  const label = "block text-sm font-medium text-stone-700";

  return (
    <PageShell signedInAs={displayName} onSignOut={handleLogout}>
      <div className="mx-auto grid w-full max-w-6xl flex-1 grid-cols-1 lg:grid-cols-[minmax(0,1.15fr)_minmax(20rem,0.85fr)]">
      <form onSubmit={handlePreview} className="space-y-6 px-6 py-10 sm:px-10">
        <div>
          <p className="text-xs tracking-[0.16em] text-stone-500 uppercase">Arrangement</p>
          <h1 className="font-display mt-2 text-4xl leading-none text-charcoal">Tell us the circumstances</h1>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-stone-600">
            I&apos;m sorry for your loss. You do not pick a day. We assign the earliest free ceremony, and you approve it.
          </p>
        </div>

        <div>
          <label htmlFor="deceased" className={label}>Who died</label>
          <p className="text-xs text-stone-500">Their full name, as it should appear on the record.</p>
          <input id="deceased" required value={deceased} onChange={(e) => setDeceased(e.target.value)} className={field} />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="dateOfDeath" className={label}>Date of death</label>
            <input
              id="dateOfDeath"
              type="date"
              required
              max={new Date().toISOString().slice(0, 10)}
              value={dateOfDeath}
              onChange={(e) => setDateOfDeath(e.target.value)}
              className={field}
            />
          </div>
          <div>
            <label htmlFor="attendees" className={label}>People attending</label>
            <input
              id="attendees"
              type="number"
              min={1}
              required
              value={attendees}
              onChange={(e) => setAttendees(Number(e.target.value))}
              className={field}
            />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="serviceType" className={label}>Service</label>
            <select id="serviceType" value={serviceType} onChange={(e) => setServiceType(e.target.value)} className={field}>
              {SERVICE_TYPES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="funeralPackage" className={label}>Arrangement</label>
            <select id="funeralPackage" value={funeralPackage} onChange={(e) => setFuneralPackage(e.target.value)} className={field}>
              {PACKAGES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="home" className={label}>Funeral home</label>
            <select
              id="home"
              value={homeId ?? ""}
              onChange={(e) => setHomeId(Number(e.target.value))}
              className={field}
            >
              {homes.map((home) => (
                <option key={home.id} value={home.id}>{home.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="venue" className={label}>Space</label>
            <select
              id="venue"
              required
              value={venueId ?? ""}
              onChange={(e) => setVenueId(Number(e.target.value))}
              className={field}
            >
              <option value="" disabled>Choose a space</option>
              {offered.map((venue) => (
                <option key={venue.id} value={venue.id}>
                  {venue.name} · {venue.venueTypeLabel} · up to {venue.maxAttendees}
                </option>
              ))}
            </select>
            {!offered.length && (
              <p className="mt-1 text-xs text-amber-700">
                No space here holds {attendees} for this service. Try another home.
              </p>
            )}
          </div>
        </div>

        {phoneRequired && (
          <div>
            <label htmlFor="phone" className={label}>Phone</label>
            <input id="phone" required value={phone} onChange={(e) => setPhone(e.target.value)} className={field} />
          </div>
        )}

        {error && (
          <div className="rounded-lg border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-900">{error}</div>
        )}

        <button
          type="submit"
          disabled={loading || !offered.length}
          className="w-full bg-charcoal px-4 py-3 text-sm font-semibold text-ivory hover:bg-forest disabled:opacity-50"
        >
          {loading ? "Checking…" : "See what can be held"}
        </button>
      </form>

      <aside className="flex flex-col justify-center border-t border-stone-300 bg-white/50 px-6 py-10 sm:px-10 lg:border-l lg:border-t-0">
        {!preview && !created && (
          <div>
            <p className="text-xs tracking-[0.16em] text-stone-500 uppercase">Proposal</p>
            <p className="font-display mt-3 text-3xl leading-tight text-charcoal">
              The home will settle the day after you tell us the facts.
            </p>
            <p className="mt-4 max-w-sm text-sm leading-relaxed text-stone-600">
              There is no calendar to shop from. One ceremony is assigned. A director still confirms.
            </p>
          </div>
        )}

        {preview && !created && (
          <section>
            <p className="text-xs tracking-[0.16em] text-stone-500 uppercase">Proposed arrangement</p>
            <h2 className="font-display mt-3 text-3xl leading-tight text-charcoal">{deceased || "The person remembered"}</h2>
            {chosenVenue && (
              <p className="mt-2 text-sm text-stone-600">
                {chosenVenue.name} — {chosenVenue.address}
              </p>
            )}
            <p className="mt-6 text-sm leading-relaxed text-stone-700">
              {preview.dates.length
                ? `Days are open from ${formatDate(preview.dates[0])} onwards. The funeral home settles the exact day and hour when you confirm.`
                : "The funeral home will settle the day when you confirm."}
            </p>
            <p className="mt-3 text-sm text-charcoal">
              {attendees} attending · {preview.amount} {preview.currency}
            </p>
            {preview.notice && <p className="mt-2 text-sm text-stone-600">{preview.notice}</p>}
            <button
              type="button"
              onClick={handleConfirm}
              disabled={loading}
              className="mt-8 w-full bg-charcoal px-4 py-3 text-sm font-semibold text-ivory hover:bg-forest disabled:opacity-50"
            >
              {loading ? "Confirming…" : "Confirm this arrangement"}
            </button>
          </section>
        )}

        {created && (
          <section>
            <p className="text-xs tracking-[0.16em] text-forest uppercase">Received</p>
            <h2 className="font-display mt-3 text-3xl leading-tight text-charcoal">{deceased}</h2>
            {chosenVenue && <p className="mt-2 text-sm text-stone-700">{chosenVenue.name}</p>}
            <p className="mt-4 text-sm text-charcoal">{formatDateTime(created.startAt)}</p>
            <p className="mt-1 text-sm text-stone-700">
              Reference {created.id} · {created.formattedAmount} · {created.status.toLowerCase()}
            </p>
            <p className="mt-6 text-sm leading-relaxed text-stone-600">
              The funeral home has been notified and will be in touch about the rest. This is pending until a director confirms.
            </p>
          </section>
        )}
      </aside>
      </div>
    </PageShell>
  );
}
