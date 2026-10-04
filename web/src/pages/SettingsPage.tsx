import { ApiError } from '../api/http'
import { defaultColor, MODULE_IDS } from '../api/preferences'
import type { CalendarAudience, ModuleId } from '../api/types'
import { usePreferences, useUpdatePreferences } from '../hooks/usePreferences'

const MODULE_LABELS: Record<ModuleId, string> = {
  events: 'События',
  calendar: 'Календарь',
  notes: 'Заметки',
}

const COLOR_LABELS: Record<CalendarAudience, string> = {
  ME: 'Ваш цвет',
  GROUP: 'Цвет группы',
  STAFF: 'Цвет персонала',
}

const COLOR_ORDER: readonly CalendarAudience[] = ['ME', 'GROUP', 'STAFF']

/** Maps a `PATCH /preferences` failure to a localised message. */
function preferencesError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'invalid_color') {
      return 'Недопустимый цвет. Используйте формат #RRGGBB'
    }
    return error.message
  }
  return 'Не удалось сохранить настройки. Попробуйте позже.'
}

/**
 * `/settings` — account-only screen (guarded by `RequireRole`). Two sections:
 * "Модульность" toggles home modules and "Цвета календаря" edits the three
 * audience colours. Every change is a partial `PATCH` applied optimistically, so
 * it takes effect without a reload; a rejected colour is shown as an error and
 * rolled back without touching the other values.
 */
export function SettingsPage() {
  const { preferences } = usePreferences()
  const mutation = useUpdatePreferences()

  const error = mutation.isError ? preferencesError(mutation.error) : null

  return (
    <section className="page">
      <h1>Настройки</h1>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      <section className="settings-section" aria-labelledby="settings-modules">
        <h2 id="settings-modules">Модульность</h2>
        <p className="placeholder-note">Выключенные модули скрываются на главной и в меню.</p>
        <ul className="settings-list">
          {MODULE_IDS.map((id) => (
            <li key={id} className="settings-list__item">
              <label className="settings-toggle" htmlFor={`module-${id}`}>
                <input
                  id={`module-${id}`}
                  type="checkbox"
                  checked={preferences.modules[id] !== false}
                  onChange={(event) =>
                    mutation.mutate({ modules: { [id]: event.target.checked } })
                  }
                />
                <span>{MODULE_LABELS[id]}</span>
              </label>
            </li>
          ))}
        </ul>
      </section>

      <section className="settings-section" aria-labelledby="settings-colors">
        <h2 id="settings-colors">Цвета календаря</h2>
        <p className="placeholder-note">
          Цвет заливки мероприятий по адресату. Применяется мягко, чтобы текст оставался читаемым.
        </p>
        <ul className="settings-list">
          {COLOR_ORDER.map((audience) => (
            <li key={audience} className="settings-list__item">
              <label className="settings-color" htmlFor={`color-${audience}`}>
                <span>{COLOR_LABELS[audience]}</span>
                <input
                  id={`color-${audience}`}
                  type="color"
                  value={preferences.calendarColors[audience]}
                  aria-label={COLOR_LABELS[audience]}
                  onChange={(event) =>
                    mutation.mutate({
                      calendarColors: { [audience]: event.target.value },
                    })
                  }
                />
              </label>
              <button
                type="button"
                className="button button--secondary button--small"
                aria-label={`Сбросить ${COLOR_LABELS[audience]}`}
                onClick={() =>
                  mutation.mutate({
                    calendarColors: { [audience]: defaultColor(audience) },
                  })
                }
              >
                Сбросить
              </button>
            </li>
          ))}
        </ul>
      </section>
    </section>
  )
}
