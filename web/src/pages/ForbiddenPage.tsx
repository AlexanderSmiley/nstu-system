import { Link } from 'react-router-dom'

/** 403 screen shown when a client-side role guard denies a route (access spec). */
export function ForbiddenPage() {
  return (
    <section className="page">
      <h1>Доступ запрещён</h1>
      <p className="form-error" role="alert">
        У вас недостаточно прав для этого раздела
      </p>
      <Link className="link" to="/">
        На главную
      </Link>
    </section>
  )
}
