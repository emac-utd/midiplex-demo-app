package edu.utdallas.midiplexdemo

import android.app.Activity
import android.hardware.SensorEventListener
import android.os.Bundle
import android.view.Menu
import android.content.Context
import android.hardware.SensorManager
import android.hardware.Sensor
import android.widget.TextView
import android.widget.ToggleButton
import java.net.InetSocketAddress
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import android.widget.Toast
import java.io.IOException

class MainActivity extends Activity with SensorEventListener with FindView {

  //Have to make this a var so it has object scope but can be set during onCreate
  var sensorManager: SensorManager = null

  def tiltSensor: Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    sensorManager = getSystemService(Context.SENSOR_SERVICE) match {
      case s: SensorManager => s
      case _ => throw new ClassCastException
    }
  }

  override def onResume = {
    super.onResume
    sensorManager.registerListener(this, tiltSensor, SensorManager.SENSOR_DELAY_NORMAL)
  }
  override def onPause = {
    super.onPause
    sensorManager.unregisterListener(this)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu)
    return true
  }

  def onAccuracyChanged(sensor: android.hardware.Sensor, accuracy: Int) = {
    //Don't really need this
  }
  
  def datify(value: Float) : Byte = ((value + 10) * 10).toByte

  def send(value: Byte, port: Int) = {
    (new Thread(new Runnable {
      def run = {
        try {
          val address =
            new InetSocketAddress(findView[TextView](R.id.editText1).getText.toString, port)
          val request = new DatagramPacket((value :: Nil).toArray, 1, address)
          val socket = new DatagramSocket
          socket.send(request)
        } catch {
          case e: SocketException => {
            Toast.makeText(MainActivity.this,
              "Connection failed", Toast.LENGTH_SHORT)
            findView[ToggleButton](R.id.dataToggle).setChecked(false)
          }
          case e: IOException => {
            Toast.makeText(MainActivity.this,
              "Message error", Toast.LENGTH_SHORT)
            findView[ToggleButton](R.id.dataToggle).setChecked(false)
          }
          case e : Throwable => throw e //Pass it along to someone who can handle it or crash
        }
      }
    })).start()
  }

  def onSensorChanged(event: android.hardware.SensorEvent) = {
    findView[TextView](R.id.sensor_monitor).setText(
      event.values(0).toString() + ','
        + event.values(1).toString() + ','
        + event.values(2).toString())

    if (findView[ToggleButton](R.id.dataToggle).isChecked) {
      send(datify(event.values(0)), 9001)
      send(datify(event.values(1)), 9002)
      send(datify(event.values(2)), 9003)
    }
  }
}