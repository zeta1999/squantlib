package squantlib.payoff

import org.jquantlib.time._
import org.jquantlib.time.calendars.NullCalendar
import org.jquantlib.daycounters.DayCounter
import scala.collection.mutable.MutableList
import org.jquantlib.time.DateGeneration.Rule._
import scala.collection.immutable.LinearSeq
import org.jquantlib.daycounters.Absolute

class Schedule(inputdates:List[CalcPeriod]) extends LinearSeq[CalcPeriod] {
 
	val (dates, legorder) = inputdates.zipWithIndex.sortBy(_._1.eventDate).unzip
	
    def apply(i:Int):CalcPeriod = dates(i)
	override def isEmpty:Boolean = dates.isEmpty
	override def head:CalcPeriod = dates.head
	override def tail = dates.tail
	override def length = dates.length
	override def iterator:Iterator[CalcPeriod] = dates.iterator

    def get(i:Int):CalcPeriod = dates(i)
    
    val effectiveDate = dates.minBy(_.startDate).startDate
    val terminationDate = dates.maxBy(_.endDate).endDate
    
    def startDate(i:Int):Date = dates(i).startDate
    val startDates:List[Date] = dates.map(_.startDate)
    val startYears:List[Double] = dates.map(d => d.daycounter.yearFraction(effectiveDate, d.startDate))
    
    def endDate(i:Int):Date = dates(i).endDate
    val endDates:List[Date] = dates.map(_.endDate)
    val endYears:List[Double] = dates.map(d => d.daycounter.yearFraction(effectiveDate, d.endDate))
    
    def eventDate(i:Int):Date = dates(i).eventDate
    val eventDates:List[Date] = dates.map(_.eventDate)
    val eventYears:List[Double] = dates.map(d => d.daycounter.yearFraction(effectiveDate, d.eventDate))
    
    def paymentDate(i:Int):Date = dates(i).paymentDate
    val paymentDates:List[Date] = dates.map(_.paymentDate)
    val paymentYears:List[Double] = dates.map(d => d.daycounter.yearFraction(effectiveDate, d.paymentDate))

    def currentPeriods(ref:Date) = dates.filter(d => (ref ge d.startDate) && (ref lt d.endDate))
    
    override def toString = "eventdate startdate enddate paymentdate\n" + dates.mkString("\n")
    
}

object Schedule{
	
	def apply(inputDates:List[CalcPeriod]) = new Schedule(inputDates)
	
	def apply(
	    effectiveDate:Date,
		terminationDate:Date,
		tenor:Period,
		calendar:Calendar,
		calendarConvention:BusinessDayConvention,
		paymentConvention:BusinessDayConvention,
		terminationDateConvention:BusinessDayConvention,
		rule:DateGeneration.Rule,
		fixingInArrears:Boolean,
		noticeDay:Int,
		daycount:DayCounter,
		firstDate:Option[Date],
		nextToLastDate:Option[Date],
		addRedemption:Boolean,
		maturityNotice:Int) = {
	  
		assert(firstDate.isEmpty || firstDate.get.gt(effectiveDate))
		assert(nextToLastDate.isEmpty || nextToLastDate.get.lt(terminationDate))
		
	    val nullCalendar = new NullCalendar
	    
	    def calcperiod(startdate:Date, enddate:Date):CalcPeriod = CalcPeriod(startdate, enddate, noticeDay, fixingInArrears, daycount, calendar, if (enddate == terminationDate) terminationDateConvention else paymentConvention)
	    
	    val redemptionLegs:List[CalcPeriod] = 
	      if(addRedemption) List(CalcPeriod(effectiveDate, terminationDate, maturityNotice, true, new Absolute, calendar, terminationDateConvention))
	      else List.empty
	    
	    val couponLegs:List[CalcPeriod] = (rule match {
		  
		  case Zero => List(calcperiod(effectiveDate, terminationDate))
	
	      case Backward => 
		  	var tempdates:MutableList[CalcPeriod] = MutableList.empty
	        
	        val initialDate = nextToLastDate match {
	          case Some(d) => tempdates += calcperiod(d, terminationDate); d
	          case None => terminationDate
	        }
	        
	        var periods=1
	        var startDate:Date = initialDate
	        var endDate:Date = terminationDate
	
	        do {
	          endDate = startDate
	          startDate = nullCalendar.advance(initialDate, tenor.mul(periods).negative, calendarConvention)
	          if (Math.abs(effectiveDate.sub(startDate)) < 14) {startDate = effectiveDate}
	          tempdates += calcperiod(if (startDate lt effectiveDate) effectiveDate else startDate, endDate)
	          periods = periods + 1
	        } while (startDate gt effectiveDate)
	         
	        tempdates.sortBy(_.eventDate).toList
	
	      case Forward =>
		  	var tempdates:MutableList[CalcPeriod] = MutableList.empty
	        
	        val initialDate = firstDate match {
	          case Some(d) => tempdates += calcperiod(effectiveDate, d); d
	          case None => effectiveDate
	        }
	        
	        var periods=1
	        var startDate:Date = effectiveDate
	        var endDate:Date = initialDate
	
	        do {
	          startDate = endDate
	          endDate = nullCalendar.advance(initialDate, tenor.mul(periods), calendarConvention)
	          if (Math.abs(terminationDate.sub(endDate)) < 14) {endDate = terminationDate}
	          tempdates += calcperiod(startDate, if (endDate ge terminationDate) terminationDate else endDate)
	          periods = periods + 1
	        } while (endDate lt terminationDate)
	          
	        tempdates.sortBy(_.eventDate).toList
	
	      case _ => 
	        println("Unknown schedule rule")
	        List.empty
	    })
	    
	    new Schedule(couponLegs ++ redemptionLegs)
	}

}


class CalcPeriod(val eventDate:Date, val startDate:Date, val endDate:Date,val paymentDate:Date, val daycounter:DayCounter) {
	def daycount:Double = daycounter.yearFraction(startDate, endDate)
	override def toString = eventDate.shortDate.toString + " " + startDate.shortDate.toString + " " + endDate.shortDate.toString + " " + paymentDate.shortDate.toString + " " + daycounter.toString
}

object CalcPeriod {
  
	def apply(eventDate:Date, startDate:Date, endDate:Date, paymentDate:Date, daycount:DayCounter):CalcPeriod = 
	  new CalcPeriod(eventDate, startDate, endDate, paymentDate, daycount)
	
	def apply(startDate:Date, endDate:Date, notice:Int, inarrears:Boolean, daycount:DayCounter, calendar:Calendar, paymentConvention:BusinessDayConvention):CalcPeriod = {
	  val eventDate = if (inarrears) calendar.advance(endDate, -notice, TimeUnit.Days)
			  		else calendar.advance(startDate, -notice, TimeUnit.Days)
			  		  
	  val paymentDate = calendar.adjust(endDate, paymentConvention)
	  				
	  new CalcPeriod(eventDate, startDate, endDate, paymentDate, daycount)
	}
  
}