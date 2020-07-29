package com.wpy.wpy_irent

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wpy.wpy_irent.AlarmNotification.AlarmSoundService
import com.wpy.wpy_irent.AlarmNotification.NotificationUtil
import com.wpy.wpy_irent.AlarmNotification.PrefUtil
import com.wpy.wpy_irent.AlarmNotification.TimerExpiredReceiver
import com.wpy.wpy_irent.Api.ApiService
import com.wpy.wpy_irent.Auth.HistoriPref
import com.wpy.wpy_irent.Auth.SharedPref
import com.wpy.wpy_irent.Auth.Utils
import com.wpy.wpy_irent.Model.Paket.PaketBatal
import com.wpy.wpy_irent.Model.Paket.PaketBatalRespon
import kotlinx.android.synthetic.main.activity_play.*
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*


class PlayActivity : AppCompatActivity(){

    var btnMulai: Button? = null
    var btnBatal: Button? = null
    private val a: PlayActivity = this

    companion object{
        private const val REQ_CODE=0
        fun setAlarm(context: Context, nowSeconds: Long, secondsRemaining: Long): Long{
            val wakeUpTime=(nowSeconds+secondsRemaining)*1000
            val alarmManager=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent= Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent=PendingIntent.getBroadcast(context, REQ_CODE, intent, 0)
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, wakeUpTime, pendingIntent)
            PrefUtil.setAlarmSetTime(nowSeconds, context)
            return wakeUpTime
        }

        fun removeAlarm(context: Context){
            val intent=Intent(context, TimerExpiredReceiver::class.java)
            val pendingIntent= PendingIntent.getBroadcast(context, REQ_CODE, intent, 0)
            val alarmManager=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            PrefUtil.setAlarmSetTime(0, context)
        }

        val nowSeconds: Long
            get() = Calendar.getInstance().timeInMillis/1000
    }

    enum class TimerState{
        STOPPED, RUNNING, FINISH
    }
    private var timer: CountDownTimer?=null
    private var timerLengthSeconds= 0L
    private var timerState= TimerState.STOPPED
    private var secondsRemaining= 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_play)
        btnMulai = findViewById(R.id.btnMulai)
        btnBatal = findViewById(R.id.btnBatal)
        btnMulai?.setOnClickListener {
            timerState=TimerState.RUNNING
            cekKondisi()
            startTimer()
            updateButtons()
        }
        btnBatal?.setOnClickListener {
            batalkanPaket()
            dibatalkan()
        }
    }
    private fun batalkanPaket(){
        var userId = SharedPref.getInstance(this).user.id_user
        var pakId = HistoriPref.getInstance(this).paket.id_paket
        val json = JSONObject()
        json.put("id_user",userId)
        json.put("id_paket",pakId)
        ApiService.loginApiCall().paketBatal(
            PaketBatal(
                userId,pakId
            )
        ).enqueue(object : Callback<PaketBatalRespon>{
            override fun onResponse(
                call: Call<PaketBatalRespon>,
                response: Response<PaketBatalRespon>
            ) {
                if (response.body()!!.status){
                    val dibatalkan = "Telah Dibatalkan"
                    Toast.makeText(applicationContext, "Paket " + dibatalkan, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<PaketBatalRespon>, t: Throwable) {
                Utils.showInfoDialog(a, "ERROR", "Mohon periksa koneksi internet anda!!")
            }

        })
    }

    private fun dibatalkan() {
        timer?.cancel()
        PrefUtil.setAlarmSetTime(0,this)
        PrefUtil.setTimerState(TimerState.STOPPED,this)
        PrefUtil.setTimerLength(0,this)
        PrefUtil.setPreviousTimerLengthSeconds(0,this)
        PrefUtil.setTimerSecondsRemaining(0,this)
        NotificationUtil.hideTimerNotification(this)
        removeAlarm(this)
        HistoriPref.getInstance(this).clear()
        this.stopService(Intent(Intent(this, AlarmSoundService::class.java)))
        val intent = Intent(this@PlayActivity, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)

        startActivity(intent)
        this@PlayActivity.finish()
    }

    private fun ambilWaktu(){
        val lengthInMinutes=PrefUtil.getTimerLength(this)
        timerLengthSeconds=lengthInMinutes*60L
        progressCountdown.max=timerLengthSeconds.toInt()
    }

    private fun cekKondisi(){
        timerState=PrefUtil.getTimerState(this)
        secondsRemaining=if(timerState==TimerState.RUNNING){
            PrefUtil.getTimerSecondsRemaining(this)
        }else{
            timerLengthSeconds
        }

        if(timerState==TimerState.STOPPED){
            ambilWaktu()
            updateButtons()
        }else if (timerState==TimerState.RUNNING){
            setPreviousTimerLength()
        }
        val alarmSetTime=PrefUtil.getAlarmSetTime(this)

        if(alarmSetTime>0){
            secondsRemaining-= nowSeconds-alarmSetTime
        }

        if (secondsRemaining<=0){
            if (timerState == TimerState.FINISH){
                NotificationUtil.showTimerExpired(this)
            }
            if (timerState == TimerState.STOPPED){
                ambilWaktu()
                updateButtons()
            }
        }else if(timerState==TimerState.RUNNING){
            startTimer()
        }
        updateCountdownUI()
        updateButtons()
    }
    override fun onResume() {
        cekKondisi()
        removeAlarm(this)
        NotificationUtil.hideTimerNotification(this)
        super.onResume()
    }

    override fun onPause() {
        if(timerState==TimerState.RUNNING){
//            timer?.cancel()
            val wakeUpTime= setAlarm(this, nowSeconds, secondsRemaining)
            NotificationUtil.showTimerRunning(this, wakeUpTime)
        }else if(timerState==TimerState.STOPPED){
            NotificationUtil.showTimerPaused(this)
        }else if (timerState==TimerState.FINISH){
            timer?.onFinish()
            NotificationUtil.showTimerExpired(this)
            startService(Intent(this,AlarmSoundService::class.java))
            yaudahSelesai()
        }
        PrefUtil.setPreviousTimerLengthSeconds(timerLengthSeconds, this)
        PrefUtil.setTimerSecondsRemaining(secondsRemaining, this)
        PrefUtil.setTimerState(timerState, this)
        super.onPause()

    }

    override fun onDestroy() {
        if(timerState==TimerState.RUNNING){
//            timer?.cancel()
            val wakeUpTime= setAlarm(this, nowSeconds, secondsRemaining)
            NotificationUtil.showTimerRunning(this, wakeUpTime)
        }else if(timerState==TimerState.STOPPED){
            NotificationUtil.showTimerPaused(this)
        } else if (timerState==TimerState.FINISH){
            timer?.onFinish()
            NotificationUtil.showTimerExpired(this)
            startService(Intent(this,AlarmSoundService::class.java))
            yaudahSelesai()
        }
        PrefUtil.setPreviousTimerLengthSeconds(timerLengthSeconds, this)
        PrefUtil.setTimerSecondsRemaining(secondsRemaining, this)
        PrefUtil.setTimerState(timerState, this)
        super.onDestroy()
    }

    private fun setPreviousTimerLength(){
        timerLengthSeconds=PrefUtil.getPreviousTimerLengthSeconds(this)
        progressCountdown.max=timerLengthSeconds.toInt()
    }

    private fun startTimer(){
        timerState=TimerState.RUNNING
        timer=object : CountDownTimer(secondsRemaining*1000, 1000){
            override fun onFinish() = yaudahSelesai()

            override fun onTick(millisUntilFinished: Long) {
                secondsRemaining=millisUntilFinished/1000
                updateCountdownUI()
            }
        }.start()
    }
    @SuppressLint("SetTextI18n")
    private fun updateCountdownUI(){
        val minutesUntilFinished=secondsRemaining/60
        val secondsInMinutesUntilFinished=secondsRemaining-(minutesUntilFinished*60)
        val secondsString=secondsInMinutesUntilFinished.toString()
        remaining_time.text="$minutesUntilFinished:${
        if(secondsString.length==2){secondsString}
        else{"0"+secondsString}}"
        progressCountdown.progress=(timerLengthSeconds-secondsRemaining).toInt()
    }
    private fun yaudahSelesai(){
        timerState=TimerState.FINISH
        txtInfo.text = "SELESAI"
        remaining_time.text = "Selesai.."
        progressCountdown.progress=0
        secondsRemaining=timerLengthSeconds
        val intent = Intent(this@PlayActivity, SelesaiActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun updateButtons(){
        when(timerState){
            TimerState.RUNNING->{
                txtInfo.visibility = View.GONE
                btnMulai?.visibility = View.GONE
                btnBatal?.visibility = View.VISIBLE
            }
            TimerState.STOPPED->{
                txtInfo.visibility = View.VISIBLE
                btnMulai?.visibility = View.VISIBLE
                btnBatal?.visibility = View.GONE
            }
            TimerState.FINISH->{
                txtInfo.visibility = View.VISIBLE
                btnMulai?.visibility = View.VISIBLE
                btnBatal?.visibility = View.GONE
            }
        }
    }


}


