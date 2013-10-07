package activator.analytics.common

import com.typesafe.atmos.trace._
import com.typesafe.atmos.uuid.UUID
import scala.collection.immutable.Stream
import activator.analytics.data.{ PlayRequestSummary, RequestSummaryType, TimeMark }

object PlayRequestSummaryGenerator {
  import scala.util.Random
  import java.util.concurrent.atomic.AtomicLong
  import scala.collection.mutable.Builder

  private val random: Random = new Random
  private val counter: AtomicLong = new AtomicLong(1L)

  private def stringBy(minLength: Int = 6, maxLength: Int = 10): Stream[String] = Stream.continually(new String(Random.alphanumeric.take(random.nextInt(maxLength) + minLength).toArray))

  private def stringSeqBy(maxSeqLength: Int = 3, minLength: Int = 6, maxLength: Int = 10): Stream[Seq[String]] = Stream.continually(stringBy(minLength, maxLength).take(random.nextInt(maxSeqLength + 1)).toSeq)

  private class Interval(bottom: Long, maxOffset: Int = 10000, minDuration: Int = 100, maxDuration: Int = 1000000) {
    val offset = random.nextInt(maxOffset)
    val startNanos = bottom + offset
    val startMillis = startNanos / 1000000
    val duration: Long = minDuration.longValue + random.nextInt(maxDuration)
    val endNanos = startNanos + duration
    val endMillis = endNanos / 1000000
    override def toString: String = "[offset: " + offset + " startNanos: " + startNanos + " startMillis: " + startMillis + " duration: " + duration + " endMillis: " + endMillis + " endNanos: " + endNanos + "]"
  }

  private def actionRequestInfoGen: ActionRequestInfo =
    ActionRequestInfo(random.nextInt(1000).longValue,
      Map[String, String](stringBy().zip(stringBy()).take(random.nextInt(3)).toSeq: _*),
      patternGen(),
      patternGen(),
      httpMethodGen,
      "HTTP/1.1",
      Map[String, Seq[String]](stringBy().zip(stringSeqBy()).take(random.nextInt(3)).toSeq: _*),
      Map[String, Seq[String]](stringBy().zip(stringSeqBy()).take(random.nextInt(3)).toSeq: _*))

  private case class RequestSummaryTypeRecord(httpStatusCode: Int, summaryType: RequestSummaryType, errorMessage: Option[String], stackTrace: Option[Seq[String]])

  private def requestSummaryTypeGen: RequestSummaryTypeRecord = random.nextInt(10) match {
    case 0 ⇒ RequestSummaryTypeRecord(500, RequestSummaryType.Exception, Some(stringBy().head), Some(stringBy().take(20).toSeq))
    case 1 ⇒ RequestSummaryTypeRecord(400, RequestSummaryType.BadRequest, Some(stringBy().head), None)
    case 2 ⇒ RequestSummaryTypeRecord(404, RequestSummaryType.HandlerNotFound, None, None)
    case _ ⇒ RequestSummaryTypeRecord(200, RequestSummaryType.Normal, None, None)
  }

  private def intervalGen(bottom: Long, maxOffset: Int = 10000, minDuration: Int = 100, maxDuration: Int = 1000000): Interval = new Interval(bottom, maxOffset, minDuration, maxDuration)

  private def counterGen: Long = counter.getAndAdd(1L + random.nextInt(100) + 1)

  private def ipGen(fill: String = "."): String = {
    val ip1 = random.nextInt(200)
    val ip2 = random.nextInt(200)
    val ip3 = random.nextInt(200)
    val ip4 = random.nextInt(200)
    (ip1 + 10).toString + fill + (ip2 + 10) + fill + (ip3 + 10) + fill + (ip4 + 10)
  }

  private def nodeGen(host: String): String = stringBy().head + "@" + host

  private def controllerGen: String =
    stringBy(10).take(2).mkString(".")

  private def methodGen: String = stringBy().head
  private def domainGen: String = stringBy().take(2).mkString(".") + "." + (random.nextInt(3) match {
    case 0 ⇒ "com"
    case 1 ⇒ "org"
    case 2 ⇒ "net"
  })

  private def patternGen(maxLength: Int = 6): String = "/" + stringBy(3).take(random.nextInt(maxLength) + 1).mkString("/")

  private def newRequestDuration: Interval = intervalGen(System.nanoTime, minDuration = random.nextInt(1000000) + 300000)

  private def httpMethodGen: String = if (random.nextBoolean) "GET" else "POST"

  private def asyncResponseTimes(base: Long, maxCount: Int = 3, minDuration: Int = 1, maxDuration: Int = 100): Seq[Long] = {
    def next(a: Builder[Long, Seq[Long]], base: Long, remaining: Int): Seq[Long] =
      if (remaining == 0) a.result
      else {
        val n = base + random.nextInt(maxDuration) + minDuration
        next(a += n, n, remaining - 1)
      }
    next(Seq.newBuilder[Long], base, maxCount)
  }

  private def sessionGenerator(maxSize: Int = 6): Option[Map[String, String]] =
    if (random.nextBoolean) {
      val keys = random.nextInt(maxSize) + 1
      Some(Map(stringBy().zip(stringBy()).take(keys): _*))
    } else None

  private def genActionInvocationInfo: ActionInvocationInfo = {
    val uri = patternGen()
    ActionInvocationInfo(controllerGen,
      methodGen,
      uri,
      counterGen,
      uri,
      uri,
      httpMethodGen,
      "HTTP/1.1",
      ipGen(),
      Some(ipGen()),
      Some(domainGen),
      sessionGenerator())
  }

  def generate: PlayRequestSummary = {
    val host = ipGen("-")
    val interval = newRequestDuration
    val st = requestSummaryTypeGen
    val asyncResponses = random.nextInt(3)
    val requestInfo = actionRequestInfoGen
    val maxDuration: Int = interval.duration.intValue / 3
    val inputProcessing = intervalGen(interval.startNanos, maxDuration = maxDuration)
    val actionExecution = intervalGen(inputProcessing.endNanos, maxDuration = maxDuration)
    val asyncTimes = asyncResponseTimes(actionExecution.endNanos, asyncResponses)
    val outputStart = if (asyncResponses == 0) actionExecution.endNanos else asyncTimes.max
    val remaining = (interval.endNanos - outputStart - 10).intValue
    val outputProcessing = intervalGen(outputStart, maxOffset = 10, maxDuration = remaining)
    PlayRequestSummary(new UUID,
      nodeGen(host),
      host,
      st.summaryType,
      requestInfo,
      TimeMark(interval.startMillis, interval.startNanos),
      TimeMark(interval.endMillis, interval.endNanos),
      interval.duration,
      genActionInvocationInfo,
      ActionSimpleResult(ActionResultInfo(st.httpStatusCode)),
      asyncTimes,
      inputProcessing.duration,
      TimeMark(actionExecution.startMillis, actionExecution.startNanos),
      actionExecution.duration,
      TimeMark(outputProcessing.startMillis, outputProcessing.startNanos),
      interval.endNanos - outputProcessing.startNanos,
      random.nextInt(10000).longValue,
      random.nextInt(1000000).longValue,
      st.errorMessage,
      st.stackTrace)
  }

  def genStream: Stream[PlayRequestSummary] = Stream.continually(generate)
}