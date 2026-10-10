from models.user import User
from models.order import Order


def process_checkout(user: User, amount: float) -> Order:
    return Order(id=1, user_id=user.id, amount=amount, user=user)
