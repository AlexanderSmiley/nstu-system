import { Link } from 'react-router-dom'
import { useSiteName } from '../../hooks/useSiteName'
import { MenuIcon } from '../icons'
import { UserMenu } from './UserMenu'

interface HeaderProps {
  onToggleSidebar: () => void
}

export function Header({ onToggleSidebar }: HeaderProps) {
  const siteName = useSiteName()

  return (
    <header className="app-header">
      <button
        type="button"
        className="icon-button"
        aria-label="Меню"
        onClick={onToggleSidebar}
      >
        <MenuIcon />
      </button>
      {/*
        The link is wrapped so only the text itself is clickable: the wrapper
        stretches to centre the title, but clicks on its empty part do nothing
        (access spec — "Область клика по названию сайта").
      */}
      <div className="app-header__brand" data-testid="app-header-brand">
        <Link to="/" className="app-header__title">
          {siteName}
        </Link>
      </div>
      <UserMenu />
    </header>
  )
}
