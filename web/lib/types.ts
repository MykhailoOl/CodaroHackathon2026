
// ---- reservation-assistant API ------------------------------------------

export interface AssistantSession {
  authenticated: boolean;
  userId: number;
  username: string;
  role: string;
  phoneRequired: boolean;
  expectedStatus: string;
  csrfToken?: string;
}

export interface AssistantHome {
  id: number;
  name: string;
  description?: string;
  address?: string;
}

export interface AssistantVenue {
  id: number;
  name: string;
  venueType: string;
  venueTypeLabel: string;
  address: string;
  openingTime: string;
  closingTime: string;
  maxAttendees: number;
  imagePath?: string;
}

export interface ArrangementRequest {
  venueId: number;
  serviceType: string;
  funeralPackage: string;
  deceasedFullName: string;
  dateOfDeath: string;
  attendees: number;
  paymentMethod: string;
  phone?: string;
  extraIds: number[];
}

export interface AssistantPreview {
  dates: string[];
  amount: number;
  currency: string;
  expectedStatus: string;
  notice?: string;
}

export interface ArrangementCreated {
  id: number;
  status: string;
  amount: number;
  formattedAmount: string;
  startAt: string;
  endAt: string;
  dates: string[];
}
