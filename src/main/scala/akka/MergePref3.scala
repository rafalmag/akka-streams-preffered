package akka

import akka.stream._
import akka.stream.stage._

import scala.concurrent.duration._
import scala.language.postfixOps

class MergePref3[T]() extends GraphStage[UniformFanInShape[T, T]] {
  override def initialAttributes = Attributes.name("MergePref3")

  override val shape: UniformFanInShape[T, T] = new UniformFanInShape[T, T](2, "MergePref3")

  def out: Outlet[T] = shape.out

  val inHi: Inlet[T] = shape.in(0)
  val inLo: Inlet[T] = shape.in(1)


  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {

//    private var runningUpstreams = 2

//    private def upstreamsClosed = runningUpstreams == 0

    val timerKey = "timerKey"

    private def schedulePullLow: Unit = {
      scheduleOnce(timerKey, 100 millis)
    }

    override def preStart(): Unit = {
      tryPull(inHi)
      schedulePullLow
    }

    override def onTimer(key: Any): Unit = {
      if (!isAvailable(inHi) && !hasBeenPulled(inLo)) {
        println("scheduled pull on low")
        tryPull(inLo)
      } else {
        schedulePullLow
      }
    }

    setHandler(inHi, new InHandler {
      override def onPush(): Unit = {
        if (isAvailable(out)) {
          // isAvailable(out) implies !pending
          // -> grab and push immediately
          push(out, grab(inHi))
        } else {
          // TODO should we do that?
          println("reschedule from inHi.onPush")
          schedulePullLow
        }
        //        tryPull(inHi)
      }

      //      override def onUpstreamFinish() = {
      //        runningUpstreams -= 1
      //        if (upstreamsClosed ) {
      //          // TODO
      //          completeStage()
      //        }
      //      }
    })

    setHandler(inLo, new InHandler {
      override def onPush(): Unit = {
        if (isAvailable(out)) {
          // isAvailable(out) implies !pending
          // -> grab and push immediately

          // we need to handle inLow when it arrived
          push(out, grab(inLo))
          if (!isAvailable(inHi)) {
            println("reschedule from inLo.onPush")
            schedulePullLow
          }
        }
      }

      //      override def onUpstreamFinish() = {
      //        runningUpstreams -= 1
      //        if (upstreamsClosed && !pending)
      //          completeStage()
      //      }

    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (isAvailable(inHi)) {
          push(out, grab(inHi))
        } else if (isAvailable(inLo)) {
          push(out, grab(inLo))
        }
        if (!hasBeenPulled(inHi)) {
          tryPull(inHi)
        }
        schedulePullLow
      }
    })
  }

  override def toString = "MergePref3"

}