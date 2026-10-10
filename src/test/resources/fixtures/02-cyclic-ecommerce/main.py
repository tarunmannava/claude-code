from services.checkout import process_checkout
from models.user import User

user = User(id=1, name="Alice")
order = process_checkout(user, 99.9)
