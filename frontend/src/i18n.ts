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
        showPassword: 'Show password',
        hidePassword: 'Hide password',
        rememberMe: 'Remember my email',
        displayName: 'Name',
        submitting: 'Please wait…',
        logout: 'Log out',
        greeting: 'Hi, {{name}}!',
        learner: 'learner',
        genericError: 'Something went wrong. Please try again.',
        or: 'or',
        guestCta: 'Try it without an account →',
        guestBadge: "You're exploring as a guest.",
      },
      guest: {
        title: 'Save your progress',
        lead: "You're learning as a guest. Create an account to save your progress.",
        save: 'Save my progress',
        createAccount: 'Create account',
        emailTaken:
          "That email already has an account. Log in instead — your guest progress from this session won't be saved.",
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
        subtitle: 'The Oxford 5000 core words, with Bangla meanings and examples.',
        show: 'Browse words',
        hide: 'Hide',
        searchPlaceholder: 'Search a word or meaning…',
        loading: 'Loading words…',
        error: 'Could not load the word list.',
        empty: 'No words for this level yet.',
        count: 'Showing {{from}}-{{to}} of {{total}}',
        pagerLabel: 'Word list pages',
        prev: 'Previous',
        next: 'Next',
        page: 'Page {{page}} of {{pages}}',
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
      practice: {
        heroEyebrow: 'Daily practice',
        heroTitle: 'Today’s practice',
        heroCopy: 'Fresh exercises from your {{level}} words — short sentences in the mix.',
        levelBadge: 'Your level: {{level}}',
        pickLevel: 'Choose your level',
        bands: {
          A1: 'Beginner',
          A2: 'Elementary',
          B1: 'Intermediate',
          B2: 'Upper intermediate',
          C1: 'Advanced',
        },
        start: 'Start session',
        loading: 'Preparing your session…',
        error: 'Could not start the session.',
        backHome: 'Back to home',
        exit: 'Leave session',
        progress: '{{done}} of {{total}} answered',
        toSummary: 'See round summary',
        roundTitle: 'Round complete!',
        roundScore: '{{correct}} / {{total}} correct',
        sessionScore: 'Session: {{correct}} / {{total}} correct',
        xpEarned: '+{{xp}} XP',
        keepGoing: 'Keep going',
        finish: 'Finish for now',
        resultsTitle: 'Session complete!',
        levelUpOffer: 'You’ve mastered most of your level — ready for {{level}}?',
        levelUpAccept: 'Move up to {{level}}',
        levelUpDone: 'Your level is now {{level}}. New sessions will use it.',
      },
      onboarding: {
        title: 'Welcome to Shikhi!',
        welcome: 'Learn English step by step, in Bangla. One tap starts your first practice session.',
        placement: 'Where should we start? Pick the level that fits — you can change it anytime.',
        skip: 'Skip — start at A1',
      },
      profile: {
        open: 'Open profile',
        openFromStats: 'Your progress — open profile',
        title: 'Profile',
        cardTitle: 'Your details',
        loading: 'Loading your profile…',
        error: 'Could not load your profile.',
        save: 'Save',
        cancel: 'Cancel',
        editName: 'Edit name',
        language: 'Language',
        joined: 'Joined {{date}}',
        stats: {
          longestStreak: 'Longest streak',
          dailyGoal: 'Daily goal',
          reviewDue: 'Review due',
          lessonsCompleted: 'Lessons completed',
          practiceSessions: 'Practice sessions',
          accuracy: 'Lifetime accuracy',
        },
        mastery: {
          title: 'Words mastered',
          count: '{{mastered}} of {{total}}',
          level: '{{level}} mastery',
        },
        actions: {
          title: 'Account',
          export: 'Download my data',
          exportError: 'Could not export your data.',
          delete: 'Delete account',
          deleteConfirmPrompt: 'This permanently deletes your account and data. Are you sure?',
          deleteConfirm: 'Yes, delete',
          deleteError: 'Could not delete your account.',
        },
      },
      legal: {
        navLabel: 'Legal pages',
        backToApp: '← Back to Shikhi',
        privacyLink: 'Privacy Policy',
        deleteAccountLink: 'Delete account',
        privacy: {
          title: 'Privacy Policy',
          effectiveDate: 'Effective date: {{date}}',
          intro:
            'Shikhi ("the app", "we") is an English-learning app for Bangla speakers, available as an Android app and a web app. This page explains what information we collect, why we collect it, and how you can have it deleted.',
          contact: 'Questions about this policy? Contact us at {{email}}.',
          sections: {
            dataTitle: 'Information we collect',
            dataBody:
              "When you create an account, we collect your email address and password. Your password is never stored directly — only a secure one-way hash is kept. You may also add an optional display name. As you use Shikhi, we store your UI language preference and your learning activity: lesson and practice progress, vocabulary statistics, and streaks. You can also use Shikhi as a guest without providing an email — guest progress is tied to a temporary account until you choose to create a full one.",
            useTitle: 'How we use your information',
            useBody:
              'We use this information only to provide and sync the Shikhi learning service across your devices — for example, tracking your progress, adapting practice to your level, and remembering your preferences. We do not show ads, and we do not sell your data.',
            sharingTitle: 'Sharing',
            sharingBody:
              'We do not share your data with third parties for advertising or marketing. Shikhi runs on hosting infrastructure that processes data on our behalf: our backend is hosted on Render, our database on Neon (PostgreSQL), and our web app on Cloudflare Pages. These providers do not use your data for their own purposes.',
            securityTitle: 'Security',
            securityBody:
              'Data sent between your device and our servers is encrypted in transit (HTTPS/TLS). Passwords are never stored in plain text — only a one-way hash is kept.',
            deletionTitle: 'Deleting your data',
            deletionBody:
              'You can permanently delete your account and all associated learning data at any time — inside the app (Profile → Delete account) or using the account-deletion page linked below. Deletion is immediate and permanent, and cannot be undone.',
            childrenTitle: 'Children',
            childrenBody:
              'Shikhi is intended for a general audience learning English. If you believe a child has provided us with personal information without appropriate consent, please contact us and we will delete it.',
            changesTitle: 'Changes to this policy',
            changesBody:
              'We may update this policy as Shikhi evolves. When we do, we will update the effective date above.',
          },
        },
      },
      deleteAccount: {
        title: 'Delete your account',
        intro:
          'You can request deletion of your Shikhi account and all learning data from this page, without needing to reinstall the app.',
        whatIsDeleted:
          'Deleting your account permanently removes your login credentials, display name, learning progress (lessons, practice history, vocabulary statistics) and streaks. This cannot be undone.',
        loading: 'Checking your session…',
        signedInAs: 'Signed in as {{name}}.',
        deleteButton: 'Delete my account',
        deleteSuccess: 'Your account has been deleted. You have been signed out.',
        deleteError: 'Could not delete your account. Please try again.',
        guestNotice:
          "You're currently browsing as a guest, which isn't a saved account yet. If you have an account you'd like to delete, first log out of this guest session below, then sign in with that account.",
        notSignedIn: {
          lead:
            'To delete your account, first sign in below. You can also delete your account directly inside the app: open Shikhi, go to Profile, and tap "Delete account".',
        },
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
        showPassword: 'পাসওয়ার্ড দেখান',
        hidePassword: 'পাসওয়ার্ড লুকান',
        rememberMe: 'আমার ইমেইল মনে রাখুন',
        displayName: 'নাম',
        submitting: 'অপেক্ষা করুন…',
        logout: 'লগ আউট',
        greeting: 'স্বাগতম, {{name}}!',
        learner: 'শিক্ষার্থী',
        genericError: 'কিছু একটা সমস্যা হয়েছে। আবার চেষ্টা করুন।',
        or: 'অথবা',
        guestCta: 'অ্যাকাউন্ট ছাড়াই চেষ্টা করুন →',
        guestBadge: 'আপনি অতিথি হিসেবে দেখছেন।',
      },
      guest: {
        title: 'আপনার অগ্রগতি সংরক্ষণ করুন',
        lead: 'আপনি অতিথি হিসেবে শিখছেন। অগ্রগতি সংরক্ষণ করতে একটি অ্যাকাউন্ট তৈরি করুন।',
        save: 'আমার অগ্রগতি সংরক্ষণ করুন',
        createAccount: 'অ্যাকাউন্ট তৈরি করুন',
        emailTaken:
          'এই ইমেইলে ইতিমধ্যে একটি অ্যাকাউন্ট আছে। বরং লগ ইন করুন — এই সেশনের অতিথি অগ্রগতি সংরক্ষিত হবে না।',
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
        count: '{{total}}টির মধ্যে {{from}}-{{to}} দেখানো হচ্ছে',
        pagerLabel: 'শব্দ তালিকার পৃষ্ঠা',
        prev: 'পূর্ববর্তী',
        next: 'পরবর্তী',
        page: '{{pages}}টির মধ্যে {{page}} নম্বর পৃষ্ঠা',
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
      practice: {
        heroEyebrow: 'দৈনিক অনুশীলন',
        heroTitle: 'আজকের অনুশীলন',
        heroCopy: 'আপনার {{level}} স্তরের শব্দ থেকে নতুন অনুশীলন — সাথে ছোট ছোট বাক্য।',
        levelBadge: 'আপনার স্তর: {{level}}',
        pickLevel: 'আপনার স্তর বেছে নিন',
        bands: {
          A1: 'একদম শুরু',
          A2: 'প্রাথমিক',
          B1: 'মধ্যম',
          B2: 'উচ্চতর',
          C1: 'দক্ষ',
        },
        start: 'সেশন শুরু করুন',
        loading: 'আপনার সেশন তৈরি হচ্ছে…',
        error: 'সেশন শুরু করা যায়নি।',
        backHome: 'হোমে ফিরে যান',
        exit: 'সেশন ছাড়ুন',
        progress: '{{total}}টির মধ্যে {{done}}টির উত্তর দেওয়া হয়েছে',
        toSummary: 'রাউন্ডের ফলাফল দেখুন',
        roundTitle: 'রাউন্ড শেষ!',
        roundScore: '{{total}}টির মধ্যে {{correct}}টি সঠিক',
        sessionScore: 'সেশন: {{total}}টির মধ্যে {{correct}}টি সঠিক',
        xpEarned: '+{{xp}} XP',
        keepGoing: 'চালিয়ে যান',
        finish: 'আজ এ পর্যন্তই',
        resultsTitle: 'সেশন সম্পন্ন!',
        levelUpOffer: 'আপনি এই স্তরের বেশিরভাগ শব্দ আয়ত্ত করেছেন — {{level}} এ যেতে প্রস্তুত?',
        levelUpAccept: '{{level}} এ উন্নীত হোন',
        levelUpDone: 'আপনার স্তর এখন {{level}}। নতুন সেশনে এটি ব্যবহৃত হবে।',
      },
      onboarding: {
        title: 'শিখিতে স্বাগতম!',
        welcome: 'বাংলায় ধাপে ধাপে ইংরেজি শিখুন। এক ট্যাপেই শুরু হবে আপনার প্রথম অনুশীলন।',
        placement: 'কোথা থেকে শুরু করবেন? আপনার উপযুক্ত স্তরটি বেছে নিন — যেকোনো সময় বদলানো যাবে।',
        skip: 'এড়িয়ে যান — A1 থেকে শুরু',
      },
      profile: {
        open: 'প্রোফাইল খুলুন',
        openFromStats: 'আপনার অগ্রগতি — প্রোফাইল খুলুন',
        title: 'প্রোফাইল',
        cardTitle: 'আপনার বিবরণ',
        loading: 'আপনার প্রোফাইল লোড হচ্ছে…',
        error: 'আপনার প্রোফাইল লোড করা যায়নি।',
        save: 'সংরক্ষণ করুন',
        cancel: 'বাতিল করুন',
        editName: 'নাম সম্পাদনা করুন',
        language: 'ভাষা',
        joined: '{{date}} তারিখে যোগ দিয়েছেন',
        stats: {
          longestStreak: 'দীর্ঘতম ধারা',
          dailyGoal: 'দৈনিক লক্ষ্য',
          reviewDue: 'পুনরালোচনা বাকি',
          lessonsCompleted: 'সম্পন্ন পাঠ',
          practiceSessions: 'অনুশীলন সেশন',
          accuracy: 'সামগ্রিক নির্ভুলতা',
        },
        mastery: {
          title: 'আয়ত্ত করা শব্দ',
          count: '{{total}}টির মধ্যে {{mastered}}টি',
          level: '{{level}} আয়ত্ত',
        },
        actions: {
          title: 'অ্যাকাউন্ট',
          export: 'আমার তথ্য ডাউনলোড করুন',
          exportError: 'আপনার তথ্য এক্সপোর্ট করা যায়নি।',
          delete: 'অ্যাকাউন্ট মুছে ফেলুন',
          deleteConfirmPrompt: 'এটি আপনার অ্যাকাউন্ট ও সব তথ্য স্থায়ীভাবে মুছে ফেলবে। আপনি কি নিশ্চিত?',
          deleteConfirm: 'হ্যাঁ, মুছে ফেলুন',
          deleteError: 'আপনার অ্যাকাউন্ট মুছে ফেলা যায়নি।',
        },
      },
      legal: {
        navLabel: 'আইনি পাতা',
        backToApp: '← শিখিতে ফিরে যান',
        privacyLink: 'গোপনীয়তা নীতি',
        deleteAccountLink: 'অ্যাকাউন্ট মুছে ফেলুন',
        privacy: {
          title: 'গোপনীয়তা নীতি',
          effectiveDate: 'কার্যকর তারিখ: {{date}}',
          intro:
            'শিখি ("অ্যাপ", "আমরা") বাংলাভাষীদের জন্য একটি ইংরেজি শেখার অ্যাপ, যা অ্যান্ড্রয়েড অ্যাপ ও ওয়েব অ্যাপ হিসেবে পাওয়া যায়। এই পাতায় আমরা কী তথ্য সংগ্রহ করি, কেন করি এবং কীভাবে তা মুছে ফেলা যায় তা ব্যাখ্যা করা হয়েছে।',
          contact: 'এই নীতি নিয়ে প্রশ্ন থাকলে {{email}} ঠিকানায় যোগাযোগ করুন।',
          sections: {
            dataTitle: 'আমরা যে তথ্য সংগ্রহ করি',
            dataBody:
              'আপনি অ্যাকাউন্ট তৈরি করলে আমরা আপনার ইমেইল ঠিকানা ও পাসওয়ার্ড সংগ্রহ করি। পাসওয়ার্ড কখনো সরাসরি সংরক্ষণ করা হয় না — শুধু এর একটি সুরক্ষিত ওয়ান-ওয়ে হ্যাশ রাখা হয়। আপনি ঐচ্ছিকভাবে একটি প্রদর্শন নামও যোগ করতে পারেন। শিখি ব্যবহারের সময় আমরা আপনার UI ভাষার পছন্দ এবং শেখার কার্যক্রম — পাঠ ও অনুশীলনের অগ্রগতি, শব্দভাণ্ডারের পরিসংখ্যান এবং ধারা (streak) — সংরক্ষণ করি। ইমেইল ছাড়াও আপনি অতিথি হিসেবে শিখি ব্যবহার করতে পারেন; পূর্ণ অ্যাকাউন্ট তৈরি না করা পর্যন্ত অতিথির অগ্রগতি একটি সাময়িক অ্যাকাউন্টের সাথে যুক্ত থাকে।',
            useTitle: 'আমরা কীভাবে আপনার তথ্য ব্যবহার করি',
            useBody:
              'আমরা এই তথ্য শুধু শিখি শেখার সেবা প্রদান ও আপনার ডিভাইসগুলোর মধ্যে সিঙ্ক করতে ব্যবহার করি — যেমন আপনার অগ্রগতি ট্র্যাক করা, আপনার স্তর অনুযায়ী অনুশীলন মানানসই করা এবং আপনার পছন্দ মনে রাখা। আমরা কোনো বিজ্ঞাপন দেখাই না এবং আপনার তথ্য বিক্রি করি না।',
            sharingTitle: 'তথ্য শেয়ারিং',
            sharingBody:
              'বিজ্ঞাপন বা মার্কেটিংয়ের জন্য আমরা তৃতীয় পক্ষের সাথে আপনার তথ্য শেয়ার করি না। শিখি যেসব অবকাঠামো প্রদানকারীর ওপর চলে তারা আমাদের পক্ষে তথ্য প্রক্রিয়া করে: আমাদের ব্যাকএন্ড Render-এ, ডেটাবেজ Neon (PostgreSQL)-এ, এবং ওয়েব অ্যাপ Cloudflare Pages-এ হোস্ট করা হয়। এই প্রদানকারীরা নিজেদের উদ্দেশ্যে আপনার তথ্য ব্যবহার করে না।',
            securityTitle: 'নিরাপত্তা',
            securityBody:
              'আপনার ডিভাইস ও আমাদের সার্ভারের মধ্যে পাঠানো সব তথ্য চলাচলের সময় এনক্রিপ্ট করা থাকে (HTTPS/TLS)। পাসওয়ার্ড কখনো প্লেইন টেক্সটে সংরক্ষণ করা হয় না — শুধু একটি ওয়ান-ওয়ে হ্যাশ রাখা হয়।',
            deletionTitle: 'আপনার তথ্য মুছে ফেলা',
            deletionBody:
              'আপনি যেকোনো সময় আপনার অ্যাকাউন্ট ও সংশ্লিষ্ট সব শেখার তথ্য স্থায়ীভাবে মুছে ফেলতে পারেন — অ্যাপের ভেতরে (প্রোফাইল → অ্যাকাউন্ট মুছে ফেলুন) অথবা নিচে সংযুক্ত অ্যাকাউন্ট-মুছুন পাতা ব্যবহার করে। মুছে ফেলা তাৎক্ষণিক ও স্থায়ী — এটি ফিরিয়ে আনা যায় না।',
            childrenTitle: 'শিশুরা',
            childrenBody:
              'শিখি সাধারণ দর্শকদের জন্য তৈরি, যারা ইংরেজি শিখছেন। যদি আপনার মনে হয় কোনো শিশু উপযুক্ত সম্মতি ছাড়া আমাদের ব্যক্তিগত তথ্য দিয়েছে, দয়া করে আমাদের সাথে যোগাযোগ করুন — আমরা তা মুছে ফেলব।',
            changesTitle: 'এই নীতিতে পরিবর্তন',
            changesBody:
              'শিখির বিকাশের সাথে সাথে আমরা এই নীতি হালনাগাদ করতে পারি। তখন উপরের কার্যকর তারিখ পরিবর্তন করা হবে।',
          },
        },
      },
      deleteAccount: {
        title: 'আপনার অ্যাকাউন্ট মুছে ফেলুন',
        intro:
          'অ্যাপ পুনরায় ইনস্টল না করেই আপনি এই পাতা থেকে আপনার শিখি অ্যাকাউন্ট ও সব শেখার তথ্য মুছে ফেলার অনুরোধ করতে পারেন।',
        whatIsDeleted:
          'অ্যাকাউন্ট মুছে ফেললে আপনার লগইন তথ্য, প্রদর্শন নাম, শেখার অগ্রগতি (পাঠ, অনুশীলনের ইতিহাস, শব্দভাণ্ডার পরিসংখ্যান) এবং ধারা স্থায়ীভাবে মুছে যায়। এটি ফিরিয়ে আনা যায় না।',
        loading: 'আপনার সেশন পরীক্ষা করা হচ্ছে…',
        signedInAs: '{{name}} হিসেবে সাইন ইন করা আছেন।',
        deleteButton: 'আমার অ্যাকাউন্ট মুছে ফেলুন',
        deleteSuccess: 'আপনার অ্যাকাউন্ট মুছে ফেলা হয়েছে। আপনাকে সাইন আউট করা হয়েছে।',
        deleteError: 'আপনার অ্যাকাউন্ট মুছে ফেলা যায়নি। আবার চেষ্টা করুন।',
        guestNotice:
          'আপনি বর্তমানে অতিথি হিসেবে ব্রাউজ করছেন, যা এখনো সংরক্ষিত অ্যাকাউন্ট নয়। যদি আপনার এমন কোনো অ্যাকাউন্ট থাকে যা মুছে ফেলতে চান, প্রথমে নিচে এই অতিথি সেশন থেকে লগ আউট করুন, তারপর সেই অ্যাকাউন্টে সাইন ইন করুন।',
        notSignedIn: {
          lead:
            'আপনার অ্যাকাউন্ট মুছে ফেলতে প্রথমে নিচে সাইন ইন করুন। আপনি চাইলে সরাসরি অ্যাপের ভেতর থেকেও মুছে ফেলতে পারেন: শিখি খুলুন, প্রোফাইলে যান, এবং "অ্যাকাউন্ট মুছে ফেলুন"-এ ট্যাপ করুন।',
        },
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
