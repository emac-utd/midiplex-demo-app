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
import android.os.Handler
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.CompoundButton
import java.net.Socket
import java.io.DataOutputStream

class MainActivity extends Activity with SensorEventListener with FindView {

  //Unavoidable mutables due to arbitrary creation time and need for access
  var sensorManager: SensorManager = null
  var tiltAngles: Array[Double] = Array.fill(3)(0d)
  var socketX: Socket = null
  var socketY: Socket = null

  val handler = new Handler

  def makeAndRunThread(fn: Function0[Unit]) = (new Thread(new Runnable {
    def run = fn()
  })).start()

  def tiltSensor: Sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

  private def getToggle: android.widget.ToggleButton = {
    findView[ToggleButton](R.id.dataToggle)
  }

  //Using Array and not Seq because Android doesn't like the Array -> Seq conversion
  def addArrays(x: Array[Double], y: Array[Double]) =
    x.zip(y).map(p => p._1 + p._2)

  def anglesToAccel(angles: Array[Double]) = angles.map(a => a * -0.25d)

  def limit(nums: Array[Double], min: Double, max: Double) =
    nums.map(p => p match {
      case p if p < min => min
      case p if p > max => max
      case p => p
    })

  def sendPositions(pos: Array[Byte]) = {
    send(pos(0), socketX) //x
    send(pos(1), socketY) //y
  }

  //Again, funny business due to unavailability of Seq conversion
  def condSet[A](arr: Array[A], pred: Boolean, index: Int, value: A): Array[A] = pred match {
    case p if p == true => {
      val cloned = arr.clone
      cloned.update(index, value)
      cloned
    }
    case _ => arr.clone
  }

  def nextUpdate(pos: Array[Double], vel: Array[Double]): Runnable = new Runnable {
    def run = {
      val newVel = addArrays(vel, anglesToAccel(tiltAngles))
      val newPos = limit(addArrays(pos, newVel), 0d, 127d)
      val newVelXLock = condSet(newVel, newPos(0) == 0d || newPos(0) == 127d, 0, 0d)
      val newVelLocked = condSet(newVelXLock, newPos(1) == 0d || newPos(1) == 127d, 1, 0d)

      findView[TextView](R.id.sensor_monitor).setText(
        tiltAngles(0).toString + ','
          + tiltAngles(1).toString + ','
          + tiltAngles(2).toString + '\n'
          + newPos(0).toByte.toString + ','
          + newPos(1).toByte.toString)

      if (findView[ToggleButton](R.id.dataToggle).isChecked) {
        val newBytes = newPos.map(v => v.toByte)
        sendPositions(newBytes)
      }

      handler.postDelayed(nextUpdate(newPos, newVelLocked), 100)
    }
  }

  def connect = makeAndRunThread(() => {
    val address =
      findView[TextView](R.id.editText1).getText.toString
    try {
      socketX = new Socket(address, 9001)
      socketY = new Socket(address, 9002)
    } catch {
      case e: SocketException => {
        Toast.makeText(MainActivity.this,
          "Connection failed", Toast.LENGTH_SHORT)
        getToggle.setChecked(false)
      }
      case e: IOException => {
        Toast.makeText(MainActivity.this,
          "Socket error", Toast.LENGTH_SHORT)
        getToggle.setChecked(false)
      }
      case e: Throwable => throw e //Pass it along to someone who can handle it or crash
    }
  })

  def disconnect = makeAndRunThread(() => {
    try {
      if (socketX != null)
        socketX.close
      if (socketY != null)
        socketY.close
    } catch {
      case e: SocketException => {
        Toast.makeText(MainActivity.this,
          "Close failed", Toast.LENGTH_SHORT)
        findView[ToggleButton](R.id.dataToggle).setChecked(false)
      }
      case e: IOException => {
        Toast.makeText(MainActivity.this,
          "Close error", Toast.LENGTH_SHORT)
        findView[ToggleButton](R.id.dataToggle).setChecked(false)
      }
      case e: Throwable => throw e //Pass it along to someone who can handle it or crash
    }
  })

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    sensorManager = getSystemService(Context.SENSOR_SERVICE) match {
      case s: SensorManager => s
      case _ => throw new ClassCastException
    }

    //On/off toggle events
    val toggle = getToggle
    toggle.setOnCheckedChangeListener(
      new OnCheckedChangeListener {
        def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = {
          if (isChecked) connect else disconnect
        }
      })
  }

  override def onResume = {
    super.onResume
    sensorManager.registerListener(this, tiltSensor, SensorManager.SENSOR_DELAY_NORMAL)

    val toggle = getToggle
    if (toggle.isChecked) connect

    val initPos = Array.fill(3)(63d)
    val initVel = Array.fill(3)(0d)
    handler.post(nextUpdate(initPos, initVel))
  }
  override def onPause = {
    super.onPause
    sensorManager.unregisterListener(this)
    handler.removeCallbacksAndMessages(null)

    disconnect
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.main, menu)
    return true
  }

  def onAccuracyChanged(sensor: android.hardware.Sensor, accuracy: Int) = {
    //Don't really need this
  }

  def datify(value: Double): Byte = ((value + Math.PI / 2) / Math.PI * 255).toByte

  def square(x: Double) = Math.pow(x, 2)
  def rms(vals: Array[Float]): Double =
    Math.sqrt(vals.foldLeft(0d)(_ + square(_)))

  def angles(vals: Array[Float]): Array[Double] =
    vals.map(x => Math.asin(x / rms(vals)))

  def send(value: Byte, socket: Socket) = makeAndRunThread(() => {
    try {
      val stream = new DataOutputStream(socket.getOutputStream())
      stream.writeByte(value)
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
      case e: Throwable => throw e //Pass it along to someone who can handle it or crash
    }
  })

  def onSensorChanged(event: android.hardware.SensorEvent) = {
    tiltAngles = angles(event.values)
  }
}