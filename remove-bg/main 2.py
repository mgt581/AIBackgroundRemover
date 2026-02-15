import io
import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image
from firebase_functions import https_fn
from firebase_admin import initialize_app, storage
from transformers import AutoModelForImageSegmentation
from torchvision.transforms.functional import normalize

initialize_app()

# Load model globally for warm starts
device = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")
model = AutoModelForImageSegmentation.from_pretrained("briaai/MDBG-1.4", trust_remote_code=True)
model.to(device)
model.eval()


def preprocess_image(im: np.ndarray, model_input_size: list) -> torch.Tensor:
    if len(im.shape) < 3:
        im = im[:, :, np.newaxis]
    im_tensor = torch.tensor(im, dtype=torch.float32).permute(2, 0, 1)
    im_tensor = F.interpolate(torch.unsqueeze(im_tensor, 0), size=model_input_size, mode='bilinear')
    image = torch.divide(im_tensor, 255.0)
    image = normalize(image, [0.5, 0.5, 0.5], [1.0, 1.0, 1.0])
    return image


def postprocess_image(result: torch.Tensor, im_size: list) -> np.ndarray:
    result = torch.squeeze(F.interpolate(result, size=im_size, mode='bilinear'), 0)
    ma = torch.max(result)
    mi = torch.min(result)
    result = (result - mi) / (ma - mi)
    im_array = (result * 255).permute(1, 2, 0).cpu().data.numpy().astype(np.uint8)
    im_array = np.squeeze(im_array)
    return im_array


@https_fn.on_call()
def remove_background(req: https_fn.CallableRequest) -> dict:
    """
    Firebase Function to remove background from an image.
    Expects 'image_url' in data.
    """
    image_url = req.data.get("image_url")
    if not image_url:
        return {"error": "No image_url provided"}

    try:
        # 1. Download/Open image
        import requests
        response = requests.get(image_url)
        orig_image = Image.open(io.BytesIO(response.content)).convert("RGB")
        orig_im = np.array(orig_image)
        orig_im_size = orig_im.shape[0:2]

        # 2. Preprocess
        model_input_size = [1024, 1024]
        image_tensor = preprocess_image(orig_im, model_input_size).to(device)

        # 3. Inference
        with torch.no_grad():
            result = model(image_tensor)

        # 4. Postprocess
        result_image = postprocess_image(result[0][0], orig_im_size)
        mask = Image.fromarray(result_image)

        # 5. Apply Mask
        no_bg_image = orig_image.copy()
        no_bg_image.putalpha(mask)

        # 6. Save the result to Firebase Storage (or return as Base64)
        output_buffer = io.BytesIO()
        no_bg_image.save(output_buffer, format="PNG")
        output_buffer.seek(0)

        bucket = storage.bucket()
        blob = bucket.blob(
            f"processed/{req.auth.uid if req.auth else 'anon'}_{int(torch.randint(0, 10000, (1,)))}.png")
        blob.upload_from_file(output_buffer, content_type="image/png")
        blob.make_public()

        return {"processed_url": blob.public_url}

    except Exception as e:
        return {"error": str(e)}


@https_fn.on_call()
def change_background(req: https_fn.CallableRequest) -> dict:
    """
    Expect    Firebase Function to change the background.
S 'image_url' and 'bg_url' (or color hex) in data.
    """
    image_url = req.data.get("image_url")
    bg_url = req.data.get("bg_url")  # Can be imaged URL or color

    if not image_url or not bg_url:
        return {"error": "Missing image_url or bg_url"}

    try:
        import requests
        # 1. Get the original image and remove the background
        img_response = requests.get(image_url)
        orig_image = Image.open(io.BytesIO(img_response.content)).convert("RGB")

        # (Internal bg removal logic same as above)
        orig_im = np.array(orig_image)
        image_tensor = preprocess_image(orig_im, [1024, 1024]).to(device)
        with torch.no_grad():
            result = model(image_tensor)
        mask = Image.fromarray(postprocess_image(result[0][0], orig_im.shape[0:2]))

        fg_image = orig_image.copy()
        fg_image.putalpha(mask)

        # 2. Get/Create background
        if bg_url.startswith("http"):
            bg_response = requests.get(bg_url)
            background = Image.open(io.BytesIO(bg_response.content)).convert("RGBA")
            background = background.resize(orig_image.size, Image.Resampling.LANCZOS)
        else:
            # Assume it's a hex color
            background = Image.new("RGBA", orig_image.size, bg_url)

        # 3. Composite
        combined = Image.alpha_composite(background, fg_image)

        # 4. Save and return
        output_buffer = io.BytesIO()
        combined.convert("RGB").save(output_buffer, format="JPEG")
        output_buffer.seek(0)

        bucket = storage.bucket()
        blob = bucket.blob(
            f"combined/{req.auth.uid if req.auth else 'anon'}_{int(torch.randint(0, 10000, (1,)))}.jpg")
        blob.upload_from_file(output_buffer, content_type="image/jpeg")
        blob.make_public()

        return {"processed_url": blob.public_url}

    except Exception as e:
        return {"error": str(e)}
