package com.ruimmpires.mqtthome

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvCurrentTemp: TextView
    private lateinit var lineChart: LineChart
    private lateinit var btnSettings: Button
    private lateinit var btnAbout: Button

    private lateinit var dataSet1: LineDataSet
    private lateinit var dataSet2: LineDataSet
    private var mqttClient: MqttAsyncClient? = null

    // Configurable variables
    private var brokerIp = ""
    private var brokerPort = ""
    private var topic1 = ""
    private var topic2 = ""
    private var name1 = "" // NEW
    private var name2 = "" // NEW

    private val referenceTime = System.currentTimeMillis()
    private var latestTemp1 = "--"
    private var latestTemp2 = "--"

    private lateinit var sharedPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        lineChart = findViewById(R.id.lineChart)
        tvCurrentTemp = findViewById(R.id.tvCurrentTemp)
        btnSettings = findViewById(R.id.btnSettings)
        btnAbout = findViewById(R.id.btnAbout)


        sharedPrefs = getSharedPreferences("MQTT_PREFS", Context.MODE_PRIVATE)
        loadSettings()

        setupChart()

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        if (brokerIp.isNotEmpty()) {
            connectToMqtt()
        } else {
            tvCurrentTemp.text = "Please configure settings"
        }
    }

    // NEW Function to show the credits
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About MQTTatHome")
            .setMessage("A custom IoT visualization tool.\n\nDeveloped by: Rui Pires and Gemini Pro\nGitHub: github.com/ruimmpires/mqttathome\n22Feb2026\nVersion 1.0")
            .setPositiveButton("OK", null)
            .setIcon(R.mipmap.ic_launcher) // This adds your new app icon to the dialog!
            .show()
    }

    private fun setupChart() {
        // Use the loaded names for the legend
        dataSet1 = LineDataSet(ArrayList<Entry>(), name1)
        dataSet1.color = Color.BLUE
        dataSet1.setCircleColor(Color.BLUE)
        dataSet1.valueTextColor = Color.BLACK
        dataSet1.lineWidth = 2f

        dataSet2 = LineDataSet(ArrayList<Entry>(), name2)
        dataSet2.color = Color.RED
        dataSet2.setCircleColor(Color.RED)
        dataSet2.valueTextColor = Color.BLACK
        dataSet2.lineWidth = 2f

        val lineData = LineData(dataSet1, dataSet2)
        lineChart.data = lineData

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val originalTimeMillis = referenceTime + (value.toLong() * 1000)
                return dateFormat.format(Date(originalTimeMillis))
            }
        }
        xAxis.labelRotationAngle = -45f
        lineChart.description.isEnabled = false
        lineChart.invalidate()
    }

    private fun loadSettings() {
        brokerIp = sharedPrefs.getString("IP", "g0mesp1res.dynip.sapo.pt") ?: ""
        brokerPort = sharedPrefs.getString("PORT", "1883") ?: ""
        topic1 = sharedPrefs.getString("TOPIC1", "home/watertemp/1/tx") ?: ""
        topic2 = sharedPrefs.getString("TOPIC2", "home/watertemp/2") ?: ""

        // Load the names, default to "Sensor 1" and "Sensor 2" if not set yet
        name1 = sharedPrefs.getString("NAME1", "Shower") ?: "Shower"
        name2 = sharedPrefs.getString("NAME2", "Solar") ?: "Solar"
    }

    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val etIp = dialogView.findViewById<EditText>(R.id.etBrokerIp)
        val etPort = dialogView.findViewById<EditText>(R.id.etBrokerPort)
        val etTop1 = dialogView.findViewById<EditText>(R.id.etTopic1)
        val etTop2 = dialogView.findViewById<EditText>(R.id.etTopic2)

        // NEW inputs
        val etName1 = dialogView.findViewById<EditText>(R.id.etName1)
        val etName2 = dialogView.findViewById<EditText>(R.id.etName2)

        etIp.setText(brokerIp)
        etPort.setText(brokerPort)
        etTop1.setText(topic1)
        etTop2.setText(topic2)
        etName1.setText(name1)
        etName2.setText(name2)

        AlertDialog.Builder(this)
            .setTitle("MQTT Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val editor = sharedPrefs.edit()
                editor.putString("IP", etIp.text.toString())
                editor.putString("PORT", etPort.text.toString())
                editor.putString("TOPIC1", etTop1.text.toString())
                editor.putString("TOPIC2", etTop2.text.toString())
                editor.putString("NAME1", etName1.text.toString()) // Save new name
                editor.putString("NAME2", etName2.text.toString()) // Save new name
                editor.apply()

                loadSettings()

                // Update chart legend text immediately
                dataSet1.label = name1
                dataSet2.label = name2
                lineChart.notifyDataSetChanged()
                lineChart.invalidate()

                // Update the big text display immediately
                tvCurrentTemp.text = "$name1: $latestTemp1°C | $name2: $latestTemp2°C"

                connectToMqtt()
                Toast.makeText(this, "Settings Saved! Reconnecting...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectToMqtt() {
        try {
            if (mqttClient?.isConnected == true) {
                mqttClient?.disconnect()
            }
        } catch (e: Exception) { e.printStackTrace() }

        val brokerUri = "tcp://$brokerIp:$brokerPort"
        val clientId = MqttClient.generateClientId()

        try {
            mqttClient = MqttAsyncClient(brokerUri, clientId, MemoryPersistence())

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.d("MQTT", "Connection lost")
                }

                override fun messageArrived(incomingTopic: String?, message: MqttMessage?) {
                    val payload = message.toString()
                    val tempValue = payload.toFloatOrNull() ?: return

                    val now = System.currentTimeMillis()
                    val secondsSinceStart = ((now - referenceTime) / 1000f)

                    runOnUiThread {
                        if (incomingTopic == topic1) {
                            dataSet1.addEntry(Entry(secondsSinceStart, tempValue))
                            latestTemp1 = payload
                        } else if (incomingTopic == topic2) {
                            dataSet2.addEntry(Entry(secondsSinceStart, tempValue))
                            latestTemp2 = payload
                        }

                        // Uses the custom names you typed in the settings!
                        tvCurrentTemp.text = "$name1: $latestTemp1°C | $name2: $latestTemp2°C"

                        lineChart.data.notifyDataChanged()
                        lineChart.notifyDataSetChanged()
                        lineChart.setVisibleXRangeMaximum(60f)
                        lineChart.moveViewToX(secondsSinceStart)
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            val options = MqttConnectOptions().apply { isAutomaticReconnect = true }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MQTT", "Connected to Mosquitto at $brokerUri!")
                    subscribeToTopics()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e("MQTT", "Failed to connect to $brokerUri", exception)
                }
            })

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun subscribeToTopics() {
        try {
            val topicsToSubscribe = arrayOf(topic1, topic2)
            val qos = intArrayOf(0, 0)
            mqttClient?.subscribe(topicsToSubscribe, qos)
            Log.d("MQTT", "Subscribed to $topic1 and $topic2")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}