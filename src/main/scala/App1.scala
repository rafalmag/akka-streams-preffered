import akka.actor.ActorSystem
import akka.stream.scaladsl.{MergePreferred, Source}
import akka.stream.{ActorMaterializer, ThrottleMode}

import scala.concurrent.duration._
import scala.language.postfixOps

object App1 extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val hi: Source[String, _] = Source(Stream.from(1)).map(x => s"hi-$x").throttle(1, 99 millis, 1, ThrottleMode.shaping)
  val lo: Source[String, _] = Source(Stream.from(1)).map(x => s"lo-$x").throttle(1, 110 millis, 1, ThrottleMode.shaping)

  Source.combine(hi, lo)(x => MergePreferred(x - 1))
    .throttle(1, 100 millis, 1, ThrottleMode.shaping)
    .runForeach(println)

}
