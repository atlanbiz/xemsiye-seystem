import { lazy, Suspense, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/auth'
import { DataProvider } from './context/data'
import { ToastProvider } from './context/toast'
import Layout from './components/Layout'
import Overview from './pages/Overview'
import Sites from './pages/Sites'
import SiteDetail from './pages/SiteDetail'
import Analytics from './pages/Analytics'
import Devices from './pages/Devices'
import Reports from './pages/Reports'
import Maintenance from './pages/Maintenance'
import Billing from './pages/Billing'
import Settings from './pages/Settings'
import { Skeleton } from './components/ui'

// v2 pages load on demand (Leaflet, finance model, alert rules) to keep the main bundle lean
const MapPage = lazy(() => import('./pages/MapPage'))
const Finance = lazy(() => import('./pages/Finance'))
const Alerts = lazy(() => import('./pages/Alerts'))
const Page = ({ children }: { children: ReactNode }) => <Suspense fallback={<Skeleton className="mt-4 h-[60vh]" />}>{children}</Suspense>
import Login from './pages/Login'
import NotFound from './pages/NotFound'

function Protected({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return null
  return user ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <DataProvider>
          <ToastProvider>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route element={<Protected><Layout /></Protected>}>
                <Route index element={<Overview />} />
                <Route path="sites" element={<Sites />} />
                <Route path="sites/:id" element={<SiteDetail />} />
                <Route path="map" element={<Page><MapPage /></Page>} />
                <Route path="analytics" element={<Analytics />} />
                <Route path="finance" element={<Page><Finance /></Page>} />
                <Route path="alerts" element={<Page><Alerts /></Page>} />
                <Route path="devices" element={<Devices />} />
                <Route path="reports" element={<Reports />} />
                <Route path="maintenance" element={<Maintenance />} />
                <Route path="billing" element={<Billing />} />
                <Route path="settings" element={<Settings />} />
                <Route path="*" element={<NotFound />} />
              </Route>
            </Routes>
          </ToastProvider>
        </DataProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
