class FastAPI:
    def __init__(self):
        pass

    def post(self, param):
        pass


app = FastAPI()


class File:
    def __init__(self):
        pass


class UploadFile:
    def __init__(self):
        pass

    async def read(self):
        pass


def remove():
    pass


class Response:
    def __init__(self):
        pass


@app.post("/remove-bg")
async def remove_bg(file: UploadFile = File()):
    input_bytes = await file.read()
    output_bytes = remove()
    return Response(content=output_bytes, media_type="image/png")