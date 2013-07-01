package com.github.t3hnar.cqrs

import scala.reflect.ClassTag
import scala.collection.mutable

/**
 * @author Yaroslav Klymko
 */
class RepositoryInMemory[A, E](eventStore: EventStore, context: Context[A, _, E])
                              (implicit tag: ClassTag[E]) extends Repository[A] {

  val map = mutable.Map[Identifier, AggregateRoot[A]]()

  def save(ar: AggregateRoot[A]) {
    map += ar.id -> ar
  }

  def get(id: Identifier) = map.get(id) orElse {
    val stream = eventStore.stream[E](id)
    if (stream.isEmpty) None
    else
      Some(stream.foldLeft(AggregateRoot(id, context.newInstance))((ar, e) => ar.nextRevision(context.applyEvent(ar.value, e.value))))
  }
}