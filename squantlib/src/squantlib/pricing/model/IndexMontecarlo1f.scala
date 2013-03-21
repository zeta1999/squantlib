package squantlib.pricing.model

import squantlib.model.Market
import squantlib.payoff.{Payoff, Payoffs, Schedule, CalculationPeriod, ScheduledPayoffs}
import squantlib.pricing.mcengine._
import squantlib.model.index.Index
import squantlib.model.Bond
import squantlib.util.JsonUtils._
import org.codehaus.jackson.JsonNode
import squantlib.model.rates.DiscountCurve
import org.jquantlib.time.{Date => qlDate}
import squantlib.database.fixings.Fixings
import org.jquantlib.daycounters.Actual365Fixed


case class IndexMontecarlo1f(valuedate:qlDate, 
					  mcengine:Montecarlo1f, 
					  scheduledPayoffs:ScheduledPayoffs, 
					  index:Index,
					  defaultPaths:Int) extends PricingModel {
  
	mcPaths = defaultPaths

	def generatePaths(paths:Int):List[List[Double]] = {
	  val mcYears = scheduledPayoffs.eventDateYears(valuedate)
	  val (mcdates, mcpaths) = mcengine.generatePaths(mcYears, paths)
	  if (mcdates.sameElements(mcYears)) mcpaths
	  else { println("invalid mc dates"); List.empty}
	}
	 
	def mcPrice(paths:Int):List[Double] = {
	  try { generatePaths(paths).map(p => scheduledPayoffs.price(p)).transpose.map(_.sum / paths.toDouble) }
	  catch {case e => println("MC calculation error : " + e.getStackTrace.mkString(sys.props("line.separator"))); List.empty}
	}
	
	def modelForward(paths:Int):List[Double] = generatePaths(paths).transpose.map(_.sum).map(_ / paths)
	  
	private val cachedPrice = scala.collection.mutable.WeakHashMap[String, List[Double]]()
	
	def price:List[Double] = price(mcPaths)
	
	def price(paths:Int):List[Double] = cachedPrice.getOrElseUpdate("PRICE", mcPrice(paths))
	
	val payoff:List[Payoff] = scheduledPayoffs.payoffs.toList
	
	val periods = scheduledPayoffs.schedule.toList
	
}


object IndexMontecarlo1f {
	
	var defaultPaths = 100000
	
	def apply(market:Market, bond:Bond, mcengine:Index => Option[Montecarlo1f]):Option[IndexMontecarlo1f] = apply(market, bond, defaultPaths, mcengine)
	
	def apply(
	    market:Market, 
	    bond:Bond, 
	    paths:Int, 
	    mcengine:Index => Option[Montecarlo1f]):Option[IndexMontecarlo1f] = {
	  
	  val valuedate = market.valuedate
	  
	  val scheduledPayoffs = bond.livePayoffs(valuedate)
	  
	  if (scheduledPayoffs.variables.size != 1) { 
	    println(bond.id + " : payoff not compatible with Index1d model")
	    return None}
	  
	  val variable = scheduledPayoffs.variables.head
	  
	  val index = market.getIndex(variable).orNull
	  
	  if (index == null) {
	    println(bond.id + " : invalid index underlying - " + variable + " in market " + market.paramset)
	    return None}
	  
	  if (index.currency != bond.currency) {
	    println(bond.id + " : quanto model not supported - " + variable)
	    return None}
	  
	  val mcmodel = mcengine(index).orNull
	  
	  if (mcmodel == null) {
	    println(bond.id + " : model name not found or model calibration error")
	    return None}
	  
	  
	  Some(IndexMontecarlo1f(valuedate, mcmodel, scheduledPayoffs, index, paths))
	}
}









