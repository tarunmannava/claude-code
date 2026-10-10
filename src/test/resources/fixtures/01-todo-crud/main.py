from routers.item_router import router


class FastAPI:
    def __init__(self):
        self.router = None

    def include_router(self, r):
        self.router = r


app = FastAPI()
app.include_router(router)
