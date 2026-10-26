package com.hapkidoscore.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var matchCode: EditText
    private lateinit var db: DatabaseReference
    private var matchId = ""
    private var isChief = false
    private var redScore = 0
    private var blueScore = 0
    private var redFaults = 0
    private var blueFaults = 0
    private var round = 1
    private var roundSeconds = 120
    private var restSeconds = 30
    private var timerRunning = false
    private var secondsLeft = 120
    private var timerHandler = android.os.Handler()
    private lateinit var timerText: TextView
    private lateinit var scoreText: TextView
    private lateinit var roundText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
        try {
            FirebaseApp.initializeApp(this)
            FirebaseAuth.getInstance().signInAnonymously().addOnSuccessListener {
                status.text = "آنلاین هستید؛ برای اتصال، مسابقه بسازید یا کد را وارد کنید."
            }.addOnFailureListener { status.text = "اتصال آنلاین تنظیم نشده؛ فایل google-services.json لازم است." }
        } catch (_: Exception) { status.text = "برای اتصال اینترنتی باید Firebase تنظیم شود." }
    }

    private fun buildUI() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 18); setBackgroundColor(Color.rgb(16,24,32)) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply { text = "🥋 HAPKIDO SCORE"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 12, 0, 18) }
        content.addView(title)
        status = TextView(this).apply { text = "راه‌اندازی برنامه…"; textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(0, 0, 0, 12) }
        content.addView(status)
        matchCode = EditText(this).apply { hint = "کد مسابقه برای پیوستن"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
        content.addView(matchCode)
        row(content, button("ساخت مسابقه", Color.rgb(46,125,50)) { createMatch() }, button("پیوستن با کد", Color.rgb(21,101,192)) { joinMatch(matchCode.text.toString().trim().uppercase()) })
        roundText = label("راند ۱")
        content.addView(roundText)
        timerText = label("02:00")
        content.addView(timerText)
        row(content, button("شروع / توقف زمان", Color.DKGRAY) { toggleTimer() }, button("پایان راند", Color.rgb(97,97,97)) { nextRound() })
        scoreText = label("قرمز  0  —  0  آبی")
        content.addView(scoreText)
        row(content, button("قرمز +1", Color.rgb(183,28,28)) { vote("red",1) }, button("قرمز +2", Color.rgb(183,28,28)) { vote("red",2) }, button("قرمز +3", Color.rgb(183,28,28)) { vote("red",3) })
        row(content, button("آبی +1", Color.rgb(21,101,192)) { vote("blue",1) }, button("آبی +2", Color.rgb(21,101,192)) { vote("blue",2) }, button("آبی +3", Color.rgb(21,101,192)) { vote("blue",3) })
        row(content, button("خطای قرمز", Color.rgb(183,28,28)) { fault("red") }, button("خطای آبی", Color.rgb(21,101,192)) { fault("blue") })
        row(content, button("تأیید نتیجه (سرداور)", Color.rgb(239,108,0)) { finalizeMatch() }, button("مسابقه جدید / صفر کردن", Color.DKGRAY) { resetLocal() })
        val note = TextView(this).apply { text = "نکته: برای رأی‌گیری هم‌زمان چند داور، ابتدا Firebase را طبق راهنمای داخل فایل ZIP تنظیم کنید."; setTextColor(Color.LTGRAY); textSize = 12f; setPadding(0, 18, 0, 8) }
        content.addView(note)
        scroll.addView(content); root.addView(scroll); setContentView(root)
    }

    private fun label(text: String) = TextView(this).apply { this.text = text; textSize = 21f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, 12, 0, 12) }
    private fun button(text: String, color: Int, action: () -> Unit) = Button(this).apply { this.text = text; setTextColor(Color.WHITE); setBackgroundColor(color); textSize = 12f; setOnClickListener { action() } }
    private fun row(parent: LinearLayout, vararg views: View) { val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 3, 0, 3) }; views.forEach { r.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(3,2,3,2) }) }; parent.addView(r) }

    private fun createMatch() {
        matchId = (100000..999999).random().toString(); isChief = true
        matchCode.setText(matchId)
        status.text = "مسابقه ساخته شد؛ کد را به داورها بدهید: $matchId"
        if (firebaseReady()) {
            val ref = FirebaseDatabase.getInstance().reference.child("matches").child(matchId)
            ref.child("chiefUid").setValue(FirebaseAuth.getInstance().currentUser?.uid)
            ref.child("state").setValue(stateMap())
            ref.child("votes").setValue(mapOf<String, Any>())
            listen(ref)
        }
    }
    private fun joinMatch(code: String) {
        if (code.length != 6) { status.text = "کد مسابقه باید ۶ رقمی باشد."; return }
        matchId = code; isChief = false
        if (!firebaseReady()) { status.text = "کد ثبت شد، اما اتصال آنلاین هنوز تنظیم نشده است."; return }
        val ref = FirebaseDatabase.getInstance().reference.child("matches").child(matchId)
        ref.get().addOnSuccessListener { snap -> if (snap.exists()) { status.text = "به مسابقه $matchId وصل شدید."; listen(ref) } else status.text = "این کد مسابقه پیدا نشد." }
    }
    private fun firebaseReady(): Boolean = try { FirebaseApp.getInstance(); FirebaseAuth.getInstance().currentUser != null } catch (_: Exception) { false }
    private fun listen(ref: DatabaseReference) {
        ref.child("state").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { redScore = s.child("redScore").getValue(Int::class.java) ?: 0; blueScore = s.child("blueScore").getValue(Int::class.java) ?: 0; redFaults = s.child("redFaults").getValue(Int::class.java) ?: 0; blueFaults = s.child("blueFaults").getValue(Int::class.java) ?: 0; round = s.child("round").getValue(Int::class.java) ?: 1; secondsLeft = s.child("secondsLeft").getValue(Int::class.java) ?: roundSeconds; updateDisplay() }
            override fun onCancelled(e: DatabaseError) { status.text = "خطای اتصال: ${e.message}" }
        })
    }
    private fun vote(side: String, points: Int) {
        if (matchId.isEmpty()) { status.text = "ابتدا مسابقه بسازید یا به یک مسابقه بپیوندید."; return }
        if (firebaseReady()) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseDatabase.getInstance().reference.child("matches").child(matchId).child("votes").child(uid).setValue(mapOf("side" to side, "points" to points, "round" to round, "time" to System.currentTimeMillis()))
            status.text = "رأی شما ثبت شد؛ سرداور نتیجه رسمی را تأیید می‌کند."
        } else { if (side == "red") redScore += points else blueScore += points; updateDisplay(); status.text = "امتیاز محلی ثبت شد. برای رأی‌گیری مشترک، Firebase لازم است." }
    }
    private fun fault(side: String) {
        if (side == "red") { redFaults++; blueScore++ } else { blueFaults++; redScore++ }
        if (redFaults >= 6 || blueFaults >= 6) status.text = if (redFaults >= 6) "مبارز قرمز با ۶ خطا بازنده است." else "مبارز آبی با ۶ خطا بازنده است."
        syncState(); updateDisplay()
    }
    private fun syncState() { if (firebaseReady() && matchId.isNotEmpty() && isChief) FirebaseDatabase.getInstance().reference.child("matches").child(matchId).child("state").setValue(stateMap()) }
    private fun stateMap() = mapOf("redScore" to redScore, "blueScore" to blueScore, "redFaults" to redFaults, "blueFaults" to blueFaults, "round" to round, "secondsLeft" to secondsLeft)
    private fun updateDisplay() { scoreText.text = "قرمز $redScore  —  $blueScore آبی\nخطای قرمز: $redFaults     خطای آبی: $blueFaults"; roundText.text = "راند $round"; timerText.text = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60); syncState() }
    private fun toggleTimer() { timerRunning = !timerRunning; if (timerRunning) tick() }
    private fun tick() { if (!timerRunning) return; if (secondsLeft > 0) { secondsLeft--; timerText.text = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60); timerHandler.postDelayed({ tick() },1000) } else { timerRunning = false; status.text = "زمان راند تمام شد."; syncState() } }
    private fun nextRound() { timerRunning = false; if (round < 2) { round++; secondsLeft = roundSeconds; status.text = "راند بعدی آماده است." } else if (redScore == blueScore) { round = 3; secondsLeft = roundSeconds; status.text = "تساوی؛ راند طلایی شروع شود." } else status.text = if (redScore > blueScore) "برنده: قرمز" else "برنده: آبی"; updateDisplay() }
    private fun finalizeMatch() { if (!isChief) { status.text = "فقط سرداور می‌تواند نتیجه نهایی را تأیید کند."; return }; if (firebaseReady() && matchId.isNotEmpty()) FirebaseDatabase.getInstance().reference.child("matches").child(matchId).child("finalResult").setValue(mapOf("red" to redScore,"blue" to blueScore,"approved" to true,"time" to System.currentTimeMillis())); status.text = "نتیجه نهایی تأیید شد: قرمز $redScore — آبی $blueScore" }
    private fun resetLocal() { redScore=0; blueScore=0; redFaults=0; blueFaults=0; round=1; secondsLeft=roundSeconds; timerRunning=false; updateDisplay(); status.text="امتیازها صفر شدند." }
}
