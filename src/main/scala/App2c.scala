import akka.{MergePref3, MergePref4}
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.duration._
import scala.language.postfixOps


object App2c extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

    val hi: Source[String, _] = Source(Stream.from(1)).map(x => s"hi-$x").throttle(1, 99 millis, 1, ThrottleMode.shaping)
    val lo: Source[String, _] = Source(Stream.from(1)).map(x => s"lo-$x").throttle(1, 110 millis, 1, ThrottleMode.shaping)

  Source.combine(hi,lo)(_ => new MergePref4[String]())
    .throttle(1, 100 millis, 1, ThrottleMode.shaping)
    .log("msg").withAttributes(Attributes.logLevels(onElement = Logging.WarningLevel))
    .runWith(Sink.ignore)

}
