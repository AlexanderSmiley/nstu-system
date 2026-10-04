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
      <Link to="/" className="app-header__title">
        {siteName}
      </Link>
      <UserMenu />
    </header>
  )
}
