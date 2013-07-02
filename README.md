# Proof of concept - CQRS in Scala

## API
```scala
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
```


## Example

```scala
object InventoryItemExample {
  val eventStore: EventStore = EventStoreInMemory

  case class InventoryItem(name: String, activated: Boolean)
  sealed  trait InventoryItemEvent
  sealed trait InventoryItemCommand


  object InventoryItemContext extends Context[InventoryItem, InventoryItemCommand, InventoryItemEvent] {
    def execute(command: InventoryItemCommand) = command match{
      case DeactivateInventoryItem => InventoryItemDeactivated

      case CreateInventoryItem(name) => InventoryItemCreated(name)

      case RenameInventoryItem(newName) =>
        require(newName != null)
        InventoryItemRenamed(newName)

      case CheckInItemsToInventory(count) =>
        require(count > 0)
        ItemsCheckedInToInventory(count)

      case RemoveItemsFromInventory(count) =>
        require(count > 0)
        ItemsRemovedFromInventory(count)
    }

    def applyEvent(value: InventoryItem, event: InventoryItemEvent) = event match {
      case InventoryItemDeactivated => value.copy(activated = false)
      case InventoryItemCreated(name) => value.copy(name = name)
      case InventoryItemRenamed(newName) => value.copy(name = newName)
      case ItemsCheckedInToInventory(count) => value
      case ItemsRemovedFromInventory(count) => value
    }

    def newInstance = InventoryItem("", activated = false)

    val repository = new RepositoryInMemory(eventStore, this)
  }


  case object DeactivateInventoryItem extends InventoryItemCommand
  case class CreateInventoryItem(name: String) extends InventoryItemCommand
  case class RenameInventoryItem(newName: String) extends InventoryItemCommand
  case class CheckInItemsToInventory(count: Int) extends InventoryItemCommand
  case class RemoveItemsFromInventory(count: Int) extends InventoryItemCommand


  case object InventoryItemDeactivated extends InventoryItemEvent
  case class InventoryItemCreated(name: String) extends InventoryItemEvent
  case class InventoryItemRenamed(name: String) extends InventoryItemEvent
  case class ItemsCheckedInToInventory(count: Int) extends InventoryItemEvent
  case class ItemsRemovedFromInventory(count: Int) extends InventoryItemEvent


  val itemId = newIdentifier
  on(InventoryItemContext) handle Command(itemId, CreateInventoryItem("my item"))
  on(InventoryItemContext) handle Command(itemId, RenameInventoryItem("my renamed item"))
  on(InventoryItemContext) handle Command(itemId, CheckInItemsToInventory(2))
  on(InventoryItemContext) handle Command(itemId, RemoveItemsFromInventory(1))
  on(InventoryItemContext) handle Command(itemId, DeactivateInventoryItem)


  val handler = new CommandHandler {
    def eventStore = InventoryItemExample.eventStore
  }
  def on[A, C, E](context: Context[A, C, E]) = new {
    def handle(command: Command[C]): AggregateRoot[A] = handler(command)(context)
  }
}
```