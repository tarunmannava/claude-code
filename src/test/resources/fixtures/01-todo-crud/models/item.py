from pydantic import BaseModel


class Item(BaseModel):
    id: int
    title: str
    completed: bool = False
    price: float | None = None
