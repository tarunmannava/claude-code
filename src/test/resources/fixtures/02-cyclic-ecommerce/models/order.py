from pydantic import BaseModel
from models.user import User


class Order(BaseModel):
    id: int
    user_id: int
    amount: float
    user: User | None = None
