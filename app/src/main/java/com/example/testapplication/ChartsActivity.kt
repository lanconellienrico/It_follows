package com.example.testapplication

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import org.eazegraph.lib.charts.PieChart
import org.eazegraph.lib.models.PieModel
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * ChartsActivity offers two Charts Views, which are displayed one at a time and ruled by a Switch.
 * Pie Chart -> shows activities performed over the last thirty days based on total duration,
 * Bar Chart -> shows last thirty days steps.
 */

@RequiresApi(Build.VERSION_CODES.O)
class ChartsActivity: AppCompatActivity() {

    // graphic elements
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switch: Switch
    // pie_chart stuff
    private lateinit var pie: PieChart
    private lateinit var pieText: TextView
    private lateinit var pieBackground: View
    private lateinit var walkingColor: View
    private lateinit var runningColor: View
    private lateinit var drivingColor: View
    private lateinit var ridingColor: View
    private lateinit var sittingColor: View
    private lateinit var swimmingColor: View
    private lateinit var walkingTextView: TextView
    private lateinit var runningTextView: TextView
    private lateinit var drivingTextView: TextView
    private lateinit var ridingTextView: TextView
    private lateinit var sittingTextView: TextView
    private lateinit var swimmingTextView: TextView
    // bar_chart stuff
    private lateinit var bar: BarChart
    private lateinit var barText: TextView
    private lateinit var barValueText: TextView
    private lateinit var barDescription: TextView

    // utility
    private var pieStuff = mutableListOf<View>()          // family of pieChart's elements
    private var pieData = mutableMapOf<String, Float>()   // pieChart's data from Db
    private var barStuff = mutableListOf<View>()          // family of barChart's elements

    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContentView(R.layout.charts_activity)

        setupUI()

        // get data from Db and draw graphs
        getLast30DaysActivitiesThanPie()
        getLast30DaysStepsThanBarChart()

        // add listener to the switch to swift graph shown
        switch.setOnCheckedChangeListener { _,isOn ->
            hideAndShow(isOn)
        }
    }

    private fun setupUI(){
        switch = findViewById(R.id.charts_switch)

        // pieChart stuff
        pie = findViewById(R.id.pie)
        pieText = findViewById(R.id.pie_description_text)
        pieBackground = findViewById(R.id.pie_background)
        walkingColor = findViewById(R.id.color1)
        runningColor = findViewById(R.id.color2)
        drivingColor = findViewById(R.id.color3)
        ridingColor = findViewById(R.id.color4)
        sittingColor = findViewById(R.id.color5)
        swimmingColor = findViewById(R.id.color6)
        walkingTextView = findViewById(R.id.description1)
        runningTextView = findViewById(R.id.description2)
        drivingTextView = findViewById(R.id.description3)
        ridingTextView = findViewById(R.id.description4)
        sittingTextView = findViewById(R.id.description5)
        swimmingTextView = findViewById(R.id.description6)
        // add elements to PieStuff list
        pieStuff.addAll(listOf(pie, pieText, pieBackground))
        pieStuff.addAll(listOf(walkingColor, runningColor, drivingColor, ridingColor, sittingColor, swimmingColor))
        pieStuff.addAll(listOf(walkingTextView, runningTextView, drivingTextView, ridingTextView, sittingTextView, swimmingTextView))

        // barChart stuff and add to list
        bar = findViewById(R.id.bar_chart)
        barText = findViewById(R.id.hist_description_text)
        barValueText = findViewById(R.id.bar_value_text)
        barDescription = findViewById(R.id.bar_description)
        barStuff.addAll(listOf(bar, barText, barValueText, barDescription))
    }

    // SWITCH BUTTON -> show the selected graph*, hide the other one (* all its stuff)
    private fun hideAndShow(isOn: Boolean){
        if(isOn){
            pieStuff.forEach { p -> p.visibility = View.INVISIBLE }
            barStuff.forEach { b -> b.visibility = View.VISIBLE }
        } else{
            pieStuff.forEach { p -> p.visibility = View.VISIBLE }
            barStuff.forEach { b -> b.visibility = View.INVISIBLE }
        }
    }

    /** *******************************************************************************************************************
     * PIE CHART METHODS[ last 30 days activities] :
     * getLast30DaysActivitiesThanPie : gets data from Db, than calls Maker, finally Baker
     * pieMaker : organizes data, calc. percentages and pass them to the PieChart
     * pieBaker : finally make the PieChart, once slice at a time.
     * getActivityColor : ensure every activity (so every slice) with the relative color                               */

    // get last 30 days activity history
    private fun getLast30DaysActivitiesThanPie(){
        val rightNow = System.currentTimeMillis()
        val thirtyDaysAgo = rightNow - (86400*30).toLong()*1000
        val dao = Db.getDb(this).activityEntityDao()

        CoroutineScope(Dispatchers.IO).launch {
            val recordedActivities = dao.getActivitiesByDate(thirtyDaysAgo, rightNow)
            pieMaker(recordedActivities)

            // back to main thread with the ingredients, so that we can have that pie wanted so bad
            withContext(Dispatchers.Main) {
                pieBaker()
            }
        }
    }

    // elaborate rec. activities, calc. % and fill pieData
    private fun pieMaker(recAct: List<ActivityEntity>){
        if(recAct.isEmpty()) return

        // calc. total time for each activity
        val totalTimeActivity = recAct.groupBy { it.activityType }
            .mapValues { (_, activities) ->
                activities.sumOf{
                    it.stopTime - it.startTime
                }
            }
        val totalTime = totalTimeActivity.values.sum()
        if(totalTime == 0L) return
        pieData.clear()

        // convert times in percentages
        totalTimeActivity.forEach{ (type, time) ->
            val percentage = (time.toDouble()/ totalTime*100).toFloat()
            pieData[type] = percentage
        }
    }

    // it's time to churn out a delicious pie (create slice of pie Chart and display finished pie)
    private fun pieBaker(){
        if(pieData.isNotEmpty()){
            pie.clearChart()
            pieData.forEach { (type, percentage) ->
                pie.addPieSlice(PieModel(
                    type, percentage, getActivityColor(type)))
            }
            pie.startAnimation()
        }
    }

    // give activity related color : Int
    private fun getActivityColor(type: String): Int{
        return when(type) {
            "walking" -> Color.parseColor("#E41B17")
            "running" -> Color.parseColor("#990012")
            "swimming" -> Color.parseColor("#3700B3")
            "sitting" -> Color.parseColor("#BB86FC")
            "riding" -> Color.parseColor("#F52887")
            "driving" -> Color.parseColor("#7D1B7E")
            else -> Color.parseColor("#FFFFFF")
        }
    }
    /** ****************************************************************************************************************/

    /** ***************************************************************************************************************
     * BAR CHART METHODS[ last 30 days DailySteps] :
     * - getLast30DaysStepsThanBarChart : fetch last 30 days data from Db and pass it to the Maker,
     * - barChartMaker : config. data and setup the chart + add on bar touched listener,
     * - giveMeGranul : calc. the steps on the Yaxis based on the max of steps taken.                                */

    // get last 30 days daily_steps counts
    private fun getLast30DaysStepsThanBarChart(){
        val dao = Db.getDb(this).dailyStepsEntityDao()

        // TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST
        // delete everything if needed *************************************
        /*
        CoroutineScope(Dispatchers.IO).launch {
            dao.deleteEveryDailySteps()
        }
        */
        // populate for test **********************************************
        /*
        val entries = mutableMapOf<Long, Int>()
        for(i in 1L..30L) {
            val steps = Random.nextInt(500, 15000)
            val day = LocalDate.now(ZoneId.of("Europe/Paris")).minusDays(i).toEpochDay()
            entries[day] = steps

        }
       CoroutineScope(Dispatchers.IO).launch {
           entries.forEach {
               dao.insert(DailyStepsEntity(date = it.key, steps = it.value))
           }
        }
        */
        // TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST

        // fetch data from Db and pass them to the barChart Maker
        CoroutineScope(Dispatchers.IO).launch {
            val recordedDailySteps = dao.getLastNDaysDailySteps(30)
            barChartMaker(recordedDailySteps)
        }
    }

    // config. data and load them into the Chart, set the Chart and finally display it
    private fun barChartMaker(dailySteps: List<DailyStepsEntity>){
        if(dailySteps.isEmpty()) {
            barStuff.forEach { b -> b.visibility = View.INVISIBLE }
            return
        } else{
            barValueText.visibility = View.INVISIBLE
            bar.clear()

            // get first and final day to display interval of recording
            val firstDay = dailySteps[dailySteps.size-1].getPrettyDate()
            val finalDay = dailySteps[0].getPrettyDate()

            // edit "last N days steps" to handle different registrations
            val newDescription = "Last ${dailySteps.size} days steps"
            barText.text = newDescription

            // make a list of ordered pairs to be BarEntry
            val pairedValues = dailySteps.map{ dailyStepsEntity ->
                val day = dailyStepsEntity.getDayOfMonth().toFloat()
                val steps = dailyStepsEntity.steps.toFloat()
                BarEntry(day, steps)
            }
            // setup data and colors
            val barDataSet = BarDataSet(pairedValues, "Thy Steps Per Day")
            barDataSet.color = Color.parseColor("#FF3700")         // bars color
            barDataSet.setDrawValues(false)                                  // bars value not shown

            // add data to chart
            bar.data = BarData(barDataSet)
            bar.data.barWidth = 0.7f                          // set bars width

            // x axis config.
            val xAxis = bar.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM         // where if not there
            xAxis.setDrawGridLines(false)                       // no lines on the grid
            xAxis.granularity = 1f                              // ensure constant space between the bars
            xAxis.labelCount = 5                                // show just some of them
            xAxis.textColor = Color.WHITE                       // x values color

            // y axis config.
            val yAxis = bar.axisLeft
            yAxis.axisMinimum = 0f                             // start from 0
            yAxis.granularity = giveMeGranul(dailySteps)       // steps between horizontal lines
            yAxis.setDrawGridLines(true)                       // show lines on the grid
            yAxis.textColor = Color.WHITE                      // y values color

            bar.axisRight.isEnabled = false                    // remove
            bar.description.isEnabled = false                  // all
            bar.legend.isEnabled = false                       // this
            bar.setScaleEnabled(false)                         // scum
            bar.isHighlightFullBarEnabled = true               // but I still wanna touch you
            bar.setExtraOffsets(5f, 5f, 0f, 15f)          // down there

            // forge the chart
            bar.invalidate()

            // add a listener to the bars in order to display their value when on focus
            bar.setOnChartValueSelectedListener(object: OnChartValueSelectedListener{
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val steps = it.y
                        val infoText = "${steps.toInt()} steps taken"
                        barValueText.text = infoText
                        barValueText.visibility = View.VISIBLE
                    }
                }
                override fun onNothingSelected() {
                    barValueText.visibility = View.INVISIBLE
                }
            })

            // set interval of recorded days
            val barDesc = "From $firstDay to $finalDay"
            barDescription.text = barDesc
        }
    }

    // give GRANUL ( = granularity) of axis based on max of Daily Steps Recorded
    private fun giveMeGranul(steps: List<DailyStepsEntity>): Float{
        val maxSteps = steps.maxOf { it.steps }
        val granul = when{
            maxSteps <= 5000 -> 1250f
            maxSteps <= 10000 -> 2500f
            maxSteps <= 20000 -> 5000f
            else -> 10000f
        }
        return granul
    }
}