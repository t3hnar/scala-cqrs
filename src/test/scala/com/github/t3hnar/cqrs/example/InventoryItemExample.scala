package com.github.t3hnar.cqrs
package example

/**
 * @author Yaroslav Klymko
 */
object InventoryItemExample {
  val eventStore: EventStore = EventStoreInMemory

  case class InventoryItem(name: String, activated: Boolean)
  sealed  trait InventoryItemEvent
  sealed trait InventoryItemCommand

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
