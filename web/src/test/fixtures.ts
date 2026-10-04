import type {
  Event,
  EventDetail,
  Me,
  QueueEntry,
  QueueResponse,
  StudentProfile,
} from '../api/types'

export const STUDENT_ME: Me = {
  subject: '11111111-1111-1111-1111-111111111111',
  roles: ['STUDENT'],
  displayName: 'Студент Петров',
  mustChangePassword: false,
  guest: false,
}

export const STAFF_ME: Me = {
  subject: '22222222-2222-2222-2222-222222222222',
  roles: ['STAFF'],
  displayName: 'Староста Сидоров',
  mustChangePassword: false,
  guest: false,
}

export const ADMIN_ME: Me = {
  subject: '33333333-3333-3333-3333-333333333333',
  roles: ['ADMIN'],
  displayName: 'Администратор',
  mustChangePassword: false,
  guest: false,
}

export const GUEST_ME: Me = {
  subject: 'guest:44444444-4444-4444-4444-444444444444',
  roles: ['GUEST'],
  displayName: null,
  mustChangePassword: false,
  guest: true,
}

export const MUST_CHANGE_ME: Me = {
  ...STUDENT_ME,
  mustChangePassword: true,
}

export const STUDENT_PROFILE: StudentProfile = {
  accountId: STUDENT_ME.subject,
  fullName: 'Петров Пётр Петрович',
  groupId: '55555555-5555-5555-5555-555555555555',
  groupName: 'ИУ7-51Б',
  contacts: null,
}

export function makeEvent(overrides: Partial<Event> = {}): Event {
  return {
    id: 'e1',
    title: 'Сдача лабораторных работ',
    description: null,
    availability: 'GUEST+',
    startsAt: '2026-05-01T12:00:00Z',
    entryLimit: 27,
    entryUnit: 'BRIGADE',
    journalVisibility: 'STAFF',
    retentionDays: 14,
    slug: 'lab-works',
    status: 'OPEN',
    groupId: 'g1',
    createdAt: '2026-04-01T00:00:00Z',
    updatedAt: '2026-04-01T00:00:00Z',
    ...overrides,
  }
}

export function makeEventDetail(overrides: Partial<EventDetail> = {}): EventDetail {
  return makeEvent(overrides)
}

export function makeQueueEntry(overrides: Partial<QueueEntry> = {}): QueueEntry {
  return {
    id: 'entry-1',
    position: 1,
    name: 'Бригада 1',
    status: 'WAITING',
    origin: 'JOIN',
    holderAccountId: null,
    guestRef: null,
    passedAt: null,
    createdAt: '2026-04-01T00:00:00Z',
    ...overrides,
  }
}

export function makeQueue(overrides: Partial<QueueResponse> = {}): QueueResponse {
  return {
    eventId: 'e1',
    eventStatus: 'OPEN',
    entryLimit: 27,
    entryUnit: 'BRIGADE',
    queue: [],
    journal: [],
    ...overrides,
  }
}
