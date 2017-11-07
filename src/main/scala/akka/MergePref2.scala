package akka

import akka.stream._
import akka.stream.impl.FixedSizeBuffer
import akka.stream.stage._

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.language.postfixOps

class MergePref2[T]() extends GraphStage[FanInShape2[T, T, T]] {
  override def initialAttributes = Attributes.name("MergePref2")

  override val shape: FanInShape2[T, T, T] = new FanInShape2[T, T, T]("MergePref2")

  def out: Outlet[T] = shape.out

  val inHi: Inlet[T] = shape.in0
  val inLo: Inlet[T] = shape.in1


  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new TimerGraphStageLogic(shape) {

    private val pendingQueue = FixedSizeBuffer[Inlet[T]](2)

    private def pending: Boolean = pendingQueue.nonEmpty

    private var runningUpstreams = 2

    private def upstreamsClosed = runningUpstreams == 0

    val timerKey = "timerKey"
    private def schedule: Unit = {
      scheduleOnce(timerKey, 100 millis)
    }

    @tailrec
    private def dequeueAndDispatch(): Unit = {
      val in = pendingQueue.dequeue()
      if (in == null) {
        // in is null if we reached the end of the queue
        if (upstreamsClosed) completeStage()
      } else if (isAvailable(in)) {
        push(out, grab(in))
        if (upstreamsClosed && !pending)
          completeStage()
        else{
          if(in ==inHi){
            tryPull(inHi)
          }else{
            println("reschedule from dequeueAndDispatch")
            schedule
          }
        }
      } else {
        // in was closed after being enqueued
        // try next in queue
        dequeueAndDispatch()
      }
    }

    override def preStart(): Unit = {
      tryPull(inHi)
      schedule
    }

    override def onTimer(key: Any): Unit = {
      if(pendingQueue.peek() != inHi && !isAvailable(inHi) && !hasBeenPulled(inLo)) {
        println("scheduled pull on low")
        tryPull(inLo)
      }
    }

    setHandler(inHi, new InHandler {
      override def onPush(): Unit = {
        if (isAvailable(out)) {
          // isAvailable(out) implies !pending
          // -> grab and push immediately
          push(out, grab(inHi))
          tryPull(inHi)
        } else {
          pendingQueue.enqueue(inHi)
          println("reschedule from inHi.onPush")
          schedule
        }
      }

      override def onUpstreamFinish() = {
        runningUpstreams -= 1
        if (upstreamsClosed && !pending)
          completeStage()
      }
    })


    setHandler(inLo, new InHandler {
      override def onPush(): Unit = {
        if (isAvailable(out)) {
          // isAvailable(out) implies !pending
          // -> grab and push immediately

          // we need to handle inLow when it arrived
          push(out, grab(inLo))
          if (!isAvailable(inHi) || pendingQueue.isEmpty) {
            println("reschedule from inLo.onPush")
            schedule
          }
        } else
          pendingQueue.enqueue(inLo)
      }

      override def onUpstreamFinish() = {
        runningUpstreams -= 1
        if (upstreamsClosed && !pending)
          completeStage()
      }

    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (pending)
          dequeueAndDispatch()
      }
    })
  }

  override def toString = "ZipWith2"

}