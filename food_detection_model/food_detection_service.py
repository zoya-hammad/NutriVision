import modal
import numpy as np
from PIL import Image
import io
import json
import os
from fastapi import FastAPI, File, UploadFile
import tensorflow as tf

# Create a Modal app
app = modal.App("food-detection-service")

# Define the class labels
FOOD_CLASSES = {
    0: "Aloo gobi",
    1: "Bhindi fry",
    2: "Ras malai",
    3: "bbq",
    4: "biryani",
    5: "brownie",
    6: "butter_chicken",
    7: "chai",
    8: "chapati",
    9: "chicken_tikka",
    10: "french_fries",
    11: "fried_rice",
    12: "haleem",
    13: "omelette",
    14: "paratha",
    15: "paratha_roll",
    16: "samosa"
}

# Get the directory where this script is located
current_dir = os.path.dirname(os.path.abspath(__file__))
model_path = os.path.join(current_dir, "best_model.tflite")

# Create a Modal image with the required dependencies and model file
image = modal.Image.debian_slim().pip_install(
    "tensorflow",
    "Pillow",
    "numpy",
    "fastapi[standard]",
    "python-multipart"
).add_local_file(
    model_path,  # Local path to your model file
    "/root/model.tflite"  # Path in the Modal container
)

# Load the TFLite model
@app.function(image=image)
def load_model():
    import tensorflow as tf
    interpreter = tf.lite.Interpreter(model_path="/root/model.tflite")
    interpreter.allocate_tensors()
    return interpreter

def softmax(x):
    """Apply softmax to convert logits to probabilities"""
    exp_x = np.exp(x - np.max(x))  # Subtract max for numerical stability
    return exp_x / np.sum(exp_x)

def preprocess_image(image):
    """Preprocess image for model input using the same preprocessing as training"""
    if image.mode != 'RGB':
        image = image.convert('RGB')
    
    # Resize to match training size
    image = image.resize((300, 300))
    
    # Convert to numpy array
    image_array = np.array(image, dtype=np.float32)
    
    # Apply EfficientNet preprocessing
    image_array = tf.keras.applications.efficientnet.preprocess_input(image_array)
    
    return image_array.astype(np.float32)

# Process image and make prediction
@app.function(image=image)
def predict_food(image_bytes: bytes) -> str:
    import tensorflow as tf
    
    try:
        # Load model
        interpreter = load_model.local()
        
        # Get input and output details
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        # Process image
        image = Image.open(io.BytesIO(image_bytes))
        image_array = preprocess_image(image)
        image_array = np.expand_dims(image_array, axis=0)
        
        # Verify input shape and type
        expected_shape = input_details[0]['shape']
        if image_array.shape != tuple(expected_shape):
            raise ValueError(f"Input shape mismatch. Expected {expected_shape}, got {image_array.shape}")
        
        # Make prediction
        interpreter.set_tensor(input_details[0]['index'], image_array)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        
        # Apply softmax to convert logits to probabilities
        probabilities = softmax(output[0])
        
        # Get class with highest probability
        predicted_class = np.argmax(probabilities)
        confidence = float(probabilities[predicted_class])
        
        # Get class name from mapping
        class_name = FOOD_CLASSES.get(predicted_class, f"Unknown class {predicted_class}")
        
        return f"Detected: {class_name} (Confidence: {confidence:.2%})"
        
    except Exception as e:
        return f"Error during prediction: {str(e)}"

# Create FastAPI app using ASGI
@app.function(image=image)
@modal.asgi_app()
def fastapi_app():
    from fastapi import FastAPI, File, UploadFile
    
    web_app = FastAPI()
    
    @web_app.post("/")
    async def detect_food(image: UploadFile = File(...)):
        contents = await image.read()
        result = predict_food.local(contents)
        return {"prediction": result}
    
    return web_app