from pydantic import BaseModel
from models.order import Order


class User(BaseModel):
    id: int
    name: str
    orders: list[Order] = []
