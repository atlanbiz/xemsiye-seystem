import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { CheckCircle2, Info, AlertTriangle, XCircle } from 'lucide-react'

type Kind = 'success' | 'info' | 'warning' | 'error'
interface Toast {
  id: number
  kind: Kind
  msg: string
}

const Ctx = createContext<(msg: string, kind?: Kind) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [list, setList] = useState<Toast[]>([])
  const push = useCallback((msg: string, kind: Kind = 'success') => {
    const id = Date.now() + Math.random()
    setList((l) => [...l, { id, kind, msg }])
    setTimeout(() => setList((l) => l.filter((t) => t.id !== id)), 3500)
  }, [])
  const icons = { success: CheckCircle2, info: Info, warning: AlertTriangle, error: XCircle }
  const colors = { success: 'text-emerald-500', info: 'text-blue-500', warning: 'text-amber-500', error: 'text-rose-500' }
  return (
    <Ctx.Provider value={push}>
      {children}
      <div className="pointer-events-none fixed bottom-4 start-1/2 z-[100] flex -translate-x-1/2 flex-col items-center gap-2 rtl:translate-x-1/2 no-print">
        {list.map((t) => {
          const I = icons[t.kind]
          return (
            <div key={t.id} className="toast-in pointer-events-auto flex items-center gap-2 rounded-xl border border-white/60 bg-white/95 px-4 py-3 text-sm font-medium text-slate-700 shadow-lg backdrop-blur dark:border-slate-700 dark:bg-slate-800/95 dark:text-slate-100">
              <I className={`h-5 w-5 ${colors[t.kind]}`} />
              {t.msg}
            </div>
          )
        })}
      </div>
    </Ctx.Provider>
  )
}

export const useToast = () => useContext(Ctx)
