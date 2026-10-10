from legacy.processor import dynamic_get, untyped_transform


class LegacyConfig:
    def __init__(self):
        self.api_key = "secret_123"


config = LegacyConfig()
val = dynamic_get(config, "api_key")
output = untyped_transform({"key": val})
