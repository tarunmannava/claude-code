from models.item import Item


def get_item(item_id: int) -> Item | None:
    return Item(id=item_id, title="Sample Item", completed=False)


def create_item(item: Item) -> Item:
    return item
