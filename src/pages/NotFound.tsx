import { Link } from 'react-router-dom'
import { useI18n } from '../context/i18n'

export default function NotFound() {
  const { t } = useI18n()
  return (
    <div className="card mx-auto mt-10 max-w-md text-center">
      <div className="text-6xl font-bold text-brand-600">404</div>
      <p className="mt-2 text-slate-500">{t('nf.title')}</p>
      <Link to="/" className="btn btn-primary mt-4">{t('nf.back')}</Link>
    </div>
  )
}
