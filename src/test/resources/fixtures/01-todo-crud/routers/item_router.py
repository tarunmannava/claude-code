from models.item import Item
from crud.item_crud import get_item, create_item


class APIRouter:
    def get(self, path: str):
        def decorator(f):
            return f
        return decorator

    def post(self, path: str):
        def decorator(f):
            return f
        return decorator


router = APIRouter()


@router.get("/items/{item_id}")
def read_item_endpoint(item_id: int) -> Item | None:
    return get_item(item_id)


@router.post("/items")
def create_item_endpoint(item: Item) -> Item:
    return create_item(item)
