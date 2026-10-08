import { useEffect, useState } from 'react'
import { simWeather } from './sim'

export interface Weather {
  temp: number
  code: number
  irradiance: number
  wind: number
  humidity: number
  live: boolean
}

/** Live weather from Open-Meteo (no API key), falls back to the simulator offline. */
export function useWeather(lat: number, lng: number) {
  const [w, setW] = useState<Weather>({ ...simWeather(), live: false })
  useEffect(() => {
    let cancelled = false
    const load = () => {
      const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lng}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,shortwave_radiation&timezone=auto`
      fetch(url)
        .then((r) => (r.ok ? r.json() : Promise.reject(r.status)))
        .then((j) => {
          if (cancelled || !j.current) return
          const c = j.current
          setW({
            temp: Math.round(c.temperature_2m),
            code: c.weather_code,
            irradiance: Math.round(c.shortwave_radiation ?? 0),
            wind: Math.round(c.wind_speed_10m),
            humidity: Math.round(c.relative_humidity_2m),
            live: true,
          })
        })
        .catch(() => !cancelled && setW({ ...simWeather(), live: false }))
    }
    load()
    const t = setInterval(load, 15 * 60_000)
    return () => {
      cancelled = true
      clearInterval(t)
    }
  }, [lat, lng])
  return w
}

/** WMO weather code → translation key suffix */
export function weatherKind(code: number): 'sunny' | 'partly' | 'cloudy' | 'fog' | 'rain' | 'snow' | 'storm' {
  if (code === 0 || code === 1) return 'sunny'
  if (code === 2) return 'partly'
  if (code === 3) return 'cloudy'
  if (code === 45 || code === 48) return 'fog'
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snow'
  if (code >= 95) return 'storm'
  return 'rain'
}

export const CITIES: { name: string; en: string; lat: number; lng: number }[] = [
  { name: 'ئۈرۈمچى', en: 'Urumqi', lat: 43.825, lng: 87.617 },
  { name: 'قەشقەر', en: 'Kashgar', lat: 39.47, lng: 75.99 },
  { name: 'تۇرپان', en: 'Turpan', lat: 42.95, lng: 89.18 },
  { name: 'خوتەن', en: 'Hotan', lat: 37.11, lng: 79.92 },
  { name: 'غۇلجا', en: 'Ghulja', lat: 43.92, lng: 81.32 },
  { name: 'ئاقسۇ', en: 'Aksu', lat: 41.17, lng: 80.26 },
  { name: 'قۇمۇل', en: 'Hami', lat: 42.82, lng: 93.51 },
  { name: 'كورلا', en: 'Korla', lat: 41.76, lng: 86.15 },
  { name: 'ئالمۇتا', en: 'Almaty', lat: 43.24, lng: 76.89 },
  { name: 'ئىستانبۇل', en: 'Istanbul', lat: 41.01, lng: 28.98 },
]
