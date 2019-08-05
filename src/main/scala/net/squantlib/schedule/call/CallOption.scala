package net.squantlib.schedule.call

import scala.collection.immutable.ListMap
import net.squantlib.util.UnderlyingFixing
import net.squantlib.schedule.FixingLeg
import net.squantlib.util.FixingInformation

case class CallOption(
  triggerUp: Boolean,
  forward: UnderlyingFixing,
  forwardInputString: Map[String, String],
  bonus: Double,
  invertedTrigger:Boolean,
  invertedForward:Boolean,
  removeSatisfiedTriggers:Boolean
) {
  

}

object CallOption {
  def empty = CallOption(true, UnderlyingFixing.empty, Map.empty, 0.0, false, false, false)
}
