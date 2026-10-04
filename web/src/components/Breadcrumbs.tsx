import { Fragment } from 'react'
import { Link } from 'react-router-dom'

export interface BreadcrumbItem {
  label: string
  /** Опущен для текущего уровня: он не является ссылкой. */
  to?: string
}

/**
 * Навигационная цепочка (`← События / <название> / <раздел>`). Промежуточные
 * уровни — ссылки, последний — текущий (не ссылка). Когда событие ещё не
 * загружено, промежуточный уровень просто отсутствует.
 */
export function Breadcrumbs({ items }: { items: BreadcrumbItem[] }) {
  return (
    <nav className="breadcrumbs" aria-label="Навигационная цепочка">
      <span aria-hidden="true">←</span>
      {items.map((item, index) => (
        <Fragment key={index}>
          {index > 0 && <span className="breadcrumbs__sep">/</span>}
          {item.to === undefined ? (
            <span className="breadcrumbs__current" aria-current="page">
              {item.label}
            </span>
          ) : (
            <Link className="link" to={item.to}>
              {item.label}
            </Link>
          )}
        </Fragment>
      ))}
    </nav>
  )
}
