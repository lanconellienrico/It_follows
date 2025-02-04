package com.example.testapplication

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * History Activity -> display recorded activities desc from the most recent one,
 * it provides two filtered searches:
 * - by Activity Type
 * - by Date
 * A button to clear every trace it's also available.
 *
 * in prose:
 *  History Activity shows us the ancient quests and the almighty deeds of an almost forgotten time
 *  but in step with the times, offers the most recent filter available on the market: the TypeDate Filter
 */
@RequiresApi(Build.VERSION_CODES.O)
class HistoryActivity: AppCompatActivity() {

    // graphic elements
    private lateinit var filterByType: CheckBox
    private lateinit var activitySpinner: Spinner
    private lateinit var clearButton: Button
    private lateinit var sampleText: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var filterByDate: CheckBox
    private lateinit var setDateButton: Button
    private lateinit var dateToFilterTextView: TextView

    // date elements
    private var day: Int = 0
    private var month: Int = 0
    private var year: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_activity)

        // routine setup
        setupUI()
        setTodayDate()
        getActivities()

        // add listener to the buttons
        clearButton.setOnClickListener {
            clearHistory()
        }
        filterByType.setOnCheckedChangeListener { _,_ ->
            filter()
        }
        filterByDate.setOnCheckedChangeListener { _, _ ->
            filter()
        }
        setDateButton.setOnClickListener {
            datePickerDialog()
        }
    }

    // associate local vars to layout elements
    private fun setupUI(){
        filterByType = findViewById(R.id.activity_filter)
        activitySpinner = findViewById(R.id.spinner_history)
        clearButton = findViewById(R.id.clear_button)
        sampleText = findViewById(R.id.sample_text)
        historyContainer = findViewById(R.id.history_container)
        filterByDate = findViewById(R.id.calender_check_box)
        setDateButton = findViewById(R.id.set_date_button)
        dateToFilterTextView = findViewById(R.id.date_to_filter_text)
    }

    // set current day date and display it
    private fun setTodayDate(){
        val calendar= Calendar.getInstance()
        year = calendar.get(Calendar.YEAR)
        month = calendar.get(Calendar.MONTH)
        day = calendar.get(Calendar.DAY_OF_MONTH)
        val todayDateString = "$day ${getMonthName(month)} $year"
        dateToFilterTextView.text = todayDateString
    }


    // get every activity from Db and pass them to addActivityText to be shown
    private fun getActivities(){
        val dao = Db.getDb(this).activityEntityDao()

        CoroutineScope(Dispatchers.IO).launch {
            val recordedActivities = dao.getActivities()
            withContext(Dispatchers.Main) {
                recordedActivities.forEach { act ->
                    addActivityText(act.toPrint())
                }
            }
        }
    }

    // get activities from Db according to their Type
    private fun getActivitiesByType(type: String){
        val dao = Db.getDb(this).activityEntityDao()

        CoroutineScope(Dispatchers.IO).launch {
            val recordedActivities = dao.getActivitiesByType(type)
            withContext(Dispatchers.Main) {
                recordedActivities.forEach { act ->
                    addActivityText(act.toPrint())
                }
            }
        }
    }

    private fun getActivitiesByDate(){
        val dao = Db.getDb(this).activityEntityDao()
        val dayStart = dateConverter(year, month, day, true)
        val dayEnd = dateConverter(year, month, day, false)

        CoroutineScope(Dispatchers.IO).launch {
            val recordedActivities = dao.getActivitiesByDate(dayStart, dayEnd)
            withContext(Dispatchers.Main) {
                recordedActivities.forEach { act ->
                    addActivityText(act.toPrint())
                }
            }
        }
    }

    private fun getActivitiesByDateAndType(type: String){
        val dao = Db.getDb(this).activityEntityDao()
        val dayStart = dateConverter(year, month, day, true)
        val dayEnd = dateConverter(year, month, day, false)

        CoroutineScope(Dispatchers.IO).launch {
            val recordedActivities = dao.getActivitiesByDateAndType(dayStart, dayEnd, type)
            withContext(Dispatchers.Main) {
                recordedActivities.forEach { act ->
                    addActivityText(act.toPrint())
                }
            }
        }
    }

    // create a textView to show a passed activity
    private fun addActivityText(text: String){
        // copy paste of sampleStyle
        val newTextView = TextView(ContextThemeWrapper(
            this, R.style.historyActivity)).apply {
            this.text = text
        }

        // set layout_MarginBottom here cause Android still has a long way to go
        val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(0, 16, 0, 0)
        newTextView.layoutParams = layoutParams

        // add textView to the Layout Container
        historyContainer.addView(newTextView)
    }

    // delete every activity entry in the Db and remove all view from ScrollViewContainer
    private fun clearHistory(){
        val dao = Db.getDb(this).activityEntityDao()

        // Db delete call
        CoroutineScope(Dispatchers.IO).launch {
            dao.deleteEveryActivity()
            historyContainer.removeAllViewsInLayout()
        }
    }

    // when a filter checkbox state is changed, update shown history
    private fun filter(){
        historyContainer.removeAllViewsInLayout()

        val selectedType = activitySpinner.selectedItem.toString()

        when{
            filterByType.isChecked && filterByDate.isChecked -> getActivitiesByDateAndType(selectedType)
            filterByType.isChecked -> getActivitiesByType(selectedType)
            filterByDate.isChecked -> getActivitiesByDate()
            else -> getActivities()
        }
    }

    // build a dialog box with a datePicker and update dateToFilterTextView
    private fun datePickerDialog(){
        val datePickerDialog = DatePickerDialog(this@HistoryActivity,
            R.style.datePicker,
            { _, y, m, d ->
                // update dateView and local values for year, month and day
                val newDateToFilter = "$d ${getMonthName(m)} $y"
                dateToFilterTextView.text = newDateToFilter
                year = y
                month = m
                day = d
            },
            year, month, day) // default date
        datePickerDialog.show()
    }

    // insert int -> exit relative month name
    private fun getMonthName(month: Int): String {
        return DateFormatSymbols().months[month]
    }

    // calc. dayStart or dayEnd - on given boolean par. - in Long MILLISECOND format
    private fun dateConverter(year: Int, month: Int, day: Int, start: Boolean): Long {
        val calendar = Calendar.getInstance()
        val dayRequest: Long

        if(start) {
            // set dayStart at 00:00
            calendar.set(year, month, day, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            dayRequest = calendar.timeInMillis
        } else{
            // set dayEnd at 23:59:59.999
            calendar.set(year, month, day,23,59,59)
            calendar.set(Calendar.MILLISECOND, 999)
            dayRequest = calendar.timeInMillis
        }
        return dayRequest
    }
}