# ScreenTime App — Dajiraj ke liye
## Installation Guide (Free · 15 minutes · No experience needed)

---

## YEH APP KYA KARTA HAI

✅ **Auto-tracks** — Android ke UsageStatsManager se real data leta hai. Koi manual input nahi.
✅ **Notification bar** — Hamesha dikhata hai: `⏳ 2:34 · 58%` (time + goal percentage)
✅ **Background service** — Phone ON ho ya screen band, service chalta rehta hai
✅ **Phone restart** — Reboot ke baad bhi auto-start hota hai
✅ **Dark theme** — Pure black + saffron
✅ **Hindi / English** toggle
✅ **Hourly bar chart** — 24 bars, har ghante ka breakdown
✅ **Rewards** — 3 din streak mein "🔥 Shabash!", kam use pe "🌱"
✅ **Alerts** — 80%, 100%, 150% pe notification
✅ **Year review** — Poore saal ka record, Dec 31 summary
✅ **Special dates** — Calendar mein saffron ring

---

## STEP 1: Android Studio Install Karein (FREE)

1. https://developer.android.com/studio pe jao
2. "Download Android Studio" click karo
3. Install karo (Windows/Mac/Linux — sab pe chalta hai)
4. Pehli baar kholne pe wizard aayega — sab kuch default rakho, "Next" dabaate raho

**Time: ~10-20 minutes (download + install)**

---

## STEP 2: Project Open Karein

1. Android Studio kholo
2. "Open" click karo (ya File → Open)
3. **ScreenTimeApp** folder select karo (jo zip mein tha)
4. "OK" click karo
5. Wait karo — neeche "Gradle sync" hoga (~5 min)

---

## STEP 3: Phone Setup

### Phone pe Developer Mode ON karo:
1. Settings → About Phone → Software Information
2. **"Build number"** pe 7 baar tap karo quickly
3. "You are now a developer!" message aayega

### USB Debugging ON karo:
1. Settings → Developer Options
2. **"USB Debugging"** ON karo

### Phone Computer se connect karo:
1. USB cable se connect karo
2. Phone pe popup aayega "Allow USB Debugging?" — **"Always Allow"** karo

---

## STEP 4: App Install Karo

1. Android Studio mein upar device dropdown mein **Dajiraj ka phone** select karo
2. Green **▶ Run** button dabaao
3. App compile hoga aur phone pe install ho jaayega
4. **Done!**

---

## STEP 5: Usage Access Permission (ZAROORI)

Pehli baar app kholte hi ek dialog aayega:

**"Settings kholo"** button dabaao → phir:
1. **Settings → Apps → Special App Access → Usage Access**
2. **ScreenTime** app dhundho
3. Toggle **ON** karo

**Yahi woh permission hai jo app ko Android se real screen time data deti hai.**

---

## NOTIFICATION BAR

Install hote hi notification bar mein dikhega:
```
⏳  2:34  ·  58%
Goal: 4h · Remaining: 145min
```

Yeh **hamesha** dikhai deta hai — band nahi hota. Har 60 seconds mein update.

**Emoji changes with usage:**
- `⏳` Under 25% of goal
- `⌛` 25-50%
- `⏰` 50-75%
- `🔔` Near goal
- `🚨` Over goal

---

## GOAL KAISE SET KAREIN

App mein — header pe **long press** karo (1 second hold) → Goal dialog aayega.
2H / 3H / 4H / 5H / 6H / 7H choose karo.

---

## SAMSUNG M16 SPECIFIC NOTE

Samsung ka **Digital Wellbeing** pehle se background apps ko kill karta hai.
Iska solution:

1. Settings → Apps → ScreenTime → Battery
2. **"Unrestricted"** select karo
3. Settings → Battery → Background Usage Limits
4. ScreenTime ko **exceptions** mein add karo

---

## TROUBLESHOOTING

**"Usage data nahi dikh raha"**
→ Usage Access permission check karo (Step 5)

**"Notification band ho jaata hai"**
→ Battery settings mein "Unrestricted" karo (upar dekho)

**"Data galat lag raha hai"**
→ Android ka UsageStats kabhi kabhi 1-2 min delay deta hai — normal hai

---

## APP UNINSTALL

Normal Android uninstall — Settings → Apps → ScreenTime → Uninstall

---

*Made with ❤️ for Dajiraj*
