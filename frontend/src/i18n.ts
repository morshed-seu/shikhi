import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

// Bilingual UI (decision D1): Bengali is the default; users can switch to English.
// M0 keeps translations inline; later milestones move these to per-locale catalogs.
const resources = {
  en: {
    translation: {
      app: {
        title: 'Shikhi',
        tagline: 'Learn English — in Bangla.',
        toggleLanguage: 'Switch language',
      },
      status: {
        loading: 'Checking backend…',
        ok: 'Backend: {{status}}',
        error: 'Backend unreachable',
      },
      auth: {
        loading: 'Loading…',
        formTitle: 'Sign in or create an account',
        profileTitle: 'Your account',
        login: 'Log in',
        register: 'Sign up',
        email: 'Email',
        password: 'Password',
        displayName: 'Name',
        submitting: 'Please wait…',
        logout: 'Log out',
        greeting: 'Hi, {{name}}!',
        learner: 'learner',
        genericError: 'Something went wrong. Please try again.',
      },
    },
  },
  bn: {
    translation: {
      app: {
        title: 'শিখি',
        tagline: 'বাংলায় ইংরেজি শিখুন।',
        toggleLanguage: 'ভাষা পরিবর্তন করুন',
      },
      status: {
        loading: 'ব্যাকএন্ড পরীক্ষা করা হচ্ছে…',
        ok: 'ব্যাকএন্ড: {{status}}',
        error: 'ব্যাকএন্ডে সংযোগ করা যায়নি',
      },
      auth: {
        loading: 'লোড হচ্ছে…',
        formTitle: 'সাইন ইন করুন বা অ্যাকাউন্ট তৈরি করুন',
        profileTitle: 'আপনার অ্যাকাউন্ট',
        login: 'লগ ইন',
        register: 'নিবন্ধন',
        email: 'ইমেইল',
        password: 'পাসওয়ার্ড',
        displayName: 'নাম',
        submitting: 'অপেক্ষা করুন…',
        logout: 'লগ আউট',
        greeting: 'স্বাগতম, {{name}}!',
        learner: 'শিক্ষার্থী',
        genericError: 'কিছু একটা সমস্যা হয়েছে। আবার চেষ্টা করুন।',
      },
    },
  },
} as const

void i18n.use(initReactI18next).init({
  resources,
  lng: 'bn',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

export default i18n
