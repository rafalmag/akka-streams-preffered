package akka

import akka.stream._
import akka.stream.stage._

import scala.language.postfixOps

class MergePref4[T]() extends GraphStage[UniformFanInShape[T, T]] {
  override def initialAttributes = Attributes.name("MergePref4")

  override val shape: UniformFanInShape[T, T] = new UniformFanInShape[T, T](2, "MergePref4")

  def out: Outlet[T] = shape.out

  val inHi: Inlet[T] = shape.in(0)
  val inLo: Inlet[T] = shape.in(1)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    override def preStart(): Unit = {
      tryPull(inHi)
      tryPull(inLo)
    }

    setHandler(inHi, new InHandler {
      override def onPush(): Unit = {
        if (isAvailable(out)) {
          // isAvailable(out) implies !pending
          // -> grab and push immediately
          push(out, grab(inHi))
        }
      }

      override def onUpstreamFinish() = {
        if (isClosed(inLo) && !isAvailable(inLo)) {
          completeStage()
        }
      }
    })

    setHandler(inLo, new InHandler {
      override def onPush(): Unit = {
        // do nothing, we might grab this element in onPull if inHi would not be available
      }

      override def onUpstreamFinish() = {
        if (isClosed(inHi) && !isAvailable(inHi)) {
          completeStage()
        }
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        if (isAvailable(inHi)) {
          push(out, grab(inHi))
        } else if (isAvailable(inLo)) {
          push(out, grab(inLo))
        }
        if (isClosed(inHi) && isClosed(inLo)) {
          completeStage()
        } else {
          if (!hasBeenPulled(inHi)) {
            tryPull(inHi)
          }
          if (!hasBeenPulled(inLo) && !isAvailable(inLo)) {
            tryPull(inLo)
          }
        }
      }
    })
  }

  override def toString = "MergePref4"

}