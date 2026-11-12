package com.streamdesk.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** ☰ → Settings → Language: like the phone (default), Hebrew or English. */
enum class LangMode { System, Hebrew, English }

/**
 * The app's language. Screens show t("English text"): the Hebrew line from [HE] when Hebrew is on, else the English.
 * Hebrew also turns the phone's screens right-to-left. The TV stays in English (its remote moves left/right on the grid).
 */
object Lang {
    private fun prefs(context: Context) = context.getSharedPreferences("look", Context.MODE_PRIVATE)

    var mode by mutableStateOf(LangMode.System)
        private set

    /** Set on a TV: always English there. */
    @Volatile var tv = false

    val hebrew: Boolean
        get() = !tv && when (mode) {
            LangMode.Hebrew -> true
            LangMode.English -> false
            LangMode.System -> Locale.getDefault().language.let { it == "iw" || it == "he" }
        }

    fun load(context: Context) {
        tv = context.isTelevision()
        mode = runCatching { LangMode.valueOf(prefs(context).getString("language", null).orEmpty()) }.getOrDefault(LangMode.System)
    }

    fun set(context: Context, value: LangMode) {
        mode = value
        prefs(context).edit().putString("language", value.name).apply()
    }
}

/** The text in the app's language (the English text is the key). */
fun t(en: String): String = if (Lang.hebrew) HE[en] ?: en else en

/** "13 channels" / "13 ערוצים" (and "1 channel"). */
fun channelsCount(n: Int): String =
    if (n == 1) t("1 channel") else t("%,d channels").format(n)

private val HE = mapOf(
    // Tabs and titles
    "Sport" to "ספורט",
    "Israeli TV" to "ערוצי ישראל",
    "Favorites" to "מועדפים",
    "All" to "הכל",
    "Playlists" to "רשימות ערוצים",
    "My playlists" to "רשימות הערוצים שלי",
    "All channels" to "כל הערוצים",
    "1 channel" to "ערוץ אחד",
    "%,d channels" to "%,d ערוצים",
    "Live TV, everywhere" to "טלוויזיה חיה, בכל מקום",
    "Loading your channels…" to "טוען את הערוצים שלך…",
    "Version" to "גרסה",
    // Top bar, search, views
    "Menu" to "תפריט",
    "More options" to "אפשרויות נוספות",
    "Search channels" to "חיפוש ערוצים",
    "Close search" to "סגירת החיפוש",
    "Clear search" to "ניקוי החיפוש",
    "Show as grid" to "תצוגת רשת",
    "Show as list" to "תצוגת רשימה",
    "Back" to "חזרה",
    "Back to the channels" to "חזרה לערוצים",
    // Side menu
    "Channels" to "ערוצים",
    "Account & sharing" to "חשבון ושיתוף",
    "Add playlist" to "הוספת רשימת ערוצים",
    "Add channels" to "הוספת ערוצים",
    "Update all playlists" to "עדכון כל הרשימות",
    "Share account with QR code" to "שיתוף החשבון בקוד QR",
    "Send login to TV" to "שליחת פרטי הכניסה לטלוויזיה",
    "Continue from TV" to "המשך מהטלוויזיה",
    "Settings" to "הגדרות",
    "Help" to "עזרה",
    "Check for updates" to "בדיקת עדכונים",
    "Send problem report" to "שליחת דיווח על תקלה",
    "Problems from TV" to "תקלות מהטלוויזיה",
    "About" to "אודות",
    "updated" to "עודכן",
    "just now" to "הרגע",
    // Settings
    "Look" to "מראה",
    "Theme" to "ערכת נושא",
    "Phone" to "כמו הטלפון",
    "Dark" to "כהה",
    "Light" to "בהיר",
    "Language" to "שפה",
    "Video" to "וידאו",
    "Video quality" to "איכות וידאו",
    "Lower quality on mobile data" to "איכות נמוכה בגלישה סלולרית",
    "Player" to "נגן",
    "Small window when leaving" to "חלון קטן ביציאה מהאפליקציה",
    "CH + / − buttons on the picture" to "כפתורי ערוץ +/− על התמונה",
    "Sound with screen off" to "צליל כשהמסך כבוי",
    "Home screen" to "מסך הבית",
    "View" to "תצוגה",
    "List" to "רשימה",
    "Grid" to "רשת",
    "Open on last channel" to "פתיחה בערוץ האחרון",
    "Links" to "קישורים",
    "Open a stream link" to "פתיחת קישור לשידור",
    "Hide the link bar" to "הסתרת שורת הקישור",
    // Channels
    "Add to favorites" to "הוספה למועדפים",
    "Remove from favorites" to "הסרה מהמועדפים",
    "Channel options" to "אפשרויות ערוץ",
    "Rename" to "שינוי שם",
    "Save to channels" to "שמירה בערוצים",
    "TV guide" to "לוח שידורים",
    "Play on TV" to "הפעלה בטלוויזיה",
    "Copy link" to "העתקת קישור",
    "Delete channel" to "מחיקת ערוץ",
    "Link copied." to "הקישור הועתק.",
    "No channels match your search." to "אין ערוצים שמתאימים לחיפוש.",
    "No favorites yet. Tap the star next to a channel to pin it here." to "אין עדיין מועדפים. הקישו על הכוכב ליד ערוץ כדי להוסיף אותו לכאן.",
    "No Israeli channels found. They show up here once your account has Channel 11, 12, 13, 14 or 15." to "לא נמצאו ערוצי ישראל. הם יופיעו כאן כשבחשבון שלך יהיו ערוצים 11, 12, 13, 14 או 15.",
    "No sport channels found. They show up here once your account has Sport 1–6, Sport 5 or ONE." to "לא נמצאו ערוצי ספורט. הם יופיעו כאן כשבחשבון שלך יהיו ספורט 1–6, ספורט 5 או ONE.",
    "No channels yet. Paste a stream link above, or import an M3U playlist with the + button." to "אין עדיין ערוצים. הדביקו קישור לשידור למעלה, או הוסיפו רשימת ערוצים בכפתור +.",
    // Playlists
    "Show channels" to "הצגת הערוצים",
    "Edit login" to "עריכת פרטי הכניסה",
    "Turn off" to "כיבוי",
    "Turn on" to "הפעלה",
    "Share with QR code" to "שיתוף בקוד QR",
    "Show logs" to "הצגת יומן",
    "Remove playlist" to "הסרת הרשימה",
    "Update playlist" to "עדכון הרשימה",
    "Playlist options" to "אפשרויות הרשימה",
    "Choose the account" to "בחירת חשבון",
    "The others stay saved, just turned off." to "השאר נשמרים, רק כבויים.",
    "Manage playlists" to "ניהול הרשימות",
    "Now showing %s." to "מוצג עכשיו: %s.",
    "Signed in as" to "מחובר כ־",
    "not loaded yet" to "עדיין לא נטען",
    "(off)" to "(כבוי)",
    // Messages and dialogs
    "Cancel" to "ביטול",
    "OK" to "אישור",
    "Close" to "סגירה",
    "Continue" to "המשך",
    "Copy" to "העתקה",
    "Save" to "שמירה",
    "Open" to "פתיחה",
    "Add" to "הוספה",
    "Remove" to "הסרה",
    "Send" to "שליחה",
    "Reset" to "איפוס",
    "Clear" to "ניקוי",
    "You have the latest version." to "יש לך את הגרסה האחרונה.",
    "Couldn't check for updates." to "לא הצלחנו לבדוק עדכונים.",
    "Account added. Loading the channels…" to "החשבון נוסף. טוען את הערוצים…",
    "Sign in to an account first (+ Add playlist → Login)." to "קודם צריך להתחבר לחשבון (+ הוספת רשימת ערוצים ← כניסה).",
    "Tap ⋮ next to the account → Share with QR code." to "הקישו ⋮ ליד החשבון ← שיתוף בקוד QR.",
    "Tap ⋮ next to the account → Send login to TV." to "הקישו ⋮ ליד החשבון ← שליחת פרטי הכניסה לטלוויזיה.",
    "Report copied." to "הדיווח הועתק.",
    "Details copied." to "הפרטים הועתקו.",
    "Problem report" to "דיווח על תקלה",
    "Copy details" to "העתקת הפרטים",
    "Livio stopped unexpectedly" to "Livio נסגרה באופן לא צפוי",
    "Live TV from your IPTV account on the phone, the TV and the PC: Sport and Israeli TV lists, the TV guide, favorites, and sharing an account with a QR code." to
        "טלוויזיה חיה מחשבון ה־IPTV שלך בטלפון, בטלוויזיה ובמחשב: ערוצי ספורט וערוצי ישראל, לוח שידורים, מועדפים ושיתוף חשבון בקוד QR.",
    // Add channels
    "Login" to "כניסה",
    "Link" to "קישור",
    "QR code" to "קוד QR",
    "Playlist link" to "קישור לרשימת ערוצים",
    "Name (optional)" to "שם (לא חובה)",
    "Open a .m3u file" to "פתיחת קובץ ‎.m3u",
    "Paste text" to "הדבקת טקסט",
    "Or:" to "או:",
    "Sign in" to "כניסה",
    "Pick the picture" to "בחירת התמונה",
    "Stream or playlist link" to "קישור לשידור או לרשימה",
)
