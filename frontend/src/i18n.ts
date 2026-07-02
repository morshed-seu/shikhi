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
      stats: {
        title: 'Your progress',
        xp: 'XP',
        streak: 'day streak',
        hearts: 'Hearts',
      },
      curriculum: {
        title: 'Your course',
        loading: 'Loading your course…',
        empty: 'No lessons are published yet.',
        error: 'Could not load the course.',
      },
      lesson: {
        loading: 'Starting the lesson…',
        error: 'Could not load the lesson.',
        check: 'Check',
        next: 'Continue',
        finish: 'Finish',
        correct: 'Correct!',
        incorrect: 'Not quite',
        answerLabel: 'Your answer',
        answerPlaceholder: 'Type in English…',
        unsupported: 'This exercise type is coming soon.',
        heartsLeft: 'Hearts: {{hearts}}',
        resultsTitle: 'Lesson complete!',
        score: 'Score: {{score}} / {{total}}',
        xpEarned: 'XP earned: {{xp}}',
        backToCourse: 'Back to course',
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
      stats: {
        title: 'আপনার অগ্রগতি',
        xp: 'XP',
        streak: 'দিনের ধারা',
        hearts: 'হৃদয়',
      },
      curriculum: {
        title: 'আপনার কোর্স',
        loading: 'আপনার কোর্স লোড হচ্ছে…',
        empty: 'এখনও কোনো পাঠ প্রকাশিত হয়নি।',
        error: 'কোর্স লোড করা যায়নি।',
      },
      lesson: {
        loading: 'পাঠ শুরু হচ্ছে…',
        error: 'পাঠটি লোড করা যায়নি।',
        check: 'যাচাই করুন',
        next: 'পরবর্তী',
        finish: 'শেষ করুন',
        correct: 'সঠিক!',
        incorrect: 'ঠিক হয়নি',
        answerLabel: 'আপনার উত্তর',
        answerPlaceholder: 'ইংরেজিতে লিখুন…',
        unsupported: 'এই ধরনের অনুশীলন শীঘ্রই আসছে।',
        heartsLeft: 'হৃদয়: {{hearts}}',
        resultsTitle: 'পাঠ সম্পন্ন!',
        score: 'স্কোর: {{score}} / {{total}}',
        xpEarned: 'অর্জিত XP: {{xp}}',
        backToCourse: 'কোর্সে ফিরে যান',
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
