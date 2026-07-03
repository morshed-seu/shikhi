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
        toggleTheme: 'Switch light/dark theme',
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
      review: {
        title: 'Review',
        due: '{{count}} to review',
        knewIt: 'I knew it',
        stillLearning: 'Still learning',
      },
      curriculum: {
        title: 'Your course',
        loading: 'Loading your course…',
        empty: 'No lessons are published yet.',
        error: 'Could not load the course.',
      },
      vocab: {
        title: 'Word list',
        subtitle: 'The Oxford 3000 core words, with Bangla meanings and examples.',
        show: 'Browse words',
        hide: 'Hide',
        searchPlaceholder: 'Search a word or meaning…',
        loading: 'Loading words…',
        error: 'Could not load the word list.',
        empty: 'No words for this level yet.',
        count: 'Showing {{shown}} of {{total}}',
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
        wordBankHint: 'Tap the words in order to build the sentence.',
        sentenceLabel: 'Your sentence',
        unsupported: 'This exercise type is coming soon.',
        heartsLeft: 'Hearts: {{hearts}}',
        resultsTitle: 'Lesson complete!',
        score: 'Score: {{score}} / {{total}}',
        xpEarned: 'XP earned: {{xp}}',
        backToCourse: 'Back to course',
        savedOffline: 'Saved offline — it will sync when you reconnect.',
      },
      offline: {
        message: "You're offline — your progress will sync when you reconnect.",
      },
      onboarding: {
        title: 'Welcome to Shikhi!',
        welcome: 'Learn English step by step, in Bangla. Pick a lesson below to begin.',
        placement: 'You’ll start at Beginner (A1).',
        start: 'Got it',
      },
    },
  },
  bn: {
    translation: {
      app: {
        title: 'শিখি',
        tagline: 'বাংলায় ইংরেজি শিখুন।',
        toggleLanguage: 'ভাষা পরিবর্তন করুন',
        toggleTheme: 'আলো/অন্ধকার থিম পরিবর্তন করুন',
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
      review: {
        title: 'পুনরালোচনা',
        due: 'পুনরালোচনার জন্য {{count}}টি',
        knewIt: 'আমি জানতাম',
        stillLearning: 'এখনও শিখছি',
      },
      curriculum: {
        title: 'আপনার কোর্স',
        loading: 'আপনার কোর্স লোড হচ্ছে…',
        empty: 'এখনও কোনো পাঠ প্রকাশিত হয়নি।',
        error: 'কোর্স লোড করা যায়নি।',
      },
      vocab: {
        title: 'শব্দ তালিকা',
        subtitle: 'অক্সফোর্ড ৩০০০ মূল শব্দ, বাংলা অর্থ ও উদাহরণসহ।',
        show: 'শব্দ দেখুন',
        hide: 'লুকান',
        searchPlaceholder: 'শব্দ বা অর্থ খুঁজুন…',
        loading: 'শব্দ লোড হচ্ছে…',
        error: 'শব্দ তালিকা লোড করা যায়নি।',
        empty: 'এই স্তরের জন্য এখনও কোনো শব্দ নেই।',
        count: '{{total}}টির মধ্যে {{shown}}টি দেখানো হচ্ছে',
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
        wordBankHint: 'বাক্যটি তৈরি করতে শব্দগুলো ক্রমানুসারে ট্যাপ করুন।',
        sentenceLabel: 'আপনার বাক্য',
        unsupported: 'এই ধরনের অনুশীলন শীঘ্রই আসছে।',
        heartsLeft: 'হৃদয়: {{hearts}}',
        resultsTitle: 'পাঠ সম্পন্ন!',
        score: 'স্কোর: {{score}} / {{total}}',
        xpEarned: 'অর্জিত XP: {{xp}}',
        backToCourse: 'কোর্সে ফিরে যান',
        savedOffline: 'অফলাইনে সংরক্ষিত — সংযোগ ফিরলে সিঙ্ক হবে।',
      },
      offline: {
        message: 'আপনি অফলাইনে আছেন — সংযোগ ফিরলে আপনার অগ্রগতি সিঙ্ক হবে।',
      },
      onboarding: {
        title: 'শিখিতে স্বাগতম!',
        welcome: 'বাংলায় ধাপে ধাপে ইংরেজি শিখুন। শুরু করতে নিচে একটি পাঠ বেছে নিন।',
        placement: 'আপনি প্রাথমিক (A1) থেকে শুরু করবেন।',
        start: 'বুঝেছি',
      },
    },
  },
} as const

const LOCALE_KEY = 'shikhi.locale'

function storedLocale(): 'bn' | 'en' {
  try {
    const saved = localStorage.getItem(LOCALE_KEY)
    return saved === 'en' || saved === 'bn' ? saved : 'bn'
  } catch {
    return 'bn'
  }
}

const initialLocale = storedLocale()

void i18n.use(initReactI18next).init({
  resources,
  lng: initialLocale,
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

// Reflect the language on <html lang> for assistive tech and correct Bengali rendering (a11y).
if (typeof document !== 'undefined') {
  document.documentElement.lang = initialLocale
}

/** Switch UI language, persisting it and updating <html lang> (M5). */
export function changeLocale(lang: 'bn' | 'en'): void {
  void i18n.changeLanguage(lang)
  try {
    localStorage.setItem(LOCALE_KEY, lang)
  } catch {
    // Persistence is best-effort; the in-memory switch still applies.
  }
  if (typeof document !== 'undefined') {
    document.documentElement.lang = lang
  }
}

export default i18n
