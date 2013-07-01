package com.github.t3hnar.cqrs

import scala.reflect.ClassTag

/**
 * @author Yaroslav Klymko
 */
object EventStoreInMemory extends EventStore {
  val buffer = scala.collection.mutable.ListBuffer[Event[_]]()

  def stream = buffer.toStream

  def add(event: Event[_]) {
    println(event)
    buffer += event
  }

  def stream[T](aggregateId: Identifier)(implicit tag: ClassTag[T]) = {
    buffer.toStream.collect {
      case event@Event(`aggregateId`, e) if tag.unapply(e).isDefined => event.asInstanceOf[Event[T]]
    }
  }
}
