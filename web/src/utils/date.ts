/** Formats an event start value for the list cards, or a neutral placeholder. */
export function formatEventDate(value: string | null): string {
  if (!value) {
    return 'Дата не указана'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 'Дата не указана'
  }
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
