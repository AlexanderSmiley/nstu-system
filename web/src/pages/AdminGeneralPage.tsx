import { SiteIconSection } from '../components/admin/SiteIconSection'
import { SiteNameSection } from '../components/admin/SiteNameSection'

/** `/admin/general` — admin block «Общее» (site name and icon; design.md D6). */
export function AdminGeneralPage() {
  return (
    <>
      <SiteNameSection />
      <SiteIconSection />
    </>
  )
}
