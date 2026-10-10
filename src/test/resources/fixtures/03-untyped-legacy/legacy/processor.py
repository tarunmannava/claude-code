def dynamic_get(obj, attr_name, default_val=None):
    # Dynamic hazard: getattr
    return getattr(obj, attr_name, default_val)


def dynamic_set(obj, attr_name, value):
    # Dynamic hazard: setattr
    setattr(obj, attr_name, value)


def untyped_transform(data, **kwargs):
    # Dynamic hazard: untyped dictionary and kwargs
    res = {}
    for k, v in data.items():
        res[k] = v
    return res
