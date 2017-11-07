import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.contrib.{SwitchMode, Valve, ValveSwitch}
import akka.stream.scaladsl._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

// https://github.com/akka/akka-stream-contrib/blob/master/contrib/src/main/scala/akka/stream/contrib/Valve.scala
object App3 extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val hi = Source(Stream.from(1)).map(x => s"hi-$x").throttle(1, 99 millis, 1, ThrottleMode.shaping).viaMat(new Valve[String](SwitchMode.Open))(Keep.right)
  val lo = Source(Stream.from(1)).map(x => s"lo-$x").throttle(1, 110 millis, 1, ThrottleMode.shaping).viaMat(new Valve[String](SwitchMode.Open))(Keep.right)

  val source =
    Source.fromGraph(GraphDSL.create(hi, lo)(Keep.both)({ implicit builder =>
      (hi1, lo1) =>
        import GraphDSL.Implicits._

        // picks randomly from inputs
        val merge = builder.add(Merge[String](2))
        hi1 ~> merge
        lo1 ~> merge
        SourceShape(merge.out)
    }))

  val matValues: (Future[ValveSwitch], Future[ValveSwitch]) = source
    .map(before)
    // buffer at least 2!
    .buffer(2, OverflowStrategy.backpressure)
    .throttle(1, 100 millis, 1, ThrottleMode.shaping)
    .map(after)
    .toMat(Sink.foreach(println))(Keep.left).run()

  val (hiSwitch, loSwitch) = matValues

  var hiInCounter = new AtomicInteger(0)

  import system.dispatcher

  def before(x: String) = {
    if (x.contains("hi")) {
      loSwitch.map(_.flip(SwitchMode.Close))
      val counter = hiInCounter.incrementAndGet()
      println(counter)
    } else {
      println(hiInCounter.get())
    }
    x
  }

  def after(x: String) = {
    if (x.contains("hi")) {
      if (hiInCounter.decrementAndGet() == 0) {
        loSwitch.map(_.flip(SwitchMode.Open))
      }
    }
    x
  }


}
