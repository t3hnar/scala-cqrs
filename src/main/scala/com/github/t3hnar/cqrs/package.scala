package com.github.t3hnar

import scala.reflect.ClassTag

/**
 * @author Yaroslav Klymko
 */
package object cqrs {
  type Identifier = String
  def newIdentifier: Identifier = java.util.UUID.randomUUID().toString

  type Revision = Long


  case class AggregateRoot[A](id: Identifier, revision: Long, value: A) {
    def nextRevision(value: A): AggregateRoot[A] = copy(value = value, revision = revision + 1)
  }

  object AggregateRoot {
    def apply[A](value: A): AggregateRoot[A] = apply(newIdentifier, value)
    def apply[A](id: Identifier, value: A): AggregateRoot[A] = AggregateRoot(id, -1, value)
  }

  case class Command[C](aggregateId: Identifier, value: C)
  case class Event[E](aggregateId: Identifier, value: E)

  trait Repository[A] {
    def save(ar: AggregateRoot[A])
    def get(aggregateId: Identifier): Option[AggregateRoot[A]]
  }

  trait EventStore {
    def add(event: Event[_])
    def stream[E](aggregateId: Identifier)(implicit tag: ClassTag[E]): Stream[Event[E]]
    def stream: Stream[Event[_]]
  }

  trait Context[A, C, E] {
    def execute(command: C): E
    def applyEvent(value: A, event: E): A
    def newInstance: A
    def repository: Repository[A]
  }

  trait CommandHandler {
    def eventStore: EventStore

    def apply[A, C, E](command: Command[C])(implicit context: Context[A, C, E]): AggregateRoot[A] = {

      val aggregateId = command.aggregateId

      val ar = context.repository.get(aggregateId) getOrElse AggregateRoot(context.newInstance)

      val event = context.execute(command.value)

      eventStore.add(Event(aggregateId, event))

      val result = ar.nextRevision(context.applyEvent(ar.value, event))

      context.repository.save(result)

      result
    }
  }
}
