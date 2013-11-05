package edu.utdallas.midiplexdemo

import android.app.Activity
import android.view.View
import android.widget.TextView

trait FindView extends Activity {
  def findView[WidgetType](id: Int): WidgetType = {
    findViewById(id).asInstanceOf[WidgetType]
  }
}